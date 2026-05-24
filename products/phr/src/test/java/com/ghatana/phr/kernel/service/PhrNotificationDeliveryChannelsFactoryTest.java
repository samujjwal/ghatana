package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests PHR notification delivery channel factory fallbacks when no concrete providers are configured
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrNotificationDeliveryChannelsFactory")
class PhrNotificationDeliveryChannelsFactoryTest extends EventloopTestBase {

    @Test
    @DisplayName("fails closed when provider endpoints are absent")
    void failsClosedWhenEndpointsMissing() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        assertThatThrownBy(() -> PhrNotificationDeliveryChannelsFactory.fromContext(
            PhrTestInfrastructure.createTestContext(dataCloud)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PHR notification provider endpoints are required");
    }
}
