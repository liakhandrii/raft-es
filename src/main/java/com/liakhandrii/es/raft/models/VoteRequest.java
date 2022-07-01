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

    private String messageId;

    public VoteRequest(long candidateTerm, String candidateId, long lastEntryIndex, long lastEntryTerm, String messageId) {
        this.candidateTerm = candidateTerm;
        this.candidateId = candidateId;
        this.lastEntryIndex = lastEntryIndex;
        this.lastEntryTerm = lastEntryTerm;
        this.messageId = messageId;
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

    public String getMessageId() {
        return messageId;
    }
}

