package com.liakhandrii.es.raft.models;

public class AppendEntriesResponse {
    private long responderTerm;
    private boolean isSuccessful;

    private AppendEntriesResponse(long responderTerm, boolean isSuccessful) {
        this.responderTerm = responderTerm;
        this.isSuccessful = isSuccessful;
    }

    public static AppendEntriesResponse succesful(long responderTerm) {
        return new AppendEntriesResponse(responderTerm, true);
    }

    public static AppendEntriesResponse failed(long responderTerm) {
        return new AppendEntriesResponse(responderTerm, false);
    }

    public long getResponderTerm() {
        return responderTerm;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }
}
