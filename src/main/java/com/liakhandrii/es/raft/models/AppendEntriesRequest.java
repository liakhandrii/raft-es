package com.liakhandrii.es.raft.models;


import java.util.List;

public class AppendEntriesRequest {
    private long leaderTerm;
    private long leaderId;

    private long previousIndex; // Indicates the index of an entry preceding the entry we're sending now
    private long previousTerm; // Indicates the term of an entry preceding the entry we're sending now

    private List<Object> entries; // We don't care about the data type at this point, it's up to the nodes to convert them from / to a right type

    private long commitIndex; // Indicates the last entry to be known to be saved on a majority of nodes
}

