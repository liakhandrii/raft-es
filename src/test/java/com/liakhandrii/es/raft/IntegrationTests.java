package com.liakhandrii.es.raft;

import com.liakhandrii.es.implementation.local.LocalTest;
import com.liakhandrii.es.raft.models.Entry;
import com.liakhandrii.es.raft.models.NodeRole;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * These tests test each property of the algorithm described in the paper.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTests {

    LocalTest<MockNodeAccessor> testObject = null;

    @BeforeAll
    void initialize() {
        testObject = new LocalTest<>();
        testObject.setAccessors(NodeUnitTests.generateNodes(5));
        testObject.start();
    }

    @AfterAll
    void destroy() {
        testObject.accessors.forEach(MockNodeAccessor::killNode);
        testObject.accessors = null;
    }

    long time = System.currentTimeMillis();

    @BeforeEach
    public void pause() {
        while(System.currentTimeMillis() < time) {
            // Thread.sleep causes a bunch of problems, this is a safer solution
        }
        time += 500;
    }

    @RepeatedTest(50)
    void electionSafety() {
        // Simple, there can only be one leader in the cluster
        Set<String> leaderIds = new HashSet<>();
        for (MockNodeAccessor accessor: testObject.accessors) {
            // Dead nodes can think they are leader if they got disconnected in that state, we skip them
            if (accessor.isNodeDown) {
                continue;
            }
            if (accessor.node.currentLeader != null) {
                leaderIds.add(accessor.node.currentLeader.nodeId);
            } else if (accessor.node.role == NodeRole.LEADER) {
                leaderIds.add(accessor.nodeId);
            }
        }

        assertEquals(1, leaderIds.size());
    }

    private int lastLeaderEntriesCount = 0;

    @RepeatedTest(50)
    void leaderAppendOnly() {
        MockNodeAccessor leader = null;
        for (MockNodeAccessor accessor: testObject.accessors) {
            if (accessor.isNodeDown) {
                continue;
            }
            if (accessor.node.currentLeader != null) {
                leader = (MockNodeAccessor) accessor.node.currentLeader;
                break;
            } else if (accessor.node.role == NodeRole.LEADER) {
                leader = accessor;
                break;
            }
        }

        if (leader != null) {
            assertTrue(leader.node.entries.size() >= lastLeaderEntriesCount);
            lastLeaderEntriesCount = leader.node.entries.size();
        }
    }

    @RepeatedTest(50)
    void testLogMatching() {
        // Checks if all the data on the non-dead nodes is the same
        // Can pose a problem if it lands right on the client request though
        MockNodeAccessor accessor1 = null;
        MockNodeAccessor accessor2 = null;
        for (MockNodeAccessor accessor: testObject.accessors) {
            if (accessor.isNodeDown) {
                continue;
            }
            if (accessor1 == null) {
                accessor1 = accessor;
            } else if (accessor2 == null) {
                accessor2 = accessor;
            }
        }

        int matchingIndex = Math.min(accessor1.node.entries.size(), accessor2.node.entries.size()) - 1;
        while (matchingIndex >= 0) {
            Entry<String> entry1 = accessor1.node.entries.get(matchingIndex);
            Entry<String> entry2 = accessor2.node.entries.get(matchingIndex);

            if (entry1.equals(entry2)) {
                break;
            } else {
                matchingIndex -= 1;
            }
        }

        if (matchingIndex > 0) {
            // Just checking the last 20 entries, so it doesn't take forever
            for (int i = Math.max(0, matchingIndex - 20); i < matchingIndex; i += 1) {
                Entry<String> entry1 = accessor1.node.entries.get(i);
                Entry<String> entry2 = accessor2.node.entries.get(i);

                assertEquals(entry1, entry2);
            }
        }
    }

    private Entry<String> commitedEntry = null;

    @RepeatedTest(50)
    void testLeaderCompleteness() {
        MockNodeAccessor leader = null;
        for (MockNodeAccessor accessor: testObject.accessors) {
            if (accessor.isNodeDown) {
                continue;
            }
            if (accessor.node.currentLeader != null) {
                leader = (MockNodeAccessor) accessor.node.currentLeader;
                break;
            } else if (accessor.node.role == NodeRole.LEADER) {
                leader = accessor;
                break;
            }
        }

        if (commitedEntry == null && leader != null && leader.node.commitIndex != null) {
            commitedEntry = leader.node.entries.get(leader.node.commitIndex.intValue());
        } else if (commitedEntry != null) {
            assertEquals(commitedEntry, leader.node.entries.get((int) commitedEntry.getIndex()));
        }
    }

    @RepeatedTest(50)
    void testStateMachineSafety() {
        MockNodeAccessor leader = null;
        for (MockNodeAccessor accessor: testObject.accessors) {
            if (accessor.isNodeDown) {
                continue;
            }
            if (accessor.node.currentLeader != null) {
                leader = (MockNodeAccessor) accessor.node.currentLeader;
                break;
            } else if (accessor.node.role == NodeRole.LEADER) {
                leader = accessor;
                break;
            }
        }

        Long commitIndex = leader.node.commitIndex;

        if (commitIndex == null) {
            return;
        }

        Random random = new Random();

        for (MockNodeAccessor accessor: testObject.accessors) {
            if (accessor.isNodeDown || accessor == leader) {
                continue;
            }

            int randomIndex = random.nextInt((int) (commitIndex + 1));
            Entry<String> nodeEntry = accessor.node.entries.get(randomIndex);
            Entry<String> serverEntry = accessor.node.entries.get(randomIndex);

            assertEquals(nodeEntry, serverEntry);
        }

    }

}
