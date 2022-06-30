package com.liakhandrii.es.raft.models;

public class VoteRequest {
    private long candidateTerm;
    private long candidateId;
    private long lastLogIndex;
    private long lastLogTerm;

    public long getCandidateTerm() {
        return candidateTerm;
    }

    public long getCandidateId() {
        return candidateId;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }
}

