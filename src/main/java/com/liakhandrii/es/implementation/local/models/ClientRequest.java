package com.liakhandrii.es.implementation.local.models;

public class ClientRequest<T> {
    private T value;

    public ClientRequest(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}
