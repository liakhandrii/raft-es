package com.liakhandrii.es.raft.nodes;

import com.liakhandrii.es.raft.models.*;

import java.nio.charset.Charset;
import java.util.*;

/**
 * A class prototype destined to represent a basis of what each Raft node has to have to achieve minimum functionality.
 */
abstract public class NodeCore<T> {
    /**
     * This field indicates how the node should act, because it depends heavily on the role it currently employs
     */
    protected NodeRank rank = NodeRank.FOLLOWER;

    /**
     * Value in milliseconds, should be randomized
     */
    protected long electionTimeout;

    /**
     * Value in milliseconds
     */
    protected long heartbeatInterval = 500;

    /**
     * The current term of a node, we use this value to compare it with term of other nodes we receive requests from,
     * and react accordingly if we're behind on a term
     */
    protected long currentTerm = 0;

    /**
     * We store which candidate we voted for on this term, not to vote twice on the same term
     */
    protected String votedId = null;

    protected NodeAccessor<T> currentLeader;

    /**
     * This timer fires on an interval, unless it's reset by a heartbeat
     */
    protected Timer electionTimer;

    /**
     * This timer fires regularly on an interval
     */
    protected Timer heartbeatTimer;

    /**
     * Provides a way of identyfying this node among others
     */
    protected String id;

    /**
     * Keeps references to other nodes in the cluster
     */
    protected Map<String, NodeAccessor<T>> otherNodes;

    protected Map<String, Boolean> receivedVotes;

    private Map<String, Long> commitIndexes = new HashMap<>();
    private Map<String, Long> nextIndexes = new HashMap<>();
    private long commitIndex = 0;

    /**
     * Creates a new NodeCore with an election timeout randomized from 400 to 599 ms
     * Always call this constructor in the subclasses
     */
    protected NodeCore() {
        electionTimeout = 4000 + (new Random().nextInt(2000));
        id = UUID.randomUUID().toString();
        otherNodes = new HashMap<>();
    }

    /**
     * This is common node functionality, so it's implemented in the NodeCore.
     * @param request A request object with the sender's info
     * @return A reply with our decision and our info
     */
    public VoteResponse receiveVoteRequest(VoteRequest request) {
        // We don't vote if we voted on this term already
        if (votedId != null) {
            return VoteResponse.rejected(currentTerm, id);
        }

        // We don't vote for those whose term is lower than ours
        if (request.getCandidateTerm() <= currentTerm) {
            return VoteResponse.rejected(currentTerm, id);
        }

        // We also don't vote for those whose term is the same as ours, but the last entry index is lower
        if (request.getCandidateTerm() == currentTerm && request.getLastEntryIndex() < currentIndex()) {
            return VoteResponse.rejected(currentTerm, id);
        }

        votedId = request.getCandidateId();
        return VoteResponse.voted(currentTerm, id);
    }

    protected void sendVoteRequest(NodeAccessor<T> nodeAccessor) {
        if (rank != NodeRank.CANDIDATE) {
            return;
        }

        VoteRequest request = new VoteRequest(currentTerm, id, currentIndex(), lastEntryTerm());
        VoteResponse response = nodeAccessor.sendVoteRequest(request);
        if (response.getResponderTerm() > currentTerm) {
            becomeFollower(response.getResponderId());
        } else {
            receivedVotes.put(response.getResponderId(), response.didReceiveVote());
            if (countVotes() > majoritySize()) {
                becomeLeader();
            }
        }
    }

    protected void sendEntries(NodeAccessor nodeAccessor, boolean empty) {
        AppendEntriesRequest<T> request;

        if (empty) {
            // Just sending a heartbeat
            request = new AppendEntriesRequest<T>(currentTerm, id, null, null, new ArrayList<>(), null);
        } else {
            Long lastNodeIndex = lastNodeIndexes.get(nodeAccessor.getNodeId());

            if (lastNodeIndex == null) {
                lastNodeIndex = -1L;
            }

            Entry<String> lastNodeEntry = null;
            if (lastNodeIndex > 0 && lastNodeIndex < entries.size()) {
                lastNodeEntry = entries.get(lastNodeIndex.intValue());
            }

            List<Entry<String>> newEntries;
            if (lastNodeIndex < currentIndex()) {
                newEntries = entries.subList(lastNodeIndex.intValue() + 1, entries.size());
            } else {
                newEntries = new ArrayList<>();
            }

            if (lastNodeEntry != null) {
                request = new AppendEntriesRequest<T>(currentTerm, id, lastNodeIndex, lastNodeEntry.getTerm(), newEntries, commitIndex());
            } else {
                request = new AppendEntriesRequest<T>(currentTerm, id, -1, currentTerm, newEntries, commitIndex());
            }
        }


        AppendEntriesResponse response = nodeAccessor.sendAppendEntriesRequest(request);
        processEntriesResponse(response);
    }

