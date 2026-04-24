package com.ghatana.datacloud.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Property-based query validator checks for limits and join depth constraints
 * @doc.layer product
 * @doc.pattern Property Test
 */
@DisplayName("QueryValidator Property-Based Tests")
class QueryValidatorPropertyBasedTest extends EventloopTestBase {

    private final QueryValidator validator = new QueryValidator();

    @Test
    @DisplayName("random LIMIT values respect max bound invariant")
    void randomLimitValuesRespectInvariant() {
        Random random = new Random(42);

        for (int i = 0; i < 300; i++) {
            int limit = 1 + random.nextInt(20_000);
            String query = "SELECT * FROM products LIMIT " + limit;

            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of());

            if (limit <= 10_000) {
                assertThat(result.valid()).as("limit=%s should be valid", limit).isTrue();
            } else {
                assertThat(result.valid()).as("limit=%s should be invalid", limit).isFalse();
                assertThat(result.violations()).anyMatch(v -> v.contains("LIMIT") && v.contains("maximum"));
            }
        }
    }

    @Test
    @DisplayName("random JOIN depth values respect max depth invariant")
    void randomJoinDepthValuesRespectInvariant() {
        Random random = new Random(99);

        for (int i = 0; i < 120; i++) {
            int joinDepth = random.nextInt(8);
            String query = buildJoinQuery(joinDepth);

            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of());

            if (joinDepth >= 5) {
                assertThat(result.valid()).as("joinDepth=%s should be invalid", joinDepth).isFalse();
                assertThat(result.violations()).anyMatch(v -> v.contains("JOIN depth"));
            } else {
                assertThat(result.valid()).as("joinDepth=%s should be valid", joinDepth).isTrue();
            }
        }
    }

    @Test
    @DisplayName("fuzzed SQL fragments never crash validator")
    void fuzzedSqlNeverCrashesValidator() {
        Random random = new Random(7);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-;()'";

        for (int i = 0; i < 200; i++) {
            int len = 5 + random.nextInt(80);
            StringBuilder sb = new StringBuilder(len);
            for (int c = 0; c < len; c++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }

            String query = sb.toString();
            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of());

            assertThat(result).isNotNull();
            assertThat(result.violations()).isNotNull();
        }
    }

    private static String buildJoinQuery(int joinDepth) {
        StringBuilder query = new StringBuilder("SELECT * FROM t1");
        for (int i = 0; i < joinDepth; i++) {
            int right = i + 2;
            int left = i + 1;
            query.append(" JOIN t").append(right)
                .append(" ON t").append(left).append(".id = t").append(right).append(".id");
        }
        return query.toString();
    }
}
