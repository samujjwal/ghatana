package com.ghatana.datacloud.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for expanded PII detection patterns.
 *
 * @doc.type class
 * @doc.purpose Tests for expanded PII detection with international formats and edge cases
 * @doc.layer security
 */
@DisplayName("Expanded PII Detection Tests")
class PIIDetectionServiceExpandedTest {

    @Test
    @DisplayName("Should detect international phone numbers")
    void shouldDetectInternationalPhoneNumbers() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result1 = service.detectPII("+44 20 7946 0958");
        assertThat(result1.containsPII()).isTrue();
        assertThat(result1.getFindings()).containsKey("PHONE_INTL");
        
        PIIDetectionService.PIIDetectionResult result2 = service.detectPII("+1-415-555-0132");
        assertThat(result2.containsPII()).isTrue();
    }

    @Test
    @DisplayName("Should detect unformatted SSN")
    void shouldDetectUnformattedSSN() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("123456789");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("SSN_UNFORMATTED");
    }

    @Test
    @DisplayName("Should detect formatted credit card")
    void shouldDetectFormattedCreditCard() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("4111-1111-1111-1111");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("CREDIT_CARD_FORMATTED");
    }

    @Test
    @DisplayName("Should detect IPv6 addresses")
    void shouldDetectIPv6Addresses() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("IPV6");
    }

    @Test
    @DisplayName("Should detect IBAN format")
    void shouldDetectIBANFormat() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("GB82WEST12345698765432");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("IBAN");
    }

    @Test
    @DisplayName("Should detect tax ID / EIN")
    void shouldDetectTaxId() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("12-3456789");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("TAX_ID");
    }

    @Test
    @DisplayName("Should detect date of birth in various formats")
    void shouldDetectDateOfBirthInVariousFormats() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result1 = service.detectPII("1990-01-15");
        assertThat(result1.containsPII()).isTrue();
        assertThat(result1.getFindings()).containsKey("DOB_FORMAT1");
        
        PIIDetectionService.PIIDetectionResult result2 = service.detectPII("01/15/1990");
        assertThat(result2.containsPII()).isTrue();
        assertThat(result2.getFindings()).containsKey("DOB_FORMAT2");
    }

    @Test
    @DisplayName("Should detect ZIP codes with and without ZIP+4")
    void shouldDetectZipCodes() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result1 = service.detectPII("12345");
        assertThat(result1.containsPII()).isTrue();
        assertThat(result1.getFindings()).containsKey("ZIP_US");
        
        PIIDetectionService.PIIDetectionResult result2 = service.detectPII("12345-6789");
        assertThat(result2.containsPII()).isTrue();
        assertThat(result2.getFindings()).containsKey("ZIP_US");
    }

    @Test
    @DisplayName("Should detect national ID numbers")
    void shouldDetectNationalIdNumbers() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("A123456789");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("NATIONAL_ID");
    }

    @Test
    @DisplayName("Should detect insurance policy numbers")
    void shouldDetectInsurancePolicyNumbers() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("POL123456789");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("INSURANCE_POLICY");
    }

    @Test
    @DisplayName("Should detect license plate numbers")
    void shouldDetectLicensePlateNumbers() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result = service.detectPII("ABC1234");
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getFindings()).containsKey("LICENSE_PLATE");
    }

    @Test
    @DisplayName("Should redact with different strategies")
    void shouldRedactWithDifferentStrategies() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        String email = "user@example.com";
        String masked = service.redactPII(email, PIIDetectionService.RedactionStrategy.MASKING);
        assertThat(masked).contains("***");
        
        String ssn = "123-45-6789";
        String redacted = service.redactPII(ssn, PIIDetectionService.RedactionStrategy.REMOVAL);
        assertThat(redacted).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("Should detect PII in structured data")
    void shouldDetectPIIInStructuredData() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        Map<String, Object> data = Map.of(
            "name", "John Doe",
            "email", "john@example.com",
            "phone", "555-123-4567",
            "nested", Map.of("ssn", "123-45-6789")
        );
        
        PIIDetectionService.PIIDetectionResult result = service.detectPIIInData(data);
        assertThat(result.containsPII()).isTrue();
        assertThat(result.getTotalPIICount()).isGreaterThan(0);
        assertThat(result.getFieldPaths()).containsKey("email");
        assertThat(result.getFieldPaths()).containsKey("nested.ssn");
    }

    @Test
    @DisplayName("Should handle empty and null content gracefully")
    void shouldHandleEmptyAndNullContentGracefully() {
        PIIDetectionService service = new PIIDetectionService(null);
        
        PIIDetectionService.PIIDetectionResult result1 = service.detectPII("");
        assertThat(result1.containsPII()).isFalse();
        
        PIIDetectionService.PIIDetectionResult result2 = service.detectPII(null);
        assertThat(result2.containsPII()).isFalse();
    }
}
