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
    void entityStoreQuerySpecNormalizesDefaultsAndBounds() { // GH-90000
        EntityStore.QuerySpec spec = EntityStore.QuerySpec.builder() // GH-90000
                .collection("orders")
                .offset(-5)
                .limit(0)
                .build();

        assertThat(spec.collection()).isEqualTo("orders");
        assertThat(spec.filters()).isEmpty(); // GH-90000
        assertThat(spec.sorts()).isEmpty(); // GH-90000
        assertThat(spec.offset()).isZero(); // GH-90000
        assertThat(spec.limit()).isEqualTo(EntityStore.QuerySpec.DEFAULT_LIMIT); // GH-90000
    }

    @Test
    @DisplayName("EntityStore query spec rejects runaway limits")
    void entityStoreQuerySpecRejectsRunawayLimits() { // GH-90000
        assertThatThrownBy(() -> EntityStore.QuerySpec.builder() // GH-90000
                .collection("orders")
                .filters(List.of())
                .sorts(List.of())
                .offset(0)
                .limit(EntityStore.QuerySpec.MAX_LIMIT + 1)
                .build()
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("exceeds maximum allowed value");
    }

    @Test
    @DisplayName("QueryCapability query spec builder keeps plugin-native shape")
    void queryCapabilityQuerySpecBuilderKeepsPluginNativeShape() { // GH-90000
        QueryCapability.QuerySpec spec = QueryCapability.QuerySpec.builder() // GH-90000
                .filter("status", "active") // GH-90000
                .orderBy("createdAt", "priority") // GH-90000
                .projections(List.of("id", "status")) // GH-90000
                .build(); // GH-90000

        assertThat(spec.filters()).containsEntry("status", "active"); // GH-90000
        assertThat(spec.orderBy()).containsExactly("createdAt", "priority"); // GH-90000
        assertThat(spec.ascending()).isTrue(); // GH-90000
        assertThat(spec.projections()).containsExactly("id", "status"); // GH-90000
    }

    @Test
    @DisplayName("QuerySpec types remain intentionally distinct")
    void querySpecTypesRemainIntentionallyDistinct() { // GH-90000
        EntityStore.QuerySpec entityStoreSpec = EntityStore.QuerySpec.builder() // GH-90000
                .collection("orders")
                .filter(EntityStore.Filter.eq("status", "active")) // GH-90000
                .build(); // GH-90000
        QueryCapability.QuerySpec capabilitySpec = QueryCapability.QuerySpec.builder() // GH-90000
                .filters(Map.of("status", "active")) // GH-90000
                .build(); // GH-90000

        assertThat(entityStoreSpec.collection()).isEqualTo("orders");
        assertThat(entityStoreSpec.filters()).singleElement() // GH-90000
                .extracting(EntityStore.Filter::field) // GH-90000
                .isEqualTo("status");
        assertThat(capabilitySpec.filters()).containsEntry("status", "active"); // GH-90000
        assertThat(capabilitySpec.projections()).isEmpty(); // GH-90000
    }
}
