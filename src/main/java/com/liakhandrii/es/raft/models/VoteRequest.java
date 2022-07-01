package com.liakhandrii.es.raft.models;

public class VoteRequest {
    private long candidateTerm;
    private String candidateId;

    /**
     * Indicates the index of the last entry stored by the candidate
     */
    private long lastEntryIndex;

    /**
     * Indicates the term of the last entry stored by the candidate
     */
    private long lastEntryTerm;

    public VoteRequest(long candidateTerm, String candidateId, long lastEntryIndex, long lastEntryTerm) {
        this.candidateTerm = candidateTerm;
        this.candidateId = candidateId;
        this.lastEntryIndex = lastEntryIndex;
        this.lastEntryTerm = lastEntryTerm;
    }

    public long getCandidateTerm() {
        return candidateTerm;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public long getLastEntryIndex() {
        return lastEntryIndex;
    }

    public long getLastEntryTerm() {
        return lastEntryTerm;
    }
}

