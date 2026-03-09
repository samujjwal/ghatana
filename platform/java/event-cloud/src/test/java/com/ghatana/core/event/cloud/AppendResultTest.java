package com.ghatana.core.event.cloud;

import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AppendResult} value object.
 */
@DisplayName("AppendResult")
class AppendResultTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        void shouldCreateValidResult() {
            Instant now = Instant.now();
            AppendResult result = new AppendResult(PartitionId.of(3), Offset.of(42), now);
            assertThat(result.partitionId()).isEqualTo(PartitionId.of(3));
            assertThat(result.offset()).isEqualTo(Offset.of(42));
            assertThat(result.appendTime()).isEqualTo(now);
        }

        @Test
        void shouldRejectNullPartitionId() {
            assertThatThrownBy(() -> new AppendResult(null, Offset.of(0), Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("partitionId");
        }

        @Test
        void shouldRejectNullOffset() {
            assertThatThrownBy(() -> new AppendResult(PartitionId.of(0), null, Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("offset");
        }

        @Test
        void shouldRejectNullAppendTime() {
            assertThatThrownBy(() -> new AppendResult(PartitionId.of(0), Offset.of(0), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("appendTime");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        void shouldBeEqualForSameValues() {
            Instant now = Instant.now();
            AppendResult a = new AppendResult(PartitionId.of(1), Offset.of(10), now);
            AppendResult b = new AppendResult(PartitionId.of(1), Offset.of(10), now);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentPartition() {
            Instant now = Instant.now();
            AppendResult a = new AppendResult(PartitionId.of(1), Offset.of(10), now);
            AppendResult b = new AppendResult(PartitionId.of(2), Offset.of(10), now);
            assertThat(a).isNotEqualTo(b);
        }
    }
}
