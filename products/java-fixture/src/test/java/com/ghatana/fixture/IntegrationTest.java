package com.ghatana.fixture;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Java fixture product.
 *
 * <p>Validates that the Java fixture service processes requests correctly
 * and that the Java adapter can successfully run tests on this product.</p>
 */
class IntegrationTest {

    @Test
    void shouldProcessFixtureRequest() {
        FixtureService service = new FixtureService();
        FixtureRequest request = new FixtureRequest("test-id", "test-payload");
        
        FixtureResponse response = service.process(request);
        
        assertThat(response.id()).isEqualTo("test-id");
        assertThat(response.status()).isEqualTo("processed");
        assertThat(response.message()).contains("successfully");
    }

    @Test
    void shouldRejectNullRequest() {
        FixtureService service = new FixtureService();
        
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> service.process(null)
        );
    }

    @Test
    void shouldProcessRequestWithEmptyPayload() {
        FixtureService service = new FixtureService();
        FixtureRequest request = new FixtureRequest("test-id", "");
        
        FixtureResponse response = service.process(request);
        
        assertThat(response.id()).isEqualTo("test-id");
        assertThat(response.status()).isEqualTo("processed");
    }
}
