package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.models.ClientRequest;
import com.liakhandrii.es.implementation.local.models.ClientResponse;
import com.liakhandrii.es.raft.models.*;

import java.util.*;
import java.util.concurrent.*;

public class NodeCore<T> {

    protected String id;
    protected NodeRole role = NodeRole.FOLLOWER;
    protected long currentTerm = 0;
    /**
     * Value in milliseconds, should be randomized
     */
    protected long electionTimeout;
    /**
     * Value in milliseconds
     */
    protected long heartbeatInterval;

    /**
     * Keeps references to other nodes in the cluster
     */
    protected Map<String, NodeAccessor<T>> otherNodes;
    protected NodeAccessor<T> currentLeader;

    /**
     * Used to prevent the node from giving multiple votes
     */
    protected String votedId = null;
    protected Map<String, Boolean> receivedVotes;

    protected List<Entry<T>> entries = new ArrayList<>();
    protected Long commitIndex = null;
    protected final Map<String, Long> commitIndexes = new HashMap<>();
    protected final Map<String, Long> nextIndexes = new HashMap<>();

    ScheduledExecutorService executorService;

    protected ScheduledFuture<?> electionTask;
    protected ScheduledFuture<?> heartbeatTask;

    /**
     * Creates a new NodeCore with an election timeout randomized from 250 to 400 ms
     * Always call this constructor in the subclasses, unless you do the initialization yourself.
     */
    public NodeCore(int heartbeatInterval, int electionTimeout) {
        this.heartbeatInterval = heartbeatInterval;
        this.electionTimeout = electionTimeout + (new Random().nextInt(electionTimeout / 2));
        id = UUID.randomUUID().toString();
        otherNodes = new HashMap<>();
        receivedVotes = new HashMap<>();
        executorService = Executors.newScheduledThreadPool(2);
    }

    public NodeCore(int heartbeatInterval, int electionTimeout, String id) {
        this(heartbeatInterval, electionTimeout);
        this.id = id;
    }

    /**
     * The node doesn't start its operation immediately after running the constructor. Use this method when you're ready to start using the node.
     */
    public void startNode() {
        // I'm using timers intentionally to make the thing asynchronous, which is much closer to the real world application
        startElectionTimer();
        startHeartbeatTimer();
    }

