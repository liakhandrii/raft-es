package com.liakhandrii.es.implementation.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liakhandrii.es.raft.NodeAccessor;
import com.liakhandrii.es.raft.models.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpNodeAccessor extends NodeAccessor<String> {

    private String protocol;
    private String host;
    private int port;
    private HttpClient client;
    private ObjectMapper mapper;

    public HttpNodeAccessor(String protocol, String host, int port) {
        this.nodeId = String.valueOf(port);
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();
    }

    @Override
    public AppendEntriesResponse sendAppendEntriesRequest(AppendEntriesRequest<String> request) {
        System.out.println("Leader " + request.getLeaderId() + " sends " + request.getEntries().size() + " entries to " + nodeId);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(
                    URI.create(protocol + "://" + host + ":" + port + "/appendEntries"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            AppendEntriesResponse response = mapper.readValue(httpResponse.body(), AppendEntriesResponse.class);
            if (response.isSuccessful()) {
                System.out.println("Node " + response.getResponderId() + " accepted new entries from " + request.getLeaderId());
            } else {
                System.out.println("Node " + response.getResponderId() + " rejects. Reason: " + response.getReason());
            }
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public VoteResponse sendVoteRequest(VoteRequest request) {
        System.out.println("Candidate " + request.getCandidateId() + " term " + request.getCandidateTerm() + " sends a vote request to " + nodeId + " at " + System.currentTimeMillis());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(
                    URI.create(protocol + "://" + host + ":" + port + "/requestVote"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            VoteResponse response = mapper.readValue(httpResponse.body(), VoteResponse.class);
            System.out.println("Node " + response.getResponderId() + " votes " + response.getDidReceiveVote());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getPort() {
        return port;
    }

    @Override
    public String getNodeId() {
        return String.valueOf(port);
    }
}