    public void processEntriesResponse(AppendEntriesResponse response) {

        if (response.isSuccessful()) {
            Long matchingIndex = response.getLastEntryIndex();
            commitIndexes.put(response.getResponderId(), matchingIndex);
            nextIndexes.put(response.getResponderId(), matchingIndex + 1);
            calculateCommitIndex();
        } else {

        }

        if (response.getReason() != null) {
            if (response.getReason() == FailureReason.LEADER_TERM_OUTDATED) {
                becomeFollower(null);
            } else if (response.getReason() == FailureReason.FOLLOWER_MISSING_ENTRIES) {
                lastNodeIndexes.put(nodeAccessor.getNodeId(), response.getLastEntryIndex());
            }
        }
    }

    private void calculateCommitIndex() {
        long potentialMajorityIndex = currentIndex();
        boolean indexInMajority = false;
        while (!indexInMajority && potentialMajorityIndex > 0) {
            long matchingNodes = commitIndexes.values().stream().filter(index -> index >= potentialMajorityIndex).count();
            // The +1 is we ourselves
            indexInMajority = (matchingNodes + 1) > majoritySize();
            if (!indexInMajority) {
                potentialMajorityIndex -= 1;
            }
        }

        if (potentialMajorityIndex > commitIndex) {
            commitIndex = potentialMajorityIndex;
        }
    }

    /**
     * This is an entry point to receive heartbeats or entries of any type.
     * Call this method in all subclasses
     * @param request
     * @return
     */
    public AppendEntriesResponse receiveEntries(AppendEntriesRequest<T> request) {
        restartElectionTimer();
        currentLeader = otherNodes.get(request.getLeaderId());
        return null;
    }

    /**
     * We need to check if the entries request we receive is valid.
     * @param request The request
     * @return
     */
    protected FailureReason validateAppendRequest(AppendEntriesRequest<T> request) {
        // This is a hard no-go, leaders must have a term >= that the current term of a follower
        if (request.getLeaderTerm() < currentTerm) {
            return FailureReason.LEADER_TERM_OUTDATED;
        }

        // The leader has to send older entries, because the follower lost some of the entries
        if (request.getPreviousIndex() > currentIndex() + 1) {
            return FailureReason.FOLLOWER_MISSING_ENTRIES;
        }

        // TODO: check if the entries are valid

        return null;
    }

    public void startNode() {
        startElectionTimer();
        startHeartbeatTimer();
    }

    private int countVotes() {
        return (int) receivedVotes.values().stream().filter(vote -> vote).count();
    }

    private int majoritySize() {
        return (otherNodes.size() + 1) / 2;
    }

    /**
     * Called when the node didn't receive a heartbeat, so it has to start an election.
     */
    private void electionTimerFired() {
        if (rank != NodeRank.LEADER) {
            startElection();
        }
    }

    private void startElectionTimer() {
        TimerTask electionTask = new TimerTask() {
            public void run() {
                electionTimerFired();
            }
        };
        electionTimer = new Timer();
        electionTimer.scheduleAtFixedRate(electionTask, electionTimeout, electionTimeout);
    }

    private void restartElectionTimer() {
        electionTimer.cancel();
        startElectionTimer();
    }

    protected void startElection() {
        setTerm(currentTerm + 1);
        receivedVotes = new HashMap<>();
        receivedVotes.put(id, true);
        rank = NodeRank.CANDIDATE;
        otherNodes.values().forEach(this::sendVoteRequest);
    }

    protected void becomeFollower(String leaderId) {
        rank = NodeRank.FOLLOWER;
        if (leaderId != null) {
            currentLeader = otherNodes.get(leaderId);
        }
    }

    protected void becomeLeader() {
        rank = NodeRank.LEADER;
        currentLeader = null;
        receivedVotes.clear();
        restartElectionTimer();
        otherNodes.values().forEach(nodeAccessor -> {
            commitIndexes.put(nodeAccessor.getNodeId(), 0L);
            nextIndexes.put(nodeAccessor.getNodeId(), currentIndex());
            sendEntries(nodeAccessor, false);
        });
    }

    private void startHeartbeatTimer() {
        TimerTask heartbeatTask = new TimerTask() {
            public void run() {
                heartbeatTimerFired();
            }
        };
        heartbeatTimer = new Timer();
        heartbeatTimer.scheduleAtFixedRate(heartbeatTask, heartbeatInterval, heartbeatInterval);
    }

    private void heartbeatTimerFired() {
        if (rank == NodeRank.LEADER) {
            otherNodes.values().forEach(nodeAccessor -> {
                sendEntries(nodeAccessor, false);
            });
        }
    }

    public void registerOtherNode(NodeAccessor<T> otherNode) {
        if (!otherNode.getNodeId().equals(getId())) {
            this.otherNodes.put(otherNode.getNodeId(), otherNode);
        }
    }

    /**
     * A current index is needed for many decisions, it's better to have it easily accessible
     * @return the last entry index
     */
    protected long currentIndex() {
        return entries.size() - 1;
    }
    abstract protected long lastEntryTerm();

    public String getId() {
        return id;
    }

    public void setTerm(long newTerm) {
        if (newTerm > currentTerm) {
            votedId = null;
            this.currentTerm = newTerm;
        }
    }
}
