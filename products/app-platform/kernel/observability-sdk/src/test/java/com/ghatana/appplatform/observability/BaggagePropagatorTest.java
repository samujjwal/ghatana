package com.ghatana.appplatform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BaggagePropagator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for W3C Baggage encode/decode
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BaggagePropagator — Unit Tests")
class BaggagePropagatorTest {

    @Test
    @DisplayName("encode_allFields — produces valid W3C baggage header")
    void encodeAllFields() {
        String header = BaggagePropagator.encode("t-uuid-123", "NP", "2081-2082", "auditor");

        assertThat(header).contains("tenant_id=t-uuid-123");
        assertThat(header).contains("jurisdiction=NP");
        assertThat(header).contains("fiscal_year=2081-2082");
        assertThat(header).contains("user_role=auditor");
    }

    @Test
    @DisplayName("encode_nullFieldsOmitted — null fields are skipped")
    void encodeNullFieldsOmitted() {
        String header = BaggagePropagator.encode("t-uuid-123", null, null, null);

        assertThat(header).contains("tenant_id=t-uuid-123");
        assertThat(header).doesNotContain("jurisdiction");
        assertThat(header).doesNotContain("fiscal_year");
        assertThat(header).doesNotContain("user_role");
    }

    @Test
    @DisplayName("encode_blankFieldsOmitted — blank fields are skipped")
    void encodeBlankFieldsOmitted() {
        String header = BaggagePropagator.encode("t-uuid-123", "  ", "", null);

        assertThat(header).contains("tenant_id=t-uuid-123");
        assertThat(header).doesNotContain("jurisdiction");
    }

    @Test
    @DisplayName("decode_validHeader — parses all baggage entries")
    void decodeValidHeader() {
        String header = "tenant_id=t-uuid-123,jurisdiction=NP,fiscal_year=2081-2082,user_role=auditor";
        Map<String, String> result = BaggagePropagator.decode(header);

        assertThat(result).containsEntry("tenant_id", "t-uuid-123")
                          .containsEntry("jurisdiction", "NP")
                          .containsEntry("fiscal_year", "2081-2082")
                          .containsEntry("user_role", "auditor");
    }

    @Test
    @DisplayName("decode_null — returns empty map")
    void decodeNullReturnsEmpty() {
        assertThat(BaggagePropagator.decode(null)).isEmpty();
    }

    @Test
    @DisplayName("decode_emptyString — returns empty map")
    void decodeEmptyStringReturnsEmpty() {
        assertThat(BaggagePropagator.decode("")).isEmpty();
    }

    @Test
    @DisplayName("roundtrip — encode then decode recovers original values")
    void roundtrip() {
        String encoded = BaggagePropagator.encode("tenant-abc", "NP", "2082-2083", "trader");
        Map<String, String> decoded = BaggagePropagator.decode(encoded);

        assertThat(decoded).containsEntry(BaggagePropagator.KEY_TENANT_ID, "tenant-abc")
                           .containsEntry(BaggagePropagator.KEY_JURISDICTION, "NP")
                           .containsEntry(BaggagePropagator.KEY_FISCAL_YEAR, "2082-2083")
                           .containsEntry(BaggagePropagator.KEY_USER_ROLE, "trader");
    }

    @Test
    @DisplayName("encode_specialChars — percent-encodes values with special characters")
    void encodeSpecialChars() {
        Map<String, String> entries = Map.of("fiscal_year", "2081/2082");
        String header = BaggagePropagator.encode(entries);

        assertThat(header).doesNotContain("/");
        Map<String, String> decoded = BaggagePropagator.decode(header);
        assertThat(decoded.get("fiscal_year")).isEqualTo("2081/2082");
    }
}
