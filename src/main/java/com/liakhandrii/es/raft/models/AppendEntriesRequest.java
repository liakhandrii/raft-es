package com.liakhandrii.es.raft.models;


import java.util.List;

public class AppendEntriesRequest<T> {
    private long leaderTerm;
    private String leaderId;

    /**
     * Indicates the index of an entry preceding the entry we're sending now
     */
    private long previousIndex;

    /**
     * Indicates the term of an entry preceding the entry we're sending now
     */
    private long previousTerm;

    /**
     * All the new entries the receiver has to store, empty for a heartbeat
     */
    private List<Entry<T>> entries;

    /**
     * Indicates the last entry to be known to be saved on a majority of nodes
     */
    private long commitIndex;

    public AppendEntriesRequest(long leaderTerm, String leaderId, Long previousIndex, Long previousTerm, List<Entry<T>> entries, Long commitIndex) {
        this.leaderTerm = leaderTerm;
        this.leaderId = leaderId;
        this.previousIndex = previousIndex;
        this.previousTerm = previousTerm;
        this.entries = entries;
        this.commitIndex = commitIndex;
    }

    public long getLeaderTerm() {
        return leaderTerm;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public long getPreviousIndex() {
        return previousIndex;
    }

    public long getPreviousTerm() {
        return previousTerm;
    }

    public List<Entry<T>> getEntries() {
        return entries;
    }

    public long getCommitIndex() {
        return commitIndex;
    }
}

