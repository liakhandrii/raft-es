package com.liakhandrii.es.implementation.local;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.nodes.NodeCore;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LocalTest {

    static private List<LocalNodeAccessor> accessors = new ArrayList<>();
    static private Map<String, LocalNodeAccessor> accessorsMap = new HashMap<>();

    public static void main(String[] args) {
        NodeCore<String> node1 = new NodeCore<>();
        NodeCore<String> node2 = new NodeCore<>();
        NodeCore<String> node3 = new NodeCore<>();
        NodeCore<String> node4 = new NodeCore<>();
        NodeCore<String> node5 = new NodeCore<>();
        accessors.add(new LocalNodeAccessor(node1));
        accessors.add(new LocalNodeAccessor(node2));
        accessors.add(new LocalNodeAccessor(node3));
        accessors.add(new LocalNodeAccessor(node4));
        accessors.add(new LocalNodeAccessor(node5));

        accessorsMap = accessors.stream().collect(Collectors.toMap(LocalNodeAccessor::getNodeId, Function.identity()));

        accessors.forEach(nodeAccessor -> {
            node1.registerOtherNode(nodeAccessor);
            node2.registerOtherNode(nodeAccessor);
            node3.registerOtherNode(nodeAccessor);
            node4.registerOtherNode(nodeAccessor);
            node5.registerOtherNode(nodeAccessor);
        });

        Thread thread1 = new Thread(() -> node1.startNode());
        thread1.start();

        Thread thread2 = new Thread(() -> node2.startNode());
        thread2.start();

        Thread thread3 = new Thread(() -> node3.startNode());
        thread3.start();

        Thread thread4 = new Thread(() -> node4.startNode());
        thread4.start();

        Thread thread5 = new Thread(() -> node5.startNode());
        thread5.start();

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
                LocalNodeAccessor node = accessors.get(new Random().nextInt(5));
                ClientRequest request = new ClientRequest(UUID.randomUUID().toString());
                ClientResponse response = node.sendClientRequest(request);
                if (response.getRedirect() != null) {
                    try {
                        accessorsMap.get(response.getRedirect().getNodeId()).sendClientRequest(request);
                    } catch (NullPointerException e) {
                        System.out.println("Bad redirect to " + response.getRedirect().getNodeId());
                    }
                }
            }
        }, 5434, 5434);
    }

    private static void startKillTimer() {
//        Timer timer = new Timer();
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.out.println("A node gets restarted");
//                LocalNodeAccessor node = accessors.get(new Random().nextInt(5));
//                node.killNode();
//            }
//        }, 15000, 15000);
    }

    private static void startMonitorTimer() {
//        Timer timer = new Timer();
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                accessors.forEach(localNodeAccessor -> {
//                    System.out.println(localNodeAccessor.node.getEntries());
//                });
//            }
//        }, 10000, 10000);
    }
}
