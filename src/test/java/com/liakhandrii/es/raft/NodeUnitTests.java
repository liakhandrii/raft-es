package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class NodeUnitTests {

    static private List<MockNodeAccessor> accessors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        accessors = generateNodes(5);
        // It's important that nodes stay disabled, so no random side effects happen when we are doing the unit tests.
    }

    @AfterEach
    void tearDown() {
        accessors.forEach(nodeAccessor -> {
            nodeAccessor.node.stopNode();
        });
        accessors = null;
    }

    static List<MockNodeAccessor> generateNodes(int count) {
        List<MockNodeAccessor> newAccessors = new ArrayList<>();

        for (int i = 0; i < count; i += 1) {
            NodeCore<String> node = new NodeCore<>();
            newAccessors.add(new MockNodeAccessor(node));
        }

        newAccessors.forEach(nodeAccessor -> {
            newAccessors.forEach(otherAccessor -> {
                nodeAccessor.node.registerOtherNode(otherAccessor);
            });
        });

        return newAccessors;
    }

    /**
     * Election Safety: at most one leader can be elected in a given term.
     */
    @Test
    void testElectionSafety() {
        for (int i = 0; i < 10; i += 1) {
            for (MockNodeAccessor accessor: accessors) {
                accessor.node.startElection();
            }
            Set<String> leaderIds = new HashSet<>();
            for (MockNodeAccessor accessor: accessors) {
                if (accessor.node.currentLeader != null) {
                    leaderIds.add(accessor.node.currentLeader.nodeId);
                } else if (accessor.node.role == NodeRole.LEADER) {
                    leaderIds.add(accessor.nodeId);
                }
            }
            assertEquals(1, leaderIds.size());
        }
    }

    /**
     * Leader Append-Only: a leader never overwrites or deletes entries in its log; it only appends new entries.
     */
    @Test
    void leaderAppendOnly() {
        MockNodeAccessor leader = accessors.get(0);
        leader.node.setTerm(1);
        leader.node.becomeLeader();

        leader.addRandomEntry();
        leader.addRandomEntry();
        leader.addRandomEntry();

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(1, accessors.get(1).getNodeId(), 1L, 1L, new ArrayList<>(), 1L, UUID.randomUUID().toString());
        leader.sendAppendEntriesRequest(request);

        assertEquals(3, leader.node.entries.size());
    }

    /**
     * Log Matching: if two logs contain an entry with the same index and term, then the logs are identical in all entries up through the given index.
     */
    @Test
    void testLogMatching() {
        MockNodeAccessor leader = accessors.get(0);
        leader.node.setTerm(1);
        leader.node.becomeLeader();

        Entry<String> entry1 = leader.addRandomEntry();
        Entry<String> entry2 = leader.addRandomEntry();
        Entry<String> entry3 = leader.addRandomEntry();

        for (int i = 1; i < accessors.size(); i += 1) {
            accessors.get(i).addRandomEntry();
            accessors.get(i).addRandomEntry();
        }

        leader.node.becomeLeader();

        for (int i = 0; i < accessors.size(); i += 1) {
            assertEquals(3, accessors.get(i).node.entries.size());
            assertEquals(entry1, accessors.get(i).node.entries.get(0));
            assertEquals(entry2, accessors.get(i).node.entries.get(1));
            assertEquals(entry3, accessors.get(i).node.entries.get(2));
        }
    }

    /**
     * Leader Completeness: if a log entry is committed in a given term, then that entry will be present in the logs of the leaders for all higher-numbered terms.
     */
    @Test
    void testLeaderCompleteness() {
        MockNodeAccessor leader = accessors.get(0);
        leader.node.setTerm(1);

        Entry<String> entry1 = leader.addRandomEntry();
        Entry<String> entry2 = leader.addRandomEntry();
        Entry<String> entry3 = leader.addRandomEntry();

        leader.node.becomeLeader();

        MockNodeAccessor newLeader = accessors.get(1);
        newLeader.node.becomeLeader();
        newLeader.addRandomEntry();

        assertTrue(newLeader.node.entries.contains(entry1));
        assertTrue(newLeader.node.entries.contains(entry2));
        assertTrue(newLeader.node.entries.contains(entry3));
    }

    /**
     * State Machine Safety: if a server has applied a log entry at a given index to its state machine, no other server will ever apply a different log entry for the same index.
     */
    @Test
    void testStateMachineSafety() {
        MockNodeAccessor leader = accessors.get(0);
        leader.node.setTerm(1);
        leader.addRandomEntry();
        leader.addRandomEntry();
        leader.addRandomEntry();

        leader.node.becomeLeader();

        MockNodeAccessor newLeader = accessors.get(1);
        newLeader.node.startElection();
        Entry<String> entryToCheck = newLeader.addRandomEntry();
        newLeader.node.becomeLeader();

        MockNodeAccessor otherServer = accessors.get(2);
        otherServer.addRandomEntry();

        assertEquals(entryToCheck, otherServer.node.entries.get(3));
    }

    @Test
    void testInitValues() {
        NodeCore<String> node = new NodeCore<>();

        assertEquals(NodeRole.FOLLOWER, node.role);
        assertEquals(0, node.currentTerm);
        assertEquals(50, node.heartbeatInterval);
        assertTrue(250 <= node.electionTimeout && 400 >= node.electionTimeout);
        assertEquals(0, node.otherNodes.size());
        assertEquals(0, node.receivedVotes.size());
    }

    @Test
    void testLowerTermVote() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());
    }

    @Test
    void testLowerTermHigherIndexVote() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 1L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());
    }

    @Test
    void testEqualTermLowerIndexVote() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.addRandomEntry();
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());
    }

    @Test
    void testHigherTermEqualIndexVote() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(2, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertTrue(response.didReceiveVote());
    }

    @Test
    void testRepeatedVote() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(2);
        nodeAccessor.node.votedId = nodeAccessor.nodeId;
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(2, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());
    }

    @Test
    void testRepeatedVoteWithNewTerm() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(2);
        nodeAccessor.node.votedId = nodeAccessor.nodeId;
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(3, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertTrue(response.didReceiveVote());
    }

    @Test
    void sendFirstVoteRequest() {
        MockNodeAccessor candidateAccessor = accessors.get(0);

        candidateAccessor.node.startElection();
        // We do this because we don't know which node got a request and which didn't, so we find at least one.
        VoteRequest request = accessors.stream().filter(accessor -> accessor.lastVoteRequest != null).collect(Collectors.toList()).get(0).lastVoteRequest;

        assertEquals(1, request.getCandidateTerm());
        assertNull(request.getLastEntryIndex());
        assertNull(request.getLastEntryTerm());
    }

    @Test
    void sendPopulatedVoteRequest() {
        MockNodeAccessor candidateAccessor = accessors.get(0);

        candidateAccessor.node.setTerm(1);
        candidateAccessor.addRandomEntry();
        candidateAccessor.node.setTerm(2);
        candidateAccessor.addRandomEntry();
        candidateAccessor.addRandomEntry();

        candidateAccessor.node.startElection();
        // We do this because we don't know which node got a request and which didn't, so we find at least one.
        VoteRequest request = accessors.stream().filter(accessor -> accessor.lastVoteRequest != null).collect(Collectors.toList()).get(0).lastVoteRequest;

        assertEquals(3, request.getCandidateTerm());
        assertEquals(2, request.getLastEntryIndex());
        assertEquals(2, request.getLastEntryTerm());
    }

    @Test
    void normalSendEntries() {
        MockNodeAccessor leaderAccessor = accessors.get(0);
        MockNodeAccessor nodeAccessor = accessors.get(1);

        leaderAccessor.node.sendEntries(nodeAccessor, true);

        // 'Leader' is not a leader yet
        assertNull(nodeAccessor.lastAppendRequest);

        leaderAccessor.addRandomEntry();
        leaderAccessor.addRandomEntry();
        leaderAccessor.node.becomeLeader();

        AppendEntriesRequest<String> request = nodeAccessor.lastAppendRequest;
        assertEquals(2, request.getEntries().size());

        leaderAccessor.node.sendEntries(nodeAccessor, false);
        request = nodeAccessor.lastAppendRequest;
        assertEquals(0, request.getEntries().size());

        leaderAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();

        leaderAccessor.node.sendEntries(nodeAccessor, false);
        assertEquals(3, nodeAccessor.node.entries.size());
    }

    @Test
    void appendEntry() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        Entry<String> entry = nodeAccessor.addRandomEntry();

        assertEquals(entry, nodeAccessor.node.entries.get(0));
    }

    @Test
    void appendEntryWithNewLeaderTerm() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(5, leaderAccessor.getNodeId(), null, null, entries, null, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(leaderAccessor.nodeId, nodeAccessor.node.currentLeader.nodeId);
        assertEquals(5, nodeAccessor.node.currentTerm);
        assertEquals(2, nodeAccessor.node.entries.size());
        assertEquals(entries.get(1).getData(), nodeAccessor.node.entries.get(1).getData());
    }

    @Test
    void saveLeaderCommitIndex() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(5, leaderAccessor.getNodeId(), null, null, entries, 1L, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(1, nodeAccessor.node.commitIndex);
    }

    @Test
    void rejectBacLeaderCommitIndex() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();

        nodeAccessor.node.commitIndex = 1L;

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(5, leaderAccessor.getNodeId(), null, null, entries, 5L, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(1, nodeAccessor.node.commitIndex);
    }

    @Test
    void saveLeaderCommitIndexWithOverWrite() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.node.commitIndex = 2L;

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(5, leaderAccessor.getNodeId(), null, null, entries, 1L, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(1, nodeAccessor.node.commitIndex);
    }

    @Test
    void receiveGoodEntries() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(2, leaderAccessor.getNodeId(), null, null, entries, null, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(2, nodeAccessor.node.entries.size());
        assertEquals(entries.get(1).getData(), nodeAccessor.node.entries.get(1).getData());
    }

    @Test
    void receiveBadEntries() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        nodeAccessor.addRandomEntry();

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 5),
                new Entry<>(UUID.randomUUID().toString(), 1, 6)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(2, leaderAccessor.getNodeId(), 4L, 1L, entries, 4L, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(1, nodeAccessor.node.entries.size());
        assertNotEquals(entries.get(0).getData(), nodeAccessor.node.entries.get(0).getData());
    }

    @Test
    void overwriteBadEntries() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        nodeAccessor.addRandomEntry();

        List<Entry<String>> entries = new ArrayList<>(Arrays.asList(
                new Entry<>(UUID.randomUUID().toString(), 1, 0),
                new Entry<>(UUID.randomUUID().toString(), 1, 1)
        ));

        AppendEntriesRequest<String> request = new AppendEntriesRequest<>(2, leaderAccessor.getNodeId(), null, null, entries, null, UUID.randomUUID().toString());

        nodeAccessor.sendAppendEntriesRequest(request);

        assertEquals(2, nodeAccessor.node.entries.size());
        assertEquals(entries.get(1).getData(), nodeAccessor.node.entries.get(1).getData());
    }

    @Test
    void startElection() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        // We need to kill the other nodes so we can reliably check the startElection method
        for (MockNodeAccessor accessor: accessors) {
            if (!accessor.nodeId.equals(nodeAccessor.nodeId)) {
                accessor.killNode();
            }
        }
        long previousTerm = nodeAccessor.node.currentTerm;
        nodeAccessor.node.startElection();

        assertEquals(previousTerm + 1, nodeAccessor.node.currentTerm);
        assertEquals(NodeRole.CANDIDATE, nodeAccessor.node.role);
        assertEquals(1, nodeAccessor.node.receivedVotes.size());
        assertNotNull(nodeAccessor.node.votedId);
    }

    @Test
    void becomeFollower() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor fakeLeader = accessors.get(1);

        nodeAccessor.node.becomeFollower(fakeLeader.nodeId);

        assertEquals(NodeRole.FOLLOWER, nodeAccessor.node.role);
    }

    @Test
    void saveNewLeader() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor fakeLeader = accessors.get(1);

        nodeAccessor.node.becomeFollower(fakeLeader.nodeId);

        assertEquals(fakeLeader, nodeAccessor.node.currentLeader);
    }

    @Test
    void becomeLeader() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        // We need to kill the other nodes so we can reliably check the method
        for (MockNodeAccessor accessor: accessors) {
            if (!accessor.nodeId.equals(nodeAccessor.nodeId)) {
                accessor.killNode();
            }
        }

        nodeAccessor.node.becomeLeader();

        assertEquals(NodeRole.LEADER, nodeAccessor.node.role);
        assertNull(nodeAccessor.node.currentLeader);
        assertEquals(0, nodeAccessor.node.receivedVotes.size());
    }

    @Test
    void registerOtherNode() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        NodeCore<String> newNode = new NodeCore<>();
        MockNodeAccessor newNodeAccessor = new MockNodeAccessor(newNode);

        nodeAccessor.node.registerOtherNode(newNodeAccessor);
        assertEquals(accessors.size(), nodeAccessor.node.otherNodes.size());

        nodeAccessor.node.registerOtherNode(nodeAccessor);
        assertEquals(accessors.size(), nodeAccessor.node.otherNodes.size());
    }

    @Test
    void currentIndex() {
        MockNodeAccessor nodeAccessor = accessors.get(0);

        assertNull(nodeAccessor.node.getCurrentIndex());

        nodeAccessor.addRandomEntry();

        assertEquals(0, nodeAccessor.node.getCurrentIndex());
    }

    @Test
    void commitIndex() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(1);

        assertNull(nodeAccessor.node.getCurrentIndex());

        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.node.becomeLeader();

        assertEquals(2, nodeAccessor.node.commitIndex);
    }

    @Test
    void commitIfMajorityHasTheEntries() {
        accessors.get(1).killNode();
        accessors.get(2).killNode();

        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(1);

        assertNull(nodeAccessor.node.getCurrentIndex());

        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.node.becomeLeader();

        assertEquals(2, nodeAccessor.node.commitIndex);
    }

    @Test
    void dontCommitWithoutMajority() {
        accessors.get(1).killNode();
        accessors.get(2).killNode();
        accessors.get(3).killNode();

        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(1);

        assertNull(nodeAccessor.node.getCurrentIndex());

        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.node.becomeLeader();

        assertNull(nodeAccessor.node.commitIndex);
    }

    @Test
    void dontCommitIfOtherNodesDontHaveEntries() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(1);

        assertNull(nodeAccessor.node.getCurrentIndex());

        nodeAccessor.node.becomeLeader();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();
        nodeAccessor.addRandomEntry();

        assertNull(nodeAccessor.node.commitIndex);
    }

    @Test
    void lastEntryTerm() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(1);

        assertNull(nodeAccessor.node.getLastEntryTerm());

        nodeAccessor.addRandomEntry();

        assertEquals(1, nodeAccessor.node.getLastEntryTerm());
    }

    @Test
    void setTerm() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        nodeAccessor.node.setTerm(5);
        assertEquals(5, nodeAccessor.node.currentTerm);

        nodeAccessor.node.setTerm(3);
        assertEquals(5, nodeAccessor.node.currentTerm);
    }

    @Test
    void followerClientRequest() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        leaderAccessor.node.becomeLeader();

        nodeAccessor.sendClientRequest(new ClientRequest<String>("test"));
        ClientResponse response = nodeAccessor.lastClientResponse;

        assertFalse(response.isSuccess());
        assertEquals(leaderAccessor.getNodeId(), response.getRedirect().getNodeId());
    }

    @Test
    void leaderClientRequest() {
        MockNodeAccessor leaderAccessor = accessors.get(0);

        leaderAccessor.node.becomeLeader();

        leaderAccessor.sendClientRequest(new ClientRequest<String>("test"));
        ClientResponse response = leaderAccessor.lastClientResponse;

        assertTrue(response.isSuccess());
    }
}