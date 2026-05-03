package com.ghatana.digitalmarketing.application.campaign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CampaignComplianceViolationException}.
 */
@DisplayName("CampaignComplianceViolationException")
class CampaignComplianceViolationExceptionTest {

    @Test
    @DisplayName("constructor creates exception with message")
    void constructor_createsExceptionWithMessage() {
        String message = "Campaign violates compliance rule: budget exceeded";
        CampaignComplianceViolationException exception = new CampaignComplianceViolationException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("exception is runtime exception")
    void exception_isRuntimeException() {
        CampaignComplianceViolationException exception = new CampaignComplianceViolationException("test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("exception can be thrown and caught")
    void exception_canBeThrownAndCaught() {
        assertThatThrownBy(() -> {
            throw new CampaignComplianceViolationException("Compliance violation");
        }).isInstanceOf(CampaignComplianceViolationException.class)
          .hasMessage("Compliance violation");
    }
}
