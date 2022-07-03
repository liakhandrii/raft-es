package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.NodeRank;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LocalTest {

    static public Vector<MockNodeAccessor> accessors = new Vector<>();
    static public Map<String, MockNodeAccessor> accessorsMap = new ConcurrentHashMap<>();

    public static Vector<MockNodeAccessor> generateNodes(int count, boolean startNodes) {
        Vector<MockNodeAccessor> accessors = new Vector<>();

        for (int i = 0; i < count; i += 1) {
            NodeCore<String> node = new NodeCore<>();
            accessors.add(new MockNodeAccessor(node));
        }

        accessorsMap = accessors.stream().collect(Collectors.toMap(MockNodeAccessor::getNodeId, Function.identity()));
        accessors.forEach(nodeAccessor -> {
            for (int i = 0; i < count; i += 1) {
                accessors.get(i).node.registerOtherNode(nodeAccessor);
            }
        });

        if (startNodes) {
            for (int i = 0; i < count; i += 1) {
                int finalI = i;
                Thread thread = new Thread(() -> accessors.get(finalI).node.startNode());
                thread.start();
            }
        }

        return accessors;
    }

    public static void main(String[] args) {
        accessors = generateNodes(5, true);

        startClientTimer();
        startKillTimer();
        startMonitorTimer();

        System.out.println("running");
    }

    public static void startClientTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Client sends a request");
                MockNodeAccessor node = accessors.get(new Random().nextInt(5));
                ClientRequest<String> request = new ClientRequest<>(UUID.randomUUID().toString());
                ClientResponse response = node.sendClientRequest(request);
                if (response != null && response.getRedirect() != null) {
                    try {
                        accessorsMap.get(response.getRedirect().getNodeId()).sendClientRequest(request);
                    } catch (NullPointerException e) {
                        System.out.println("Bad redirect to " + response.getRedirect().getNodeId());
                    }
                }
            }
        }, 2000, 2000);
    }

    public static void startKillTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("A node gets restarted");
                boolean killLeader = new Random().nextBoolean();
                MockNodeAccessor node = accessors.get(new Random().nextInt(5));
                node.killNode();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                node.reviveNode();
            }
        }, 5000, 5000);
    }

    public static void startMonitorTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                accessors.forEach(localNodeAccessor -> {
                    int size = localNodeAccessor.node.entries.size();
                    if (size > 0) {
                        System.out.println(localNodeAccessor.node.entries.subList(Math.max(size - 4, 0), size).toString());
                    }
                });
            }
        }, 10000, 10000);
    }
}
