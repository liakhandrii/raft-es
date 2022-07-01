package com.liakhandrii.es.raft;

import com.liakhandrii.es.raft.models.AppendEntriesRequest;
import com.liakhandrii.es.raft.models.AppendEntriesResponse;
import com.liakhandrii.es.raft.models.VoteRequest;
import com.liakhandrii.es.raft.models.VoteResponse;

import java.util.Objects;

abstract public class NodeAccessor<T> {
    protected String nodeId;

    abstract public void sendAppendEntriesRequest(AppendEntriesRequest<T> request);
    abstract public void sendVoteRequest(VoteRequest request);

    abstract public void sendAppendEntriesResponse(AppendEntriesResponse response);
    abstract public void sendVoteResponse(VoteResponse response);

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
