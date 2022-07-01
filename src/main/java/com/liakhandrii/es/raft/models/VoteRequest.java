package com.liakhandrii.es.raft.models;

public class VoteRequest {
    private final long candidateTerm;
    private final String candidateId;

    /**
     * Indicates the index of the last entry stored by the candidate
     */
    private final Long lastEntryIndex;

    /**
     * Indicates the term of the last entry stored by the candidate
     */
    private final Long lastEntryTerm;

    private final String messageId;

    public VoteRequest(long candidateTerm, String candidateId, Long lastEntryIndex, Long lastEntryTerm, String messageId) {
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

    public Long getLastEntryIndex() {
        return lastEntryIndex;
    }

    public Long getLastEntryTerm() {
        return lastEntryTerm;
    }

    public String getMessageId() {
        return messageId;
    }
}

