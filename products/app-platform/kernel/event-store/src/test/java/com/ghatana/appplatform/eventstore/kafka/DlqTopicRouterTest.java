package com.ghatana.appplatform.eventstore.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DlqTopicRouter}.
 *
 * <p>Tests the static topic naming logic and header constants. Full integration tests
 * (requiring a Kafka broker) are covered by the event-store integration test suite.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DLQ topic naming and header constants
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DlqTopicRouter — Unit Tests")
class DlqTopicRouterTest {

    @Test
    @DisplayName("dlqTopicFor_eventsTopic — replaces .events suffix with .dlq")
    void dlqTopicForEventsSuffix() {
        assertThat(DlqTopicRouter.dlqTopicFor("siddhanta.order.events"))
                .isEqualTo("siddhanta.order.dlq");
    }

    @Test
    @DisplayName("dlqTopicFor_nonStandardTopic — appends .dlq to any other topic")
    void dlqTopicForNonStandardTopic() {
        assertThat(DlqTopicRouter.dlqTopicFor("some.topic.name"))
                .isEqualTo("some.topic.name.dlq");
    }

    @Test
    @DisplayName("dlqTopicFor_paymentEvents — correctly derives DLQ topic name")
    void dlqTopicForPaymentEvents() {
        assertThat(DlqTopicRouter.dlqTopicFor("siddhanta.payment.events"))
                .isEqualTo("siddhanta.payment.dlq");
    }

    @Test
    @DisplayName("dlqTopicFor_null — throws NullPointerException")
    void dlqTopicForNullThrows() {
        assertThatThrownBy(() -> DlqTopicRouter.dlqTopicFor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("headerConstants — names match the W3C/Kafka convention")
    void headerConstantsMatchSpec() {
        assertThat(DlqTopicRouter.HDR_ORIGINAL_TOPIC).isEqualTo("dlq.original-topic");
        assertThat(DlqTopicRouter.HDR_ERROR_MESSAGE).isEqualTo("dlq.error-message");
        assertThat(DlqTopicRouter.HDR_RETRY_COUNT).isEqualTo("dlq.retry-count");
        assertThat(DlqTopicRouter.HDR_CONSUMER_GROUP).isEqualTo("dlq.consumer-group");
        assertThat(DlqTopicRouter.HDR_FAILED_AT).isEqualTo("dlq.failed-at");
    }

    @Test
    @DisplayName("dlqTopicFor_alreadyDlqTopic — appends another .dlq (idempotency not assumed)")
    void dlqTopicForAlreadyDlqTopic() {
        // Callers should not pass DLQ topics; this verifies deterministic behavior
        assertThat(DlqTopicRouter.dlqTopicFor("siddhanta.order.dlq"))
                .isEqualTo("siddhanta.order.dlq.dlq");
    }
}
