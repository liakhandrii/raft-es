package com.liakhandrii.es.implementation.local;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.AppendEntriesRequest;
import com.liakhandrii.es.raft.models.AppendEntriesResponse;
import com.liakhandrii.es.raft.models.VoteRequest;
import com.liakhandrii.es.raft.models.VoteResponse;
import com.liakhandrii.es.raft.nodes.NodeAccessor;
import com.liakhandrii.es.raft.nodes.NodeCore;

public class LocalNodeAccessor extends NodeAccessor<String> {

    public NodeCore<String> node;

    public LocalNodeAccessor(NodeCore<String> node) {
        this.node = node;
        this.nodeId = node.getId();
    }

    public void killNode() {
        // TODO
//        node.killNode();
    }

    @Override
    public void sendAppendEntriesRequest(AppendEntriesRequest<String> request) {
        System.out.println("Leader " + request.getLeaderId().substring(0, 4) + " sends " + request.getEntries().size() + " entries to " + nodeId.substring(0, 4));
        node.receiveEntries(request);
    }

    @Override
    public void sendAppendEntriesResponse(AppendEntriesResponse response) {
        if (response.isSuccessful()) {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " accepted new entries from " + nodeId.substring(0, 4));
        } else {
            System.out.println("Node " + response.getResponderId().substring(0, 4) + " rejects. Reason: " + response.getReason());
        }
        node.processEntriesResponse(response);
    }

    @Override
    public void sendVoteRequest(VoteRequest request) {
        System.out.println("Candidate " + request.getCandidateId().substring(0, 4) + " term " + request.getCandidateTerm() + " sends a vote request to " + nodeId.substring(0, 4) + " at " + System.currentTimeMillis());
        node.receiveVoteRequest(request);
    }

    @Override
    public void sendVoteResponse(VoteResponse response) {
        System.out.println("Node " + response.getResponderId().substring(0, 4) + " votes " + response.didReceiveVote());
        node.processVoteRequestResponse(response);
    }

    public ClientResponse sendClientRequest(ClientRequest request) {
        System.out.println("Node " + nodeId.substring(0, 4) + " received a client request");
        ClientResponse response = node.receiveClientRequest(request);
        if (response.getRedirect() != null) {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with redirect: " + response.getRedirect().getNodeId().substring(0, 4));
        } else {
            System.out.println("Node " + nodeId.substring(0, 4) + " responds with success");
        }
        return response;
    }
}
