package com.ghatana.platform.messaging.strategy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QueueProducerStrategy failure semantics")
class QueueProducerFailureSemanticsTest extends EventloopTestBase {

    @Test
    @DisplayName("sendWithResult() returns success result with message id when send succeeds")
    void shouldReturnSuccessResult() {
        QueueProducerStrategy producer = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                return true;
            }
        };

        QueueMessage message = new QueueMessage("k1", "payload", Map.of());
        ConnectorSendResult result = producer.sendWithResult(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.messageId()).isEqualTo("k1");
        assertThat(result.failure()).isNull();
    }

    @Test
    @DisplayName("sendWithResult() returns retryable failure when send returns false")
    void shouldClassifyBooleanFailureAsRetryable() {
        QueueProducerStrategy producer = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                return false;
            }
        };

        ConnectorSendResult result = producer.sendWithResult(new QueueMessage("k1", "payload", Map.of()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.messageId()).isNull();
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().classification()).isEqualTo(ConnectorFailureClassification.RETRYABLE);
    }

    @Test
    @DisplayName("sendWithResult() classifies IllegalArgumentException as non-retryable")
    void shouldClassifyIllegalArgumentAsNonRetryable() {
        QueueProducerStrategy producer = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                throw new IllegalArgumentException("invalid payload");
            }
        };

        ConnectorSendResult result = producer.sendWithResult(new QueueMessage("k1", "payload", Map.of()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().classification()).isEqualTo(ConnectorFailureClassification.NON_RETRYABLE);
    }

    @Test
    @DisplayName("send(key,payload) fails with ConnectorSendException instead of null")
    void shouldFailWithTypedExceptionInsteadOfNull() {
        QueueProducerStrategy producer = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                return false;
            }
        };

        assertThatThrownBy(() -> runPromise(() -> producer.send("k1", "payload")))
            .isInstanceOf(ConnectorSendException.class)
            .satisfies(error -> {
                ConnectorSendException ex = (ConnectorSendException) error;
                assertThat(ex.failure().classification()).isEqualTo(ConnectorFailureClassification.RETRYABLE);
            });
    }

    @Test
    @DisplayName("send(key,payload) returns key on success")
    void shouldReturnKeyOnSuccess() {
        QueueProducerStrategy producer = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                return true;
            }
        };

        String result = runPromise(() -> producer.send("k1", "payload"));
        assertThat(result).isEqualTo("k1");
    }

    private abstract static class StubProducer implements QueueProducerStrategy {
        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }
}
