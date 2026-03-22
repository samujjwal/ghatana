/*
 * Copyright (c) 2026 Ghatana Inc.
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

    private final ActionClassifier classifier = new ActionClassifier();

    @Nested
    @DisplayName("action classification heuristics")
    class ActionClassification {

        @Test
        @DisplayName("read-like tools should classify as READ")
        void readLikeToolsShouldClassifyAsRead() {
            assertThat(classifier.classifyAction("getUser", "user", Map.of()))
                    .isEqualTo(ActionClass.READ);
            assertThat(classifier.classifyAction("fetchOrders", "order", Map.of()))
                    .isEqualTo(ActionClass.READ);
            assertThat(classifier.classifyAction("searchProducts", "product", Map.of()))
                    .isEqualTo(ActionClass.READ);
            assertThat(classifier.classifyAction("listItems", "item", Map.of()))
                    .isEqualTo(ActionClass.READ);
        }

        @Test
        @DisplayName("external call patterns should classify as CALL_EXTERNAL")
        void externalCallsShouldClassifyCorrectly() {
            assertThat(classifier.classifyAction("httpPost", "endpoint", Map.of()))
                    .isEqualTo(ActionClass.CALL_EXTERNAL);
            assertThat(classifier.classifyAction("sendEmail", "user", Map.of()))
                    .isEqualTo(ActionClass.CALL_EXTERNAL);
            assertThat(classifier.classifyAction("slackNotify", "channel", Map.of()))
                    .isEqualTo(ActionClass.CALL_EXTERNAL);
        }

        @Test
        @DisplayName("irreversible patterns should classify as WRITE_IRREVERSIBLE")
        void irreversiblePatternsShouldClassifyCorrectly() {
            assertThat(classifier.classifyAction("publishEvent", "event", Map.of()))
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE);
            assertThat(classifier.classifyAction("transferFunds", "account", Map.of()))
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE);
            assertThat(classifier.classifyAction("broadcastMessage", "channel", Map.of()))
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE);
        }

        @Test
        @DisplayName("draft/stage patterns should classify as DRAFT")
        void draftPatternsShouldClassifyCorrectly() {
            assertThat(classifier.classifyAction("draftDocument", "document", Map.of()))
                    .isEqualTo(ActionClass.DRAFT);
            assertThat(classifier.classifyAction("stageDeployment", "deploy", Map.of()))
                    .isEqualTo(ActionClass.DRAFT);
            assertThat(classifier.classifyAction("previewReport", "report", Map.of()))
                    .isEqualTo(ActionClass.DRAFT);
        }

        @Test
        @DisplayName("delegation patterns should classify as DELEGATE")
        void delegationPatternsShouldClassifyCorrectly() {
            assertThat(classifier.classifyAction("delegateTask", "task", Map.of()))
                    .isEqualTo(ActionClass.DELEGATE);
            assertThat(classifier.classifyAction("invoke-agent", "agent", Map.of()))
                    .isEqualTo(ActionClass.DELEGATE);
        }

        @Test
        @DisplayName("memory patterns should classify as MEMORY_MUTATION")
        void memoryPatternsShouldClassifyCorrectly() {
            assertThat(classifier.classifyAction("storeMemory", "fact", Map.of()))
                    .isEqualTo(ActionClass.MEMORY_MUTATION);
            assertThat(classifier.classifyAction("rememberContext", "context", Map.of()))
                    .isEqualTo(ActionClass.MEMORY_MUTATION);
        }

        @Test
        @DisplayName("write/create patterns default to WRITE_REVERSIBLE")
        void writePatternsShouldDefaultToReversible() {
            assertThat(classifier.classifyAction("createOrder", "order", Map.of()))
                    .isEqualTo(ActionClass.WRITE_REVERSIBLE);
            assertThat(classifier.classifyAction("updateProfile", "profile", Map.of()))
                    .isEqualTo(ActionClass.WRITE_REVERSIBLE);
        }
    }

    @Nested
    @DisplayName("tool overrides")
    class ToolOverrides {

        @Test
        @DisplayName("explicit overrides should take priority over heuristics")
        void overridesShouldTakePriority() {
            ActionClassifier withOverrides = new ActionClassifier(
                    Map.of("getSecretData", ActionClass.WRITE_IRREVERSIBLE),
                    Map.of());

            // "get" would normally match READ, but override wins
            assertThat(withOverrides.classifyAction("getSecretData", "secret", Map.of()))
                    .isEqualTo(ActionClass.WRITE_IRREVERSIBLE);
        }
    }

    @Nested
    @DisplayName("reversibility classification")
    class ReversibilityClassification {

        @Test
        @DisplayName("READ actions should classify as REVERSIBLE")
        void readShouldBeReversible() {
            assertThat(classifier.classifyReversibility("getUser", ActionClass.READ))
                    .isEqualTo(ReversibilityClass.REVERSIBLE);
        }

        @Test
        @DisplayName("WRITE_IRREVERSIBLE should classify as IRREVERSIBLE")
        void writeIrreversibleShouldBeIrreversible() {
            assertThat(classifier.classifyReversibility("deletePermanent", ActionClass.WRITE_IRREVERSIBLE))
                    .isEqualTo(ReversibilityClass.IRREVERSIBLE);
        }

        @Test
        @DisplayName("WRITE_REVERSIBLE should classify as COMPENSATABLE")
        void writeReversibleShouldBeCompensatable() {
            assertThat(classifier.classifyReversibility("updateOrder", ActionClass.WRITE_REVERSIBLE))
                    .isEqualTo(ReversibilityClass.COMPENSATABLE);
        }

        @Test
        @DisplayName("overrides should take priority")
        void overridesShouldTakePriority() {
            ActionClassifier withOverrides = new ActionClassifier(
                    Map.of(),
                    Map.of("myTool", ReversibilityClass.IRREVERSIBLE));

            assertThat(withOverrides.classifyReversibility("myTool", ActionClass.READ))
                    .isEqualTo(ReversibilityClass.IRREVERSIBLE);
        }
    }
}
