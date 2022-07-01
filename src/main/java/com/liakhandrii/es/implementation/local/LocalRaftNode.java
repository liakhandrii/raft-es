package com.liakhandrii.es.implementation.local;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;
import com.liakhandrii.es.raft.nodes.NodeAccessor;
import com.liakhandrii.es.raft.nodes.NodeCore;

import java.util.*;

public class LocalRaftNode {

//    private List<Entry<String>> entries = new ArrayList<>();
//    private Map<String, Long> lastNodeIndexes = new HashMap<>();
//
//    public LocalRaftNode() {
//        super();
//    }
//
//    public void killNode() {
//        currentTerm = 0;
//        rank = NodeRank.FOLLOWER;
//        entries = new ArrayList<>();
//        lastNodeIndexes = new HashMap<>();
//    }
//
//    public ClientResponse receiveClientRequest(ClientRequest request) {
//        if (rank == NodeRank.LEADER) {
//            entries.add(new Entry<>(request.getValue(), currentTerm, currentIndex() + 1));
//            return new ClientResponse(true, null);
//        } else {
//            return new ClientResponse(false, currentLeader);
//        }
//    }
//
//    @Override
//    public AppendEntriesResponse receiveEntries(AppendEntriesRequest<String> request) {
//        super.receiveEntries(request);
//        FailureReason failure = validateAppendRequest(request);
//        if (failure != null) {
//            return AppendEntriesResponse.failed(currentTerm, currentIndex(), failure);
//        }
//
//        // In case we have some extra entries – they have to be overridden. The leader is the only source of truth
//        if (request.getPreviousIndex() < currentIndex()) {
//            entries = entries.subList(0, (int)request.getPreviousIndex() + 1);
//        }
//
//        entries.addAll(request.getEntries());
//
//        return AppendEntriesResponse.succesful(currentTerm, currentIndex());
//    }
//
//    @Override
//    protected AppendEntriesResponse sendEntries(NodeAccessor nodeAccessor) {
//        Long lastNodeIndex = lastNodeIndexes.get(nodeAccessor.getNodeId());
//        AppendEntriesRequest<String> request;
//        if (lastNodeIndex == null) {
//            lastNodeIndex = -1L;
//        }
//
//        Entry<String> lastNodeEntry = null;
//        if (lastNodeIndex > 0 && lastNodeIndex < entries.size()) {
//            lastNodeEntry = entries.get(lastNodeIndex.intValue());
//        }
//
//        List<Entry<String>> newEntries;
//        if (lastNodeIndex < currentIndex()) {
//            newEntries = entries.subList(lastNodeIndex.intValue() + 1, entries.size());
//        } else {
//            newEntries = new ArrayList<>();
//        }
//
//        if (lastNodeEntry != null) {
//            request = new AppendEntriesRequest<>(currentTerm, id, lastNodeIndex, lastNodeEntry.getTerm(), newEntries, commitIndex(), messageId);
//        } else {
//            request = new AppendEntriesRequest<String>(currentTerm, id, -1, currentTerm, newEntries, commitIndex(), messageId);
//        }
//
//        AppendEntriesResponse response = nodeAccessor.sendAppendEntriesRequest(request);
//        if (response.isSuccessful() && newEntries.size() > 0) {
//            lastNodeIndexes.put(nodeAccessor.getNodeId(), newEntries.get(newEntries.size() - 1).getIndex());
//        }
//
//        if (response.getReason() != null) {
//            if (response.getReason() == FailureReason.LEADER_TERM_OUTDATED) {
//                becomeFollower(null);
//            } else if (response.getReason() == FailureReason.DATA_INCONSISTENTCY) {
//                lastNodeIndexes.put(nodeAccessor.getNodeId(), response.getLastEntryIndex());
//            }
//        }
//
//        return response;
//    }
//
//    @Override
//    protected long lastEntryTerm() {
//        if (entries.isEmpty()) {
//            return 0;
//        }
//        return entries.get(entries.size() - 1).getTerm();
//    }
//
//    @Override
//    protected long commitIndex() {
//        // TODO:
//        return 0;
//    }
//
//    public List<Entry<String>> getEntries() {
//        return entries;
//    }
}
