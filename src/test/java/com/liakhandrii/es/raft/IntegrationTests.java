package com.liakhandrii.es.raft;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTests {

    List<MockNodeAccessor> accessors = null;

    @BeforeAll
    void initialize() {
        LocalTest.main(new String[]{});
        accessors = LocalTest.accessors;
    }

    @Test
    void testLeaderConsistency() {
        
    }

}
