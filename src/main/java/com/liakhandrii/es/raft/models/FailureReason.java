package com.liakhandrii.es.raft.models;

public enum FailureReason {
    LEADER_TERM_OUTDATED, FOLLOWER_MISSING_ENTRIES
}
