package com.liakhandrii.es.raft.nodes;

import com.liakhandrii.es.raft.models.*;

/**
 * A class prototype destined to represent a basis of what each Raft node has to have to achieve minimum functionality.
 */
abstract public class NodeCore {
    protected NodeRank rank = NodeRank.FOLLOWER;
    protected long electionTimeout = 500; // value in milliseconds, should be randomized
    protected long currentTerm = 0;

    abstract public VoteResponse receiveVoteRequest(VoteRequest request);
    abstract public AppendEntriesResponse receiveEntries(AppendEntriesRequest request);
}
