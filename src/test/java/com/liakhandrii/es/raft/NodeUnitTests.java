package com.liakhandrii.es.raft;


import com.liakhandrii.es.raft.models.AppendEntriesRequest;
import com.liakhandrii.es.raft.models.NodeRank;
import com.liakhandrii.es.raft.models.VoteRequest;
import com.liakhandrii.es.raft.models.VoteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class NodeUnitTests {

    static private List<FakeNodeAccessor> accessors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        accessors = LocalTest.generateNodes(5, false);
        // It's important that nodes stay disabled, so we can start them only when we need to test time-related things
    }

    @AfterEach
    void tearDown() {
        accessors = null;
    }

    @Test
    void testVotingRules() {
        // We can do intercept responses because we make a manual synchronous call to receiveVoteRequest, so we know by the end it's done executing – there is a request saved on the accessor
        FakeNodeAccessor nodeAccessor = accessors.get(0);
        FakeNodeAccessor candidateAccessor = accessors.get(1);

        nodeAccessor.node.setTerm(1);
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));

        VoteResponse response = candidateAccessor.lastVoteResponse;

        assertEquals(false, response.didReceiveVote());

        nodeAccessor.node.receiveVoteRequest(new VoteRequest(0, candidateAccessor.getNodeId(), 1L, 0L, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertEquals(false, response.didReceiveVote());

        nodeAccessor.addRandomEntry();
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertEquals(false, response.didReceiveVote());

        nodeAccessor.node.entries.clear();
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), null, null, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertEquals(true, response.didReceiveVote());

        // The node has voted on this term, so now it shouldn't vote
        nodeAccessor.node.receiveVoteRequest(new VoteRequest(1, candidateAccessor.getNodeId(), 0L, 0L, UUID.randomUUID().toString()));
        response = candidateAccessor.lastVoteResponse;

        assertEquals(false, response.didReceiveVote());
    }

    @Test
    void sendFirstVoteRequest() {
        FakeNodeAccessor candidateAccessor = accessors.get(0);
        FakeNodeAccessor nodeAccessor = accessors.get(1);

        candidateAccessor.node.startElection();
        // We do this because we don't know which node got a request and which didn't, so we find at least one.
        VoteRequest request = accessors.stream().filter(accessor -> accessor.lastVoteRequest != null).collect(Collectors.toList()).get(0).lastVoteRequest;

        assertEquals(1, request.getCandidateTerm());
        assertNull(request.getLastEntryIndex());
        assertNull(request.getLastEntryTerm());
    }

    @Test
    void sendPopulatedVoteRequest() {
        FakeNodeAccessor candidateAccessor = accessors.get(0);

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
    void processVoteRequestResponse() {
        // TODO: this is an integration test
    }

    @Test
    void normalSendEntries() {
        FakeNodeAccessor leaderAccessor = accessors.get(0);
        FakeNodeAccessor nodeAccessor = accessors.get(1);

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
    void processEntriesResponse() {

    }

    @Test
    void receiveEntries() {
    }

    @Test
    void receiveClientRequest() {
    }

    @Test
    void startNode() {
    }

    @Test
    void startElection() {
    }

    @Test
    void becomeFollower() {
    }

    @Test
    void becomeLeader() {
    }

    @Test
    void registerOtherNode() {
    }

    @Test
    void currentIndex() {
    }

    @Test
    void lastEntryTerm() {
    }

    @Test
    void getId() {
    }

    @Test
    void setTerm() {
    }
}