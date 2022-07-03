package com.liakhandrii.es.implementation.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liakhandrii.es.implementation.http.models.HttpClientRequest;
import com.liakhandrii.es.implementation.http.models.HttpClientResponse;
import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.NodeCore;
import com.liakhandrii.es.raft.models.*;
import io.javalin.Javalin;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

public class HttpNodeServer {

    public NodeCore<String> node;
    private int port;
    private Javalin httpServer;
    private ObjectMapper mapper;

    public HttpNodeServer(NodeCore<String> node, int port) {
        this.node = node;
        this.port = port;
        mapper = new ObjectMapper();
        httpServer = Javalin.create().start(port);
        configureServer();
    }

    private void configureServer() {
        httpServer.get("/entry", ctx -> {
            // I deliberately make no redirect here, as I want to be able to see entries stored on each node
            if (node.getEntries().size() > 0) {
                Entry<String> entry = node.getEntries().get(node.getEntries().size() - 1);
                HttpClientResponse response = new HttpClientResponse(entry.getData(), (int)entry.getTerm(), (int)entry.getIndex());

                ctx.json(response);
            } else {
                ctx.res.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        httpServer.get("/entries", ctx -> {
            // I deliberately make no redirect here, as I want to be able to see entries stored on each node
            List<Entry<String>> entries = node.getEntries();
            List<HttpClientResponse> responseEntries = new ArrayList<>();
            for (Entry<String> entry: entries) {
                HttpClientResponse response = new HttpClientResponse(entry.getData(), (int)entry.getTerm(), (int)entry.getIndex());
                responseEntries.add(response);
            }

            ctx.json(responseEntries);
        });

        httpServer.post("/entries", ctx -> {
            HttpClientRequest request = ctx.bodyAsClass(HttpClientRequest.class);
            ClientResponse response = node.receiveClientRequest(new ClientRequest<>(request.getData()));
            if (!response.isSuccess() && response.getRedirect() != null) {
                int redirectPort = ((HttpNodeAccessor) response.getRedirect()).getPort();
                String redirect = "http://" + ctx.host().replace(String.valueOf(port), String.valueOf(redirectPort)) + ctx.path();
                ctx.res.sendRedirect(redirect);
            } else if (!response.isSuccess() && response.getRedirect() == null) {
                ctx.res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else {
                ctx.res.setStatus(HttpServletResponse.SC_CREATED);
            }
        });

        httpServer.post("/appendEntries", ctx -> {
            AppendEntriesRequest<String> request = ctx.bodyAsClass(AppendEntriesRequest.class);

            AppendEntriesResponse response = node.receiveEntries(request);

            ctx.json(response);
        });

        httpServer.post("/requestVote", ctx -> {
            VoteRequest request = ctx.bodyAsClass(VoteRequest.class);

            VoteResponse response = node.receiveVoteRequest(request);

            ctx.json(response);
        });
    }
}
