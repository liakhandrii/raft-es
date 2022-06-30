package com.liakhandrii.es.raft.models;

public class VoteResponse {
    private long responderTerm;
    private boolean didReceiveVote;

    public VoteResponse(long responderTerm, boolean didReceiveVote) {
        this.responderTerm = responderTerm;
        this.didReceiveVote = didReceiveVote;
    }

    public static VoteResponse voted(long responderTerm) {
        return new VoteResponse(responderTerm, true);
    }

    public static VoteResponse rejected(long responderTerm) {
        return new VoteResponse(responderTerm, false);
    }

    public long getResponderTerm() {
        return responderTerm;
    }

    public boolean didReceiveVote() {
        return didReceiveVote;
    }
}
