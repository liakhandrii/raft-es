package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;

import java.util.*;

/**
 * A class prototype destined to represent a basis of what each Raft node has to have to achieve minimum functionality.
 */
public class NodeCore<T> {
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
    protected long heartbeatInterval = 50;

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
    private Long commitIndex = null;

    public List<Entry<T>> entries = new ArrayList<>();

    /**
     * Creates a new NodeCore with an election timeout randomized from 400 to 599 ms
     * Always call this constructor in the subclasses
     */
    public NodeCore() {
        electionTimeout = 250 + (new Random().nextInt(150));
        id = UUID.randomUUID().toString();
        otherNodes = new HashMap<>();
        receivedVotes = new HashMap<>();
    }

    /**
     * This is common node functionality, so it's implemented in the NodeCore.
     * @param request A request object with the sender's info
     * @return A reply with our decision and our info
     */
    public void receiveVoteRequest(VoteRequest request) {
        setTerm(request.getCandidateTerm());

        VoteResponse response;

        if (votedId != null) {
            // We don't vote if we voted on this term already
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else if (request.getCandidateTerm() < currentTerm) {
            // We don't vote for those whose term is lower than ours
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else if (request.getCandidateTerm() == currentTerm && currentIndex() != null && (request.getLastEntryIndex() == null || request.getLastEntryIndex() < currentIndex())) {
            // We also don't vote for those whose term is the same as ours, but the last entry index is lower
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else {
            votedId = request.getCandidateId();
            response =  VoteResponse.voted(currentTerm, id, request.getMessageId());
        }

        NodeAccessor<T> accessor = otherNodes.get(request.getCandidateId());
        accessor.sendVoteResponse(response);
    }

    public void sendVoteRequest(NodeAccessor<T> nodeAccessor) {
        if (rank != NodeRank.CANDIDATE) {
            return;
        }

        VoteRequest request = new VoteRequest(currentTerm, id, currentIndex(), lastEntryTerm(), UUID.randomUUID().toString());
        nodeAccessor.sendVoteRequest(request);
    }

    public void processVoteRequestResponse(VoteResponse response) {
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
        if (rank != NodeRank.LEADER) {
            return;
        }
        AppendEntriesRequest<T> request;

        if (empty) {
            // Just sending a heartbeat
            request = new AppendEntriesRequest<T>(currentTerm, id, null, null, new ArrayList<>(), null, UUID.randomUUID().toString());
        } else {
            Long nextNodeIndex = nextIndexes.get(nodeAccessor.getNodeId());

            if (nextNodeIndex == null) {
                nextNodeIndex = 0L;
            }


            List<Entry<T>> newEntries;
            if (currentIndex() != null && currentIndex() >= nextNodeIndex) {
                newEntries = entries.subList(nextNodeIndex.intValue(), entries.size());
            } else {
                newEntries = new ArrayList<>();
            }

            Long lastNodeIndex = nextNodeIndex - 1;
            if (lastNodeIndex < 0) {
                lastNodeIndex = null;
            }

            Long lastNodeTerm = null;
            if (lastNodeIndex != null) {
                lastNodeTerm = entries.get(lastNodeIndex.intValue()).getTerm();
            }

            request = new AppendEntriesRequest<T>(currentTerm, id, lastNodeIndex, lastNodeTerm, newEntries, commitIndex, UUID.randomUUID().toString());
        }

        nodeAccessor.sendAppendEntriesRequest(request);
    }

    public void processEntriesResponse(AppendEntriesResponse response) {
        if (rank != NodeRank.LEADER) {
            return;
        }

        if (response.isSuccessful()) {
            Long matchingIndex = response.getLastEntryIndex();
            if (matchingIndex != null) {
                commitIndexes.put(response.getResponderId(), matchingIndex);
                nextIndexes.put(response.getResponderId(), matchingIndex + 1);
            }
            calculateCommitIndex();
        }
//        else {
//            if (response.getReason() != null) {
//                if (response.getReason() == FailureReason.LEADER_TERM_OUTDATED) {
//                    becomeFollower(null);
//                } else if (response.getReason() == FailureReason.FOLLOWER_MISSING_ENTRIES) {
//                    lastNodeIndexes.put(nodeAccessor.getNodeId(), response.getLastEntryIndex());
//                }
//            }
//        }
    }

    private void calculateCommitIndex() {
        if (currentIndex() == null) {
            return;
        }
        long potentialMajorityIndex = currentIndex();
        boolean indexInMajority = false;
        while (!indexInMajority && potentialMajorityIndex > 0) {
            final long finalPotentialMajorityIndex = potentialMajorityIndex;
            long matchingNodes = commitIndexes.values().stream().filter(index -> index >= finalPotentialMajorityIndex).count();
            // The +1 is we ourselves
            indexInMajority = (matchingNodes + 1) > majoritySize();
            if (!indexInMajority) {
                potentialMajorityIndex -= 1;
            }
        }

        if (indexInMajority && (commitIndex == null || potentialMajorityIndex > commitIndex)) {
            commitIndex = potentialMajorityIndex;
        }
    }

    /**
     * This is an entry point to receive heartbeats or entries of any type.
     * Call this method in all subclasses
     * @param request
     * @return
     */
    public void receiveEntries(AppendEntriesRequest<T> request) {
        setTerm(request.getLeaderTerm());

        rank = NodeRank.FOLLOWER;
        currentLeader = otherNodes.get(request.getLeaderId());

        restartElectionTimer();

        NodeAccessor<T> accessor = otherNodes.get(request.getLeaderId());

        AppendEntriesResponse response = null;
        if (request.getPreviousIndex() != null && currentIndex() != null) {
            boolean fail = false;
            if (request.getPreviousIndex() > currentIndex()) {
                fail = true;
            } else {
                Entry<T> storedLog = entries.get(request.getPreviousIndex().intValue());
                if (storedLog.getTerm() != request.getPreviousTerm()) {
                    fail = true;
                }
            }

            if (fail) {
                response = AppendEntriesResponse.failed(currentTerm, currentIndex(), id, request.getMessageId(), FailureReason.DATA_INCONSISTENCY);
                accessor.sendAppendEntriesResponse(response);
                return;
            }
        }

        if (request.getEntries().size() > 0) {
            // We have to override the extra entries, obey the leader
            if (request.getPreviousIndex() != null && request.getPreviousIndex() < currentIndex()) {
                commitIndex = request.getCommitIndex();
                entries = entries.subList(0, request.getPreviousIndex().intValue() + 1);
            } else if (request.getPreviousIndex() == null) {
                entries.clear();
            }

            entries.addAll(request.getEntries());

            response = AppendEntriesResponse.succesful(currentTerm, currentIndex(), id, request.getMessageId());
        } else {
            response = AppendEntriesResponse.succesful(currentTerm, currentIndex(), id, request.getMessageId());
        }
        accessor.sendAppendEntriesResponse(response);
    }

    public ClientResponse receiveClientRequest(ClientRequest<T> request) {
        if (rank == NodeRank.LEADER) {
            entries.add(new Entry<>(request.getValue(), currentTerm, currentIndex() == null ? 0 : currentIndex() + 1));
            return new ClientResponse(true, null);
        } else {
            return new ClientResponse(false, currentLeader);
        }
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
        if (electionTimer != null) {
            electionTimer.cancel();
        }
        startElectionTimer();
    }

    protected void startElection() {
        setTerm(currentTerm + 1);
        votedId = id;
        receivedVotes.clear();
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
            nextIndexes.put(nodeAccessor.getNodeId(), commitIndex);
        });

        otherNodes.values().forEach(nodeAccessor -> {
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
    protected Long currentIndex() {
        if (entries.size() == 0) {
            return null;
        }
        return entries.size() - 1L;
    }

    protected Long lastEntryTerm() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(entries.size() - 1).getTerm();
    }

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
