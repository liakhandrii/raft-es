package com.liakhandrii.es.raft.models;


import java.util.List;
import java.util.List;

public class AppendEntriesRequest<T> {
    private long leaderTerm;
    private String leaderId;

    /**
     * Indicates the index of an entry preceding the entry we're sending now
     */
    private Long previousIndex;

    /**
     * Indicates the term of an entry preceding the entry we're sending now
     */
    private Long previousTerm;

    /**
     * All the new entries the receiver has to store, empty for a heartbeat
     */
    private List<Entry<T>> entries;

    /**
     * Indicates the last entry to be known to be saved on a majority of nodes
     */
    private Long commitIndex;

    private String messageId;

    public AppendEntriesRequest(long leaderTerm, String leaderId, Long previousIndex, Long previousTerm, List<Entry<T>> entries, Long commitIndex, String messageId) {
        this.leaderTerm = leaderTerm;
        this.leaderId = leaderId;
        this.previousIndex = previousIndex;
        this.previousTerm = previousTerm;
        this.entries = entries;
        this.commitIndex = commitIndex;
        this.messageId = messageId;
    }

    public Long getLeaderTerm() {
        return leaderTerm;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public Long getPreviousIndex() {
        return previousIndex;
    }

    public Long getPreviousTerm() {
        return previousTerm;
    }

    public List<Entry<T>> getEntries() {
        return entries;
    }

    public Long getCommitIndex() {
        return commitIndex;
    }

    public String getMessageId() {
        return messageId;
    }
}

