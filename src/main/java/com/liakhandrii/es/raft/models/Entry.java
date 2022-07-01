package com.liakhandrii.es.raft.models;

public class Entry<T> {
    private T data;
    private long term;
    private long index;

    public Entry(T data, long term, long index) {
        this.data = data;
        this.term = term;
        this.index = index;
    }

    public T getData() {
        return data;
    }

    public long getTerm() {
        return term;
    }

    public long getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return "Entry{" +
                "data=" + data.toString().substring(0, 4) +
                ", term=" + term +
                ", index=" + index +
                '}';
    }
}
