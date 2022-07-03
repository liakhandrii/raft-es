package com.liakhandrii.es.implementation.http.models;

public class HttpClientResponse {
    private String data;
    private int term;
    private int index;

    public HttpClientResponse(String data, int term, int index) {
        this.data = data;
        this.term = term;
        this.index = index;
    }

    public String getData() {
        return data;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }
}
