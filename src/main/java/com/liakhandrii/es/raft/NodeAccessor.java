package com.liakhandrii.es.raft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.liakhandrii.es.raft.models.AppendEntriesRequest;
import com.liakhandrii.es.raft.models.AppendEntriesResponse;
import com.liakhandrii.es.raft.models.VoteRequest;
import com.liakhandrii.es.raft.models.VoteResponse;

import java.util.Objects;

abstract public class NodeAccessor<T> {
    protected String nodeId;

    abstract public AppendEntriesResponse sendAppendEntriesRequest(AppendEntriesRequest<T> request);
    abstract public VoteResponse sendVoteRequest(VoteRequest request);

    public String getNodeId() {
        return nodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeAccessor)) return false;
        NodeAccessor<T> that = (NodeAccessor<T>) o;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}
