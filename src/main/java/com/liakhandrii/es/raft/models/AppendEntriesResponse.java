package com.liakhandrii.es.raft.models;

public class AppendEntriesResponse {
    private long responderTerm;
    private long lastEntryIndex;
    private boolean isSuccessful;
    private FailureReason reason;
    private String responderId;
    private String messageId;

    private AppendEntriesResponse(long responderTerm, long lastEntryIndex, boolean isSuccessful, FailureReason reason, String responderId, String messageId) {
        this.responderTerm = responderTerm;
        this.lastEntryIndex = lastEntryIndex;
        this.isSuccessful = isSuccessful;
        this.reason = reason;
        this.responderId = responderId;
        this.messageId = messageId;
    }

    /**
     * Creates a response indicating we saved the received entries
     * @param responderTerm the current term of the node sending the response
     * @return a new AppendEntriesResponse object, configured per our needs
     */
    public static AppendEntriesResponse succesful(long responderTerm, long lastEntryIndex, String responderId, String messageId) {
        return new AppendEntriesResponse(responderTerm, lastEntryIndex, true, null, responderId, messageId);
    }

    /**
     * Creates a response indicating something went wrong
     * @param responderTerm the current term of the node sending the response
     * @param reason the failure reason
     * @return a new AppendEntriesResponse object, configured per our needs
     */
    public static AppendEntriesResponse failed(long responderTerm, long lastEntryIndex, String responderId, String messageId, FailureReason reason) {
        return new AppendEntriesResponse(responderTerm, lastEntryIndex, false, reason, responderId, messageId);
    }

    public long getResponderTerm() {
        return responderTerm;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public FailureReason getReason() {
        return reason;
    }

    public long getLastEntryIndex() {
        return lastEntryIndex;
    }

    public String getResponderId() {
        return responderId;
    }

    public String getMessageId() {
        return messageId;
    }
}
