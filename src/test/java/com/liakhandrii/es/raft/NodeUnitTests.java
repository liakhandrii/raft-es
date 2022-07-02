package com.liakhandrii.es.raft;


import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class NodeUnitTests {

    static private List<MockNodeAccessor> accessors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        accessors = LocalTest.generateNodes(5, false);
        // It's important that nodes stay disabled, so no random side effects happen when we are doing the unit tests.
    }

    @AfterEach
    void tearDown() {
        accessors = null;
    }

    @Test
    void testInitValues() {
        NodeCore<String> node = new NodeCore<>();

        assertEquals(NodeRank.FOLLOWER, node.rank);
        assertEquals(0, node.currentTerm);
        assertEquals(50, node.heartbeatInterval);
        assertTrue(250 <= node.electionTimeout && 400 >= node.electionTimeout);
        assertEquals(0, node.otherNodes.size());
        assertEquals(0, node.receivedVotes.size());

    }

    @Test
    void testVotingRules() {
        // We can do intercept responses because we make a manual synchronous call to receiveVoteRequest, so we know by the end it's done executing – there is a request saved on the accessor
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());

        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 1L, 0L, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());

        nodeAccessor.addRandomEntry();
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());

        nodeAccessor.node.entries.clear();
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertTrue(response.didReceiveVote());

        // The node has voted on this term, so now it shouldn't vote
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertFalse(response.didReceiveVote());
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
    void calculateCommitIndex() {
        MockNodeAccessor leaderAccessor = accessors.get(0);

        leaderAccessor.addRandomEntry();
        leaderAccessor.addRandomEntry();
        leaderAccessor.node.becomeLeader();

        assertEquals(1, leaderAccessor.node.commitIndex);
    }

    @Test
    void receiveGoodEntries() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor leaderAccessor = accessors.get(1);

        ArrayList<Entry<String>> entries = new ArrayList<>(Arrays.asList(
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

        ArrayList<Entry<String>> entries = new ArrayList<>(Arrays.asList(
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

        ArrayList<Entry<String>> entries = new ArrayList<>(Arrays.asList(
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
        assertEquals(NodeRank.CANDIDATE, nodeAccessor.node.rank);
        assertEquals(1, nodeAccessor.node.receivedVotes.size());
        assertNotNull(nodeAccessor.node.votedId);
    }

    @Test
    void becomeFollower() {
        MockNodeAccessor nodeAccessor = accessors.get(0);
        MockNodeAccessor fakeLeader = accessors.get(1);

        nodeAccessor.node.becomeFollower(fakeLeader.nodeId);

        assertEquals(NodeRank.FOLLOWER, nodeAccessor.node.rank);
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

        assertEquals(NodeRank.LEADER, nodeAccessor.node.rank);
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

        nodeAccessor.sendClientRequest(new ClientRequest("test"));
        ClientResponse response = nodeAccessor.lastClientResponse;

        assertFalse(response.isSuccess());
        assertEquals(leaderAccessor.getNodeId(), response.getRedirect().getNodeId());
    }

    @Test
    void leaderClientRequest() {
        MockNodeAccessor leaderAccessor = accessors.get(0);

        leaderAccessor.node.becomeLeader();

        leaderAccessor.sendClientRequest(new ClientRequest("test"));
        ClientResponse response = leaderAccessor.lastClientResponse;

        assertTrue(response.isSuccess());
    }
}