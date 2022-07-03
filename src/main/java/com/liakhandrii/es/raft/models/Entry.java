package com.liakhandrii.es.raft.models;

import java.util.Objects;

public class Entry<T> {
    private T data;
    private long term;
    private long index;

    private Entry() {

    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entry)) return false;
        Entry<?> entry = (Entry<?>) o;
        return term == entry.term && index == entry.index && Objects.equals(data, entry.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, term, index);
    }
}
