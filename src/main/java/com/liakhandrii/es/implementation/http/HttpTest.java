package com.liakhandrii.es.implementation.http;

import com.liakhandrii.es.raft.NodeCore;
import io.javalin.Javalin;

import java.util.*;

public class HttpTest {

    private List<HttpNodeAccessor> accessors = new ArrayList<>();
    private List<HttpNodeServer> servers = new ArrayList<>();

    public static void main(String[] args) {
        int count = 5;
        int startPort = 8085;
        HttpTest test = new HttpTest();
        List<HttpNodeAccessor> accessors = createAccessors(count, startPort);
        List<HttpNodeServer> servers = startServers(accessors);
        test.setAccessors(accessors);
        test.setServers(servers);

        servers.forEach(httpNodeServer -> {
            httpNodeServer.node.startNode();
        });
    }

    private static List<HttpNodeServer> startServers(List<HttpNodeAccessor> accessors) {
        List<HttpNodeServer> servers = new ArrayList<>();

        accessors.forEach(httpNodeAccessor -> {
            NodeCore<String> node = new NodeCore<>(500, 10000, String.valueOf(httpNodeAccessor.getPort()));
            accessors.forEach(node::registerOtherNode);
            servers.add(new HttpNodeServer(node, httpNodeAccessor.getPort()));
        });

        return servers;
    }

    private static List<HttpNodeAccessor> createAccessors(int count, int startingPort) {
        List<HttpNodeAccessor> accessors = new ArrayList<>();

        for (int i = 0; i < count; i += 1) {
            accessors.add(new HttpNodeAccessor("http", "localhost", startingPort + i));
        }

        return accessors;
    }

    public void setAccessors(List<HttpNodeAccessor> accessors) {
        this.accessors = accessors;
    }

    public void setServers(List<HttpNodeServer> servers) {
        this.servers = servers;
    }
}
