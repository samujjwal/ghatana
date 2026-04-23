/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.framework.governance.ReversibilityClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP3: ActionClassifier pattern-based tool classification.
 */
@DisplayName("ActionClassifier (WP3)")
class ActionClassifierTest {

    private final ActionClassifier classifier = new ActionClassifier(); // GH-90000

    @Nested
    @DisplayName("action classification heuristics")
    class ActionClassification {

        @Test
        @DisplayName("read-like tools should classify as READ")
        void readLikeToolsShouldClassifyAsRead() { // GH-90000
            assertThat(classifier.classifyAction("getUser", "user", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.READ); // GH-90000
            assertThat(classifier.classifyAction("fetchOrders", "order", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.READ); // GH-90000
            assertThat(classifier.classifyAction("searchProducts", "product", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.READ); // GH-90000
            assertThat(classifier.classifyAction("listItems", "item", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.READ); // GH-90000
        }

        @Test
        @DisplayName("external call patterns should classify as CALL_EXTERNAL")
        void externalCallsShouldClassifyCorrectly() { // GH-90000
            assertThat(classifier.classifyAction("httpPost", "endpoint", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.CALL_EXTERNAL); // GH-90000
            assertThat(classifier.classifyAction("sendEmail", "user", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.CALL_EXTERNAL); // GH-90000
            assertThat(classifier.classifyAction("slackNotify", "channel", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.CALL_EXTERNAL); // GH-90000
        }

        @Test
        @DisplayName("irreversible patterns should classify as WRITE_IRREVERSIBLE")
        void irreversiblePatternsShouldClassifyCorrectly() { // GH-90000
            assertThat(classifier.classifyAction("publishEvent", "event", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            assertThat(classifier.classifyAction("transferFunds", "account", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            assertThat(classifier.classifyAction("broadcastMessage", "channel", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE); // GH-90000
        }

        @Test
        @DisplayName("draft/stage patterns should classify as DRAFT")
        void draftPatternsShouldClassifyCorrectly() { // GH-90000
            assertThat(classifier.classifyAction("draftDocument", "document", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.DRAFT); // GH-90000
            assertThat(classifier.classifyAction("stageDeployment", "deploy", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.DRAFT); // GH-90000
            assertThat(classifier.classifyAction("previewReport", "report", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("delegation patterns should classify as DELEGATE")
        void delegationPatternsShouldClassifyCorrectly() { // GH-90000
            assertThat(classifier.classifyAction("delegateTask", "task", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.DELEGATE); // GH-90000
            assertThat(classifier.classifyAction("invoke-agent", "agent", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.DELEGATE); // GH-90000
        }

        @Test
        @DisplayName("memory patterns should classify as MEMORY_MUTATION")
        void memoryPatternsShouldClassifyCorrectly() { // GH-90000
            assertThat(classifier.classifyAction("storeMemory", "fact", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.MEMORY_MUTATION); // GH-90000
            assertThat(classifier.classifyAction("rememberContext", "context", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.MEMORY_MUTATION); // GH-90000
        }

        @Test
        @DisplayName("write/create patterns default to WRITE_REVERSIBLE")
        void writePatternsShouldDefaultToReversible() { // GH-90000
            assertThat(classifier.classifyAction("createOrder", "order", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_REVERSIBLE); // GH-90000
            assertThat(classifier.classifyAction("updateProfile", "profile", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_REVERSIBLE); // GH-90000
        }
    }

    @Nested
    @DisplayName("tool overrides")
    class ToolOverrides {

        @Test
        @DisplayName("explicit overrides should take priority over heuristics")
        void overridesShouldTakePriority() { // GH-90000
            ActionClassifier withOverrides = new ActionClassifier( // GH-90000
                    Map.of("getSecretData", ActionClass.WRITE_IRREVERSIBLE), // GH-90000
                    Map.of()); // GH-90000

            // "get" would normally match READ, but override wins
            assertThat(withOverrides.classifyAction("getSecretData", "secret", Map.of())) // GH-90000
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE); // GH-90000
        }
    }

    @Nested
    @DisplayName("reversibility classification")
    class ReversibilityClassification {

        @Test
        @DisplayName("READ actions should classify as REVERSIBLE")
        void readShouldBeReversible() { // GH-90000
            assertThat(classifier.classifyReversibility("getUser", ActionClass.READ)) // GH-90000
                    .isEqualTo(ReversibilityClass.REVERSIBLE); // GH-90000
        }

        @Test
        @DisplayName("WRITE_IRREVERSIBLE should classify as IRREVERSIBLE")
        void writeIrreversibleShouldBeIrreversible() { // GH-90000
            assertThat(classifier.classifyReversibility("deletePermanent", ActionClass.WRITE_IRREVERSIBLE)) // GH-90000
                    .isEqualTo(ReversibilityClass.IRREVERSIBLE); // GH-90000
        }

        @Test
        @DisplayName("WRITE_REVERSIBLE should classify as COMPENSATABLE")
        void writeReversibleShouldBeCompensatable() { // GH-90000
            assertThat(classifier.classifyReversibility("updateOrder", ActionClass.WRITE_REVERSIBLE)) // GH-90000
                    .isEqualTo(ReversibilityClass.COMPENSATABLE); // GH-90000
        }

        @Test
        @DisplayName("overrides should take priority")
        void overridesShouldTakePriority() { // GH-90000
            ActionClassifier withOverrides = new ActionClassifier( // GH-90000
                    Map.of(), // GH-90000
                    Map.of("myTool", ReversibilityClass.IRREVERSIBLE)); // GH-90000

            assertThat(withOverrides.classifyReversibility("myTool", ActionClass.READ)) // GH-90000
                    .isEqualTo(ReversibilityClass.IRREVERSIBLE); // GH-90000
        }
    }
}