    public void stopNode() {
        if (electionTask != null) {
            electionTask.cancel(false);
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    public void registerOtherNode(NodeAccessor<T> otherNode) {
        if (!otherNode.getNodeId().equals(getId())) {
            this.otherNodes.put(otherNode.getNodeId(), otherNode);
        }
    }

    public void sendVoteRequest(NodeAccessor<T> nodeAccessor) {
        if (role != NodeRole.CANDIDATE) {
            return;
        }

        VoteRequest request = new VoteRequest(currentTerm, id, getCurrentIndex(), getLastEntryTerm(), UUID.randomUUID().toString());
        VoteResponse response = nodeAccessor.sendVoteRequest(request);
        processVoteResponse(response);
    }

    public void processVoteResponse(VoteResponse response) {
        if (response == null) {
            return;
        }

        if (response.getResponderTerm() > currentTerm) {
            becomeFollower(response.getResponderId());
        } else {
            receivedVotes.put(response.getResponderId(), response.getDidReceiveVote());
            if (countVotes() > majoritySize()) {
                becomeLeader();
            }
        }
    }

    public VoteResponse receiveVoteRequest(VoteRequest request) {
        setTerm(request.getCandidateTerm());

        VoteResponse response;

        if (votedId != null) {
            // We don't vote if we voted on this term already
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else if (request.getCandidateTerm() < currentTerm) {
            // We don't vote for those whose term is lower than ours
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else if (request.getCandidateTerm() == currentTerm && getCurrentIndex() != null && (request.getLastEntryIndex() == null || request.getLastEntryIndex() < getCurrentIndex())) {
            // We also don't vote for those whose term is the same as ours, but the last entry index is lower
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else if (request.getCandidateTerm() == currentTerm && getLastEntryTerm() != null && (request.getLastEntryTerm() == null || request.getLastEntryTerm() < getLastEntryTerm())) {
            // This means the node starting the election might have been stuck without a connection to a previous leader, increasing it's term but not actually storing any entries
            response = VoteResponse.rejected(currentTerm, id, request.getMessageId());
        } else {
            votedId = request.getCandidateId();
            response =  VoteResponse.voted(currentTerm, id, request.getMessageId());
        }

        return response;
    }

    /**
     * This method either sends an empty heartbeat or sends the required new entries to the specified follower
     * @param nodeAccessor follower accessor
     * @param empty if the request should just be an empty heartbeat
     */
    public void sendEntries(NodeAccessor<T> nodeAccessor, boolean empty) {
        if (role != NodeRole.LEADER) {
            return;
        }
        AppendEntriesRequest<T> request;

        if (empty) {
            // Just sending a heartbeat
            request = new AppendEntriesRequest<>(currentTerm, id, null, null, new ArrayList<>(), null, UUID.randomUUID().toString());
        } else {
            Long nextNodeIndex = nextIndexes.get(nodeAccessor.getNodeId());

            if (nextNodeIndex == null) {
                nextNodeIndex = 0L;
            }

            List<Entry<T>> newEntries;
            if (getCurrentIndex() != null && getCurrentIndex() >= nextNodeIndex) {
                newEntries = new ArrayList<>(entries.subList(nextNodeIndex.intValue(), entries.size()));
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

            request = new AppendEntriesRequest<>(currentTerm, id, lastNodeIndex, lastNodeTerm, newEntries, commitIndex, UUID.randomUUID().toString());
        }

        AppendEntriesResponse response = nodeAccessor.sendAppendEntriesRequest(request);
        processEntriesResponse(response);
    }

    public void processEntriesResponse(AppendEntriesResponse response) {
        if (response == null || role != NodeRole.LEADER) {
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
    }

    /**
     * Become follower, receive new entries, analyze their validity, store.
     */
    public AppendEntriesResponse receiveEntries(AppendEntriesRequest<T> request) {
        if (setTerm(request.getLeaderTerm()) || request.getLeaderTerm() == currentTerm) {
            becomeFollower(request.getLeaderId());
        }

        restartElectionTimer();

        NodeAccessor<T> accessor = otherNodes.get(request.getLeaderId());

        AppendEntriesResponse response;
        if (request.getPreviousIndex() != null && getCurrentIndex() != null) {
            boolean fail = false;
            if (request.getPreviousIndex() > getCurrentIndex()) {
                fail = true;
            } else {
                Entry<T> storedLog = entries.get(request.getPreviousIndex().intValue());
                if (storedLog.getTerm() != request.getPreviousTerm()) {
                    fail = true;
                }
            }

            if (fail) {
                response = AppendEntriesResponse.failed(currentTerm, getCurrentIndex(), id, request.getMessageId(), FailureReason.DATA_INCONSISTENCY);
                return response;
            }
        }

        if (request.getEntries().size() > 0) {
            // We have to override the extra entries, obey the leader
            if (request.getPreviousIndex() != null && request.getPreviousIndex() < getCurrentIndex()) {
                entries = new ArrayList<>(entries.subList(0, request.getPreviousIndex().intValue() + 1));
            } else if (request.getPreviousIndex() == null) {
                entries.clear();
            }

            entries.addAll(request.getEntries());
            if (request.getCommitIndex() != null) {
                // We can't just save the commit index from the server, it may not be true for this node
                commitIndex = Math.min(request.getCommitIndex(), getCurrentIndex());
            }

        }

        response = AppendEntriesResponse.succesful(currentTerm, getCurrentIndex(), id, request.getMessageId());
        return response;
    }

    public ClientResponse receiveClientRequest(ClientRequest<T> request) {
        if (role == NodeRole.LEADER) {
            entries.add(new Entry<>(request.getValue(), currentTerm, getCurrentIndex() == null ? 0 : getCurrentIndex() + 1));
            return new ClientResponse(true, null);
        } else {
            return new ClientResponse(false, currentLeader);
        }
    }

    protected void startElection() {
        setTerm(currentTerm + 1);
        votedId = id;
        receivedVotes.clear();
        receivedVotes.put(id, true);
        role = NodeRole.CANDIDATE;
        otherNodes.values().forEach(this::sendVoteRequest);
    }

    protected void becomeFollower(String leaderId) {
        role = NodeRole.FOLLOWER;
        if (leaderId != null) {
            currentLeader = otherNodes.get(leaderId);
        }
    }

    protected void becomeLeader() {
        role = NodeRole.LEADER;
        currentLeader = null;
        receivedVotes.clear();
        restartElectionTimer();

        otherNodes.values().forEach(nodeAccessor -> {
            commitIndexes.put(nodeAccessor.getNodeId(), 0L);
            if (commitIndex != null) {
                nextIndexes.put(nodeAccessor.getNodeId(), commitIndex);
            }
        });

        otherNodes.values().forEach(nodeAccessor -> sendEntries(nodeAccessor, false));
    }

    /**
     * Called when the node didn't receive a heartbeat, so it has to start an election.
     */
    private void electionTimerFired() {
        if (role != NodeRole.LEADER) {
            startElection();
        }
    }

    private void startElectionTimer() {
        if (electionTask != null && !electionTask.isCancelled()) {
            electionTask.cancel(false);
        }
        Runnable electionRunnable = () -> {
            try {
                electionTimerFired();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        electionTask = executorService.scheduleWithFixedDelay(electionRunnable, electionTimeout, electionTimeout, TimeUnit.MILLISECONDS);
    }

    private void restartElectionTimer() {
        startElectionTimer();
    }

    private void startHeartbeatTimer() {
        if (heartbeatTask != null && !electionTask.isCancelled()) {
            heartbeatTask.cancel(false);
        }
        Runnable heartbeatRunnable = () -> {
        try {
            heartbeatTimerFired();
        } catch (Exception e) {
            e.printStackTrace();
        }
        };
        heartbeatTask = executorService.scheduleWithFixedDelay(heartbeatRunnable, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private void heartbeatTimerFired() {
        if (role == NodeRole.LEADER) {
            otherNodes.values().forEach(nodeAccessor -> sendEntries(nodeAccessor, false));
        }
    }

    private void calculateCommitIndex() {
        if (getCurrentIndex() == null) {
            return;
        }
        long potentialMajorityIndex = getCurrentIndex();
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

    private int countVotes() {
        return (int) receivedVotes.values().stream().filter(vote -> vote).count();
    }

    private int majoritySize() {
        return (otherNodes.size() + 1) / 2;
    }

    /**
     * I went for a null value for an empty state, because it allows to catch problems much easier than something like -1
     * @return the last entry index
     */
    protected Long getCurrentIndex() {
        if (entries.size() == 0) {
            return null;
        }
        return entries.size() - 1L;
    }

    protected Long getLastEntryTerm() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(entries.size() - 1).getTerm();
    }

    public String getId() {
        return id;
    }

    public boolean setTerm(long newTerm) {
        if (newTerm > currentTerm) {
            votedId = null;
            this.currentTerm = newTerm;
            return true;
        }
        return false;
    }

    public List<Entry<T>> getEntries() {
        return entries;
    }

    public NodeRole getRole() {
        return role;
    }

    public NodeAccessor<T> getCurrentLeader() {
        return currentLeader;
    }
}
