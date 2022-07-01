package com.liakhandrii.es.raft.models;

public class VoteResponse {
    private long responderTerm;
    private boolean didReceiveVote;
    private String responderId;

    private VoteResponse(long responderTerm, boolean didReceiveVote, String responderId) {
        this.responderTerm = responderTerm;
        this.didReceiveVote = didReceiveVote;
        this.responderId = responderId;
    }

    /**
     * Creates a response indicating we did vote for a candidate
     * @param responderTerm the current term of the node sending the response
     * @return a new VoteResponse object, configured per our needs
     */
    public static VoteResponse voted(long responderTerm, String responderId) {
        return new VoteResponse(responderTerm, true, responderId);
    }

    /**
     * Creates a response indicating we did NOT vote for a candidate
     * @param responderTerm the current term of the node sending the response
     * @return a new VoteResponse object, configured per our needs
     */
    public static VoteResponse rejected(long responderTerm, String responderId) {
        return new VoteResponse(responderTerm, false, responderId);
    }

    public long getResponderTerm() {
        return responderTerm;
    }

    public boolean didReceiveVote() {
        return didReceiveVote;
    }

    public String getResponderId() {
        return responderId;
    }
}
