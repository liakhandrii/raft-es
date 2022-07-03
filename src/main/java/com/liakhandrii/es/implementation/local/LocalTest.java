package com.liakhandrii.es.implementation.local;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.NodeAccessor;
import com.liakhandrii.es.raft.NodeCore;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LocalTest<T extends LocalNodeAccessor> {

    public List<T> accessors = new ArrayList<>();
    public Map<String, T> accessorsMap = new HashMap<>();

    public static void main(String[] args) {
        LocalTest<LocalNodeAccessor> test = new LocalTest<>();
        test.setAccessors(generateNodes(5));
        test.start();
    }

    private static List<LocalNodeAccessor> generateNodes(int count) {
        List<LocalNodeAccessor> accessors = new ArrayList<>();

        for (int i = 0; i < count; i += 1) {
            NodeCore<String> node = new NodeCore<>();
            accessors.add(new LocalNodeAccessor(node));
        }

        return accessors;
    }

    public void setAccessors(List<T> nodes) {
        this.accessors = nodes;
        accessorsMap = accessors.stream().collect(Collectors.toMap(T::getNodeId, Function.identity()));
        accessors.forEach(nodeAccessor -> {
            for (int i = 0; i < accessors.size(); i += 1) {
                accessors.get(i).node.registerOtherNode(nodeAccessor);
            }
        });
    }

    public void start() {
        startClientTimer();
        startKillTimer();
        startMonitorTimer();

        accessors.forEach(accessor -> accessor.node.startNode());

        System.out.println("running");
    }

    public void startClientTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Client sends a request");
                T node = accessors.get(new Random().nextInt(5));
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

    public void startKillTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("A node gets restarted");
                boolean killLeader = new Random().nextBoolean();
                T node = accessors.get(new Random().nextInt(5));
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

    public void startMonitorTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                accessors.forEach(T -> {
                    int size = T.node.getEntries().size();
                    if (size > 0) {
                        System.out.println(T.node.getEntries().subList(Math.max(size - 4, 0), size).toString());
                    }
                });
            }
        }, 10000, 10000);
    }
}
