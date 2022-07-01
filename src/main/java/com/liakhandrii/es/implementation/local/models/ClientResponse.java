package com.liakhandrii.es.implementation.local.models;

import com.liakhandrii.es.raft.NodeAccessor;

public class ClientResponse {
    private boolean success;
    private NodeAccessor redirect;

    public ClientResponse(boolean success, NodeAccessor redirect) {
        this.success = success;
        this.redirect = redirect;
    }

    public boolean isSuccess() {
        return success;
    }

    public NodeAccessor getRedirect() {
        return redirect;
    }
}
