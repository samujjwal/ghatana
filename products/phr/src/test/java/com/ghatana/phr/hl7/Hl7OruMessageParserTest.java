package com.ghatana.phr.hl7;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Unit coverage for HL7 ORU lab message parsing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Hl7OruMessageParser")
class Hl7OruMessageParserTest {

    private final Hl7OruMessageParser parser = new Hl7OruMessageParser();

    @Test
    @DisplayName("parses patient, LOINC code, value, and status from ORU message")
    void parsesObservationMessage() {
        Hl7LabResultMessage message = parser.parse(sampleMessage());

        assertThat(message.patientId()).isEqualTo("patient-1");
        assertThat(message.loincCode()).isEqualTo("2160-0");
        assertThat(message.observationName()).isEqualTo("Creatinine");
        assertThat(message.unit()).isEqualTo("mg/dL");
        assertThat(message.status()).isEqualTo("F");
    }

    @Test
    @DisplayName("rejects malformed messages missing OBX segment")
    void rejectsMalformedMessages() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("MSH|^~\\&|LAB|GHATANA\nPID|||patient-1"));
    }

    static String sampleMessage() {
        return """
            MSH|^~\\&|LAB|GHATANA-LAB|PHR|GHATANA|20260407123000||ORU^R01|MSG-1|P|2.5
            PID|||patient-1^^^GHATANA||Sharma^Arjun
            OBR|1||order-1|24323-8^Basic Metabolic Panel|||20260407113000
            OBX|1|NM|2160-0^Creatinine||1.10|mg/dL|0.70-1.30|N|||F|||20260407120000
            """;
    }
}
