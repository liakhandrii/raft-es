package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LocalTest {

    static private List<MockNodeAccessor> accessors = new ArrayList<>();
    static private Map<String, MockNodeAccessor> accessorsMap = new HashMap<>();

    public static List<MockNodeAccessor> generateNodes(int count, boolean startNodes) {
        List<MockNodeAccessor> accessors = new ArrayList<>();

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

    private static void startClientTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Client sends a request");
                MockNodeAccessor node = accessors.get(new Random().nextInt(5));
                ClientRequest request = new ClientRequest(UUID.randomUUID().toString());
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

    private static void startKillTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("A node gets restarted");
                MockNodeAccessor node = accessors.get(new Random().nextInt(5));
                node.killNode();
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                node.reviveNode();
            }
        }, 15000, 150000);
    }

    private static void startMonitorTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                accessors.forEach(localNodeAccessor -> {
                    int size = localNodeAccessor.node.entries.size();
                    if (size > 0) {
                        System.out.println(localNodeAccessor.node.entries.subList(Math.max(size - 4, 0), size));
                    }
                });
            }
        }, 10000, 10000);
    }
}
