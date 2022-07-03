package com.liakhandrii.es.implementation.http.models;

import com.fasterxml.jackson.annotation.JsonCreator;

public class HttpClientRequest {

    private String data;

    private HttpClientRequest() {

    }

    public HttpClientRequest(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}
