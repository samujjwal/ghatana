package com.ghatana.phr.hie;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NepalHieMessageBuilderTest {

    private final NepalHieMessageBuilder builder = new NepalHieMessageBuilder();

    @Test
    void buildsHl7PatientSummaryMessage() {
        String bundle = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                { "resource": { "resourceType": "Patient", "id": "patient-1", "name": [{ "text": "Aarati Shrestha" }] } },
                { "resource": { "resourceType": "Observation", "code": { "text": "HbA1c" }, "valueQuantity": { "value": 6.8, "unit": "%" } } },
                { "resource": { "resourceType": "MedicationRequest", "medicationCodeableConcept": { "text": "Metformin" }, "dosageInstruction": [{ "text": "1 tablet twice daily" }] } }
              ]
            }
            """;

        String message = builder.buildPatientSummaryMessage(
            "patient-1",
            "corr-1",
            bundle,
            new NepalHieConfig("https://example.invalid", "GHATANA-PHR", "PHR-NEPAL", "NEPAL-HIE", "NHIE", "token", Duration.ofSeconds(5))
        );

        assertTrue(message.contains("MSH|^~\\&|GHATANA-PHR|PHR-NEPAL|NEPAL-HIE|NHIE|"));
        assertTrue(message.contains("PID|||patient-1||Aarati Shrestha"));
        assertTrue(message.contains("OBX|1|TX|HbA1c||6.8 %"));
        assertTrue(message.contains("RXE|1||Metformin||1 tablet twice daily"));
    }
}