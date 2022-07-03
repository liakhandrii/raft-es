package com.liakhandrii.es.raft.models;

public class VoteResponse {
    private long responderTerm;
    private boolean didReceiveVote;
    private String responderId;
    private String messageId;

    private VoteResponse() {

    }

    public VoteResponse(long responderTerm, boolean didReceiveVote, String responderId, String messageId) {
        this.responderTerm = responderTerm;
        this.didReceiveVote = didReceiveVote;
        this.responderId = responderId;
        this.messageId = messageId;
    }

    /**
     * Creates a response indicating we did vote for a candidate
     * @param responderTerm the current term of the node sending the response
     * @return a new VoteResponse object, configured per our needs
     */
    public static VoteResponse voted(long responderTerm, String responderId, String messageId) {
        return new VoteResponse(responderTerm, true, responderId, messageId);
    }

    /**
     * Creates a response indicating we did NOT vote for a candidate
     * @param responderTerm the current term of the node sending the response
     * @return a new VoteResponse object, configured per our needs
     */
    public static VoteResponse rejected(long responderTerm, String responderId, String messageId) {
        return new VoteResponse(responderTerm, false, responderId, messageId);
    }

    public long getResponderTerm() {
        return responderTerm;
    }

    public boolean getDidReceiveVote() {
        return didReceiveVote;
    }

    public String getResponderId() {
        return responderId;
    }

    public String getMessageId() {
        return messageId;
    }
}
