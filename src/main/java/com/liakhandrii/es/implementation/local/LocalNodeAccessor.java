package com.liakhandrii.es.implementation.local;


import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.NodeAccessor;
import com.liakhandrii.es.raft.NodeCore;
import com.liakhandrii.es.raft.models.*;

import java.util.UUID;

public class LocalNodeAccessor extends NodeAccessor<String> {

    public NodeCore<String> node;
    public boolean isNodeDown = false;

    public LocalNodeAccessor(NodeCore<String> node) {
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

    @Override
    public void sendAppendEntriesRequest(AppendEntriesRequest<String> request) {
        if (isNodeDown) { return; }
        System.out.println("Leader " + request.getLeaderId().substring(0, 4) + " sends " + request.getEntries().size() + " entries to " + nodeId.substring(0, 4));
        node.receiveEntries(request);
    }

    @Override
    public void sendAppendEntriesResponse(AppendEntriesResponse response) {
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
        if (isNodeDown) { return; }
        System.out.println("Candidate " + request.getCandidateId().substring(0, 4) + " term " + request.getCandidateTerm() + " sends a vote request to " + nodeId.substring(0, 4) + " at " + System.currentTimeMillis());
        node.receiveVoteRequest(request);
    }

    @Override
    public void sendVoteResponse(VoteResponse response) {
        if (isNodeDown) { return; }
        System.out.println("Node " + response.getResponderId().substring(0, 4) + " votes " + response.didReceiveVote());
        node.processVoteResponse(response);
    }

    public ClientResponse sendClientRequest(ClientRequest<String> request) {
        System.out.println("Node " + nodeId.substring(0, 4) + " received a client request");
        ClientResponse response = node.receiveClientRequest(request);
        if (isNodeDown) { return null; }
        if (response.getRedirect() != null) {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with redirect: " + response.getRedirect().getNodeId().substring(0, 4));
        } else {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with success");
        }
        return response;
    }
}
