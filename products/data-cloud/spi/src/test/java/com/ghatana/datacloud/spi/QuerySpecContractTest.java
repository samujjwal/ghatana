package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.spi.capability.QueryCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QuerySpec contracts")
class QuerySpecContractTest {

    @Test
    @DisplayName("EntityStore query spec normalizes defaults and bounds")
    void entityStoreQuerySpecNormalizesDefaultsAndBounds() {
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec(
                "orders",
                null,
                null,
                -5,
                0
        );

        assertThat(spec.collection()).isEqualTo("orders");
        assertThat(spec.filters()).isEmpty();
        assertThat(spec.sorts()).isEmpty();
        assertThat(spec.offset()).isZero();
        assertThat(spec.limit()).isEqualTo(EntityStore.QuerySpec.DEFAULT_LIMIT);
    }

    @Test
    @DisplayName("EntityStore query spec rejects runaway limits")
    void entityStoreQuerySpecRejectsRunawayLimits() {
        assertThatThrownBy(() -> new EntityStore.QuerySpec(
                "orders",
                List.of(),
                List.of(),
                0,
                EntityStore.QuerySpec.MAX_LIMIT + 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed value");
    }

    @Test
    @DisplayName("QueryCapability query spec builder keeps plugin-native shape")
    void queryCapabilityQuerySpecBuilderKeepsPluginNativeShape() {
        QueryCapability.QuerySpec spec = QueryCapability.QuerySpec.builder()
                .filter("status", "active")
                .orderBy("createdAt", "priority")
                .projections(List.of("id", "status"))
                .build();

        assertThat(spec.filters()).containsEntry("status", "active");
        assertThat(spec.orderBy()).containsExactly("createdAt", "priority");
        assertThat(spec.ascending()).isTrue();
        assertThat(spec.projections()).containsExactly("id", "status");
    }

    @Test
    @DisplayName("QuerySpec types remain intentionally distinct")
    void querySpecTypesRemainIntentionallyDistinct() {
        EntityStore.QuerySpec entityStoreSpec = EntityStore.QuerySpec.builder()
                .collection("orders")
                .filter(EntityStore.Filter.eq("status", "active"))
                .build();
        QueryCapability.QuerySpec capabilitySpec = QueryCapability.QuerySpec.builder()
                .filters(Map.of("status", "active"))
                .build();

        assertThat(entityStoreSpec.collection()).isEqualTo("orders");
        assertThat(entityStoreSpec.filters()).singleElement()
                .extracting(EntityStore.Filter::field)
                .isEqualTo("status");
        assertThat(capabilitySpec.filters()).containsEntry("status", "active");
        assertThat(capabilitySpec.projections()).isEmpty();
    }
}