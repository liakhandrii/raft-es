package com.liakhandrii.es.raft;


import com.liakhandrii.es.implementation.local.LocalNodeAccessor;
import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;

import java.util.UUID;

public class MockNodeAccessor extends LocalNodeAccessor {

    public AppendEntriesRequest<String> lastAppendRequest = null;
    public AppendEntriesResponse        lastAppendResponse = null;

    public VoteRequest  lastVoteRequest  = null;
    public VoteResponse lastVoteResponse = null;

    public ClientResponse lastClientResponse = null;

    public MockNodeAccessor(NodeCore<String> node) {
        super(node);
    }

    public void killNode() {
        isNodeDown = true;
        node.stopNode();
    }

    public void reviveNode() {
        isNodeDown = false;
        node.startNode();
    }

    public Entry<String> addRandomEntry() {
        long index = node.getCurrentIndex() == null ? -1 : node.getCurrentIndex();
        Entry<String> entry = new Entry<>(UUID.randomUUID().toString().substring(0, 4), node.currentTerm, index + 1);
        node.entries.add(entry);
        return entry;
    }

    @Override
    public AppendEntriesResponse sendAppendEntriesRequest(AppendEntriesRequest<String> request) {
        lastAppendRequest = request;
        if (isNodeDown) { return null; }
        System.out.println("Leader " + request.getLeaderId().substring(0, 4) + " sends " + request.getEntries().size() + " entries to " + nodeId.substring(0, 4));
        AppendEntriesResponse response = node.receiveEntries(request);
        lastAppendResponse = response;
        if (response.isSuccessful()) {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " accepted new entries from " + nodeId.substring(0, 4));
        } else {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " rejects. Reason: " + response.getReason());
        }
        return response;
    }

    @Override
    public VoteResponse sendVoteRequest(VoteRequest request) {
        lastVoteRequest = request;
        if (isNodeDown) { return null; }
        System.out.println("Candidate " + request.getCandidateId().substring(0, 4) + " term " + request.getCandidateTerm() + " sends a vote request to " + nodeId.substring(0, 4) + " at " + System.currentTimeMillis());
        VoteResponse response = node.receiveVoteRequest(request);
        lastVoteResponse = response;
        System.out.println("Node " + response.getResponderId().substring(0, 4) + " votes " + response.getDidReceiveVote());
        return response;
    }

    @Override
    public ClientResponse sendClientRequest(ClientRequest<String> request) {
        System.out.println("Node " + nodeId.substring(0, 4) + " received a client request");
        ClientResponse response = node.receiveClientRequest(request);
        lastClientResponse = response;
        if (isNodeDown) { return null; }
        if (response.getRedirect() != null) {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with redirect: " + response.getRedirect().getNodeId().substring(0, 4));
        } else {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with success");
        }
        return response;
    }
}
