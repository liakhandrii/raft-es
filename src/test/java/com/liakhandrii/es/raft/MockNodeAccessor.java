package com.liakhandrii.es.raft;


import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;

import java.util.UUID;
import java.util.logging.Logger;

public class MockNodeAccessor extends NodeAccessor<String> {

    public NodeCore<String> node;
    public boolean isNodeDown = false;

    public AppendEntriesRequest<String> lastAppendRequest = null;
    public AppendEntriesResponse        lastAppendResponse = null;

    public VoteRequest  lastVoteRequest  = null;
    public VoteResponse lastVoteResponse = null;

    public ClientResponse lastClientResponse = null;

    public MockNodeAccessor(NodeCore<String> node) {
        this.node = node;
        this.nodeId = node.getId();
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
    public void sendAppendEntriesRequest(AppendEntriesRequest<String> request) {
        lastAppendRequest = request;
        if (isNodeDown) { return; }
        System.out.println("Leader " + request.getLeaderId().substring(0, 4) + " sends " + request.getEntries().size() + " entries to " + nodeId.substring(0, 4));
        node.receiveEntries(request);
    }

    @Override
    public void sendAppendEntriesResponse(AppendEntriesResponse response) {
        lastAppendResponse = response;
        if (isNodeDown) { return; }
        if (response.isSuccessful()) {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " accepted new entries from " + nodeId.substring(0, 4));
        } else {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " rejects. Reason: " + response.getReason());
        }
        node.processEntriesResponse(response);
    }

    @Override
    public void sendVoteRequest(VoteRequest request) {
        lastVoteRequest = request;
        if (isNodeDown) { return; }
        System.out.println("Candidate " + request.getCandidateId().substring(0, 4) + " term " + request.getCandidateTerm() + " sends a vote request to " + nodeId.substring(0, 4) + " at " + System.currentTimeMillis());
        node.receiveVoteRequest(request);
    }

    @Override
    public void sendVoteResponse(VoteResponse response) {
        lastVoteResponse = response;
        if (isNodeDown) { return; }
        System.out.println("Node " + response.getResponderId().substring(0, 4) + " votes " + response.didReceiveVote());
        node.processVoteResponse(response);
    }

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
