package com.ghatana.appplatform.resilience.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RouteTimeoutConfig}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for per-route timeout configuration with override cap (K18-009)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RouteTimeoutConfig — Unit Tests")
class RouteTimeoutConfigTest {

    @Test
    @DisplayName("resolveTimeout() returns baseline when no override supplied (timeout_perRoute)")
    void resolveTimeout_returnsBaseline_whenNoOverride() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.of(
            "/risk/check", Duration.ofMillis(5), Duration.ofMillis(10));

        assertThat(cfg.resolveTimeout((String) null)).isEqualTo(Duration.ofMillis(5));
        assertThat(cfg.resolveTimeout("")).isEqualTo(Duration.ofMillis(5));
        assertThat(cfg.resolveTimeout("   ")).isEqualTo(Duration.ofMillis(5));
    }

    @Test
    @DisplayName("resolveTimeout() honours override within max (timeout_override_withinMax)")
    void resolveTimeout_honoursOverride_withinMax() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.of(
            "/reporting", Duration.ofSeconds(5), Duration.ofSeconds(30));

        // 20s is within the 30s max
        assertThat(cfg.resolveTimeout("20000")).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("resolveTimeout() caps override at max (timeout_override_exceedsMax_capped)")
    void resolveTimeout_capsOverride_whenExceedsMax() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.of(
            "/reporting", Duration.ofSeconds(5), Duration.ofSeconds(30));

        // 120s exceeds the 30s max → should be capped at 30s
        assertThat(cfg.resolveTimeout("120000")).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("resolveTimeout() caps Duration override at max")
    void resolveTimeout_capsOverride_durationVariant() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.of(
            "/api", Duration.ofSeconds(5), Duration.ofSeconds(30));

        assertThat(cfg.resolveTimeout(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.resolveTimeout(Duration.ofSeconds(15))).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("resolveTimeout() returns baseline for non-numeric override")
    void resolveTimeout_returnsBaseline_forInvalidOverride() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.defaultFor("/api");
        assertThat(cfg.resolveTimeout("not-a-number")).isEqualTo(RouteTimeoutConfig.DEFAULT_BASELINE);
    }

    @Test
    @DisplayName("resolveTimeout() returns baseline for zero/negative override")
    void resolveTimeout_returnsBaseline_forZeroOrNegative() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.defaultFor("/api");
        assertThat(cfg.resolveTimeout("0")).isEqualTo(RouteTimeoutConfig.DEFAULT_BASELINE);
        assertThat(cfg.resolveTimeout("-500")).isEqualTo(RouteTimeoutConfig.DEFAULT_BASELINE);
    }

    @Test
    @DisplayName("defaultFor() produces default baseline and max (timeout_metrics)")
    void defaultFor_producesDefaultValues() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.defaultFor("/health");
        assertThat(cfg.baselineTimeout()).isEqualTo(RouteTimeoutConfig.DEFAULT_BASELINE);
        assertThat(cfg.maxOverride()).isEqualTo(RouteTimeoutConfig.DEFAULT_MAX_OVERRIDE);
        assertThat(cfg.routePattern()).isEqualTo("/health");
    }

    @Test
    @DisplayName("metricTagValue() normalises route pattern for metric labels")
    void metricTagValue_normalisesRoutePattern() {
        RouteTimeoutConfig cfg = RouteTimeoutConfig.defaultFor("/risk/check");
        String tag = cfg.metricTagValue();
        assertThat(tag).doesNotContain("/");
        assertThat(tag).matches("[a-z0-9_]+");
    }

    @Test
    @DisplayName("constructor rejects blank routePattern")
    void constructor_rejectsBlankRoutePattern() {
        assertThatThrownBy(() -> RouteTimeoutConfig.of("   ", Duration.ofSeconds(1), Duration.ofSeconds(5)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("routePattern");
    }

    @Test
    @DisplayName("constructor rejects maxOverride < baselineTimeout")
    void constructor_rejectsMaxSmallerThanBaseline() {
        assertThatThrownBy(() -> RouteTimeoutConfig.of(
            "/api", Duration.ofSeconds(10), Duration.ofSeconds(5)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxOverride");
    }
}
