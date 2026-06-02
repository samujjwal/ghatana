package com.ghatana.platform.http.server.filter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CorrelationMiddleware}.
 *
 * @doc.type class
 * @doc.purpose Correlation middleware tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CorrelationMiddleware")
class CorrelationMiddlewareTest extends EventloopTestBase {

    @Nested
    @DisplayName("extractOrCreateCorrelationId")
    class ExtractOrCreateCorrelationId {

        @Test
        @DisplayName("returns existing correlation ID from header")
        void returnsExistingCorrelationIdFromHeader() {
            String existingId = "550e8400-e29b-41d4-a716-446655440000";
            HttpRequest request = HttpRequest.get("http://localhost/test")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), existingId)
                .build();

            String result = CorrelationMiddleware.extractOrCreateCorrelationId(request);

            assertThat(result).isEqualTo(existingId);
        }

        @Test
        @DisplayName("generates new correlation ID when header is missing")
        void generatesNewCorrelationIdWhenHeaderIsMissing() {
            HttpRequest request = HttpRequest.get("http://localhost/test").build();

            String result = CorrelationMiddleware.extractOrCreateCorrelationId(request);

            assertThat(result).isNotNull();
            assertThat(CorrelationMiddleware.isValidCorrelationId(result)).isTrue();
        }

        @Test
        @DisplayName("generates new correlation ID when header is blank")
        void generatesNewCorrelationIdWhenHeaderIsBlank() {
            HttpRequest request = HttpRequest.get("http://localhost/test")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "   ")
                .build();

            String result = CorrelationMiddleware.extractOrCreateCorrelationId(request);

            assertThat(result).isNotNull();
            assertThat(CorrelationMiddleware.isValidCorrelationId(result)).isTrue();
        }
    }

    @Nested
    @DisplayName("generateCorrelationId")
    class GenerateCorrelationId {

        @Test
        @DisplayName("generates valid UUID")
        void generatesValidUuid() {
            String correlationId = CorrelationMiddleware.generateCorrelationId();

            assertThat(correlationId).isNotNull();
            assertThat(CorrelationMiddleware.isValidCorrelationId(correlationId)).isTrue();
        }

        @Test
        @DisplayName("generates unique IDs")
        void generatesUniqueIds() {
            String id1 = CorrelationMiddleware.generateCorrelationId();
            String id2 = CorrelationMiddleware.generateCorrelationId();

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("isValidCorrelationId")
    class IsValidCorrelationId {

        @Test
        @DisplayName("returns true for valid UUID")
        void returnsTrueForValidUuid() {
            String validUuid = "550e8400-e29b-41d4-a716-446655440000";

            assertThat(CorrelationMiddleware.isValidCorrelationId(validUuid)).isTrue();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(CorrelationMiddleware.isValidCorrelationId(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for blank string")
        void returnsFalseForBlankString() {
            assertThat(CorrelationMiddleware.isValidCorrelationId("")).isFalse();
            assertThat(CorrelationMiddleware.isValidCorrelationId("   ")).isFalse();
        }

        @Test
        @DisplayName("returns false for invalid UUID format")
        void returnsFalseForInvalidUuidFormat() {
            assertThat(CorrelationMiddleware.isValidCorrelationId("not-a-uuid")).isFalse();
            assertThat(CorrelationMiddleware.isValidCorrelationId("123")).isFalse();
        }
    }

    @Nested
    @DisplayName("apply middleware")
    class ApplyMiddleware {

        @Test
        @DisplayName("propagates correlation ID to response")
        void propagatesCorrelationIdToResponse() throws Exception {
            String correlationId = "550e8400-e29b-41d4-a716-446655440000";
            HttpRequest request = HttpRequest.get("http://localhost/test")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                .build();

            HttpResponse response = runPromise(() ->
                CorrelationMiddleware.apply(request, req -> Promise.of(HttpResponse.ofCode(200).build()))
            );

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("generates and propagates correlation ID when missing")
        void generatesAndPropagatesCorrelationIdWhenMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/test").build();

            HttpResponse response = runPromise(() ->
                CorrelationMiddleware.apply(request, req -> Promise.of(HttpResponse.ofCode(200).build()))
            );

            assertThat(response.getCode()).isEqualTo(200);
            String correlationId = response.getHeader(HttpHeaders.of("X-Correlation-ID"));
            assertThat(correlationId).isNotNull();
            assertThat(CorrelationMiddleware.isValidCorrelationId(correlationId)).isTrue();
        }

        @Test
        @DisplayName("preserves existing correlation ID in response if set by handler")
        void preservesExistingCorrelationIdInResponseIfSetByHandler() throws Exception {
            String originalId = "550e8400-e29b-41d4-a716-446655440000";
            String handlerId = "660e8400-e29b-41d4-a716-446655440001";
            HttpRequest request = HttpRequest.get("http://localhost/test")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), originalId)
                .build();

            HttpResponse response = runPromise(() ->
                CorrelationMiddleware.apply(request, req ->
                    Promise.of(HttpResponse.ofCode(200).withHeader(HttpHeaders.of("X-Correlation-ID"), handlerId).build())
                )
            );

            assertThat(response.getCode()).isEqualTo(200);
            // Handler's correlation ID should be preserved
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo(handlerId);
        }
    }

    @Nested
    @DisplayName("getCorrelationId")
    class GetCorrelationId {

        @Test
        @DisplayName("returns correlation ID from request")
        void returnsCorrelationIdFromRequest() {
            String correlationId = "550e8400-e29b-41d4-a716-446655440000";
            HttpRequest request = HttpRequest.get("http://localhost/test")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                .build();

            assertThat(CorrelationMiddleware.getCorrelationId(request)).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("returns null when header is missing")
        void returnsNullWhenHeaderIsMissing() {
            HttpRequest request = HttpRequest.get("http://localhost/test").build();

            assertThat(CorrelationMiddleware.getCorrelationId(request)).isNull();
        }
    }
}
