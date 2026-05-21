package com.ghatana.phr.healthcare.privacy;

import com.ghatana.phr.healthcare.domain.DataClassification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiClassifier")
class PiiClassifierTest {

    private final PiiClassifier classifier = new PiiClassifier();

    @Test
    @DisplayName("should classify NHS ID as C2")
    void shouldClassifyNhsIdAsC2() {
        String text = "Patient NHS ID: 12345678";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("nhsId") && f.classification() == DataClassification.C2);
    }

    @Test
    @DisplayName("should classify phone number as C2")
    void shouldClassifyPhoneAsC2() {
        String text = "Phone: +977-123-456-7890";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("phone") && f.classification() == DataClassification.C2);
    }

    @Test
    @DisplayName("should classify email address as C2")
    void shouldClassifyEmailAsC2() {
        String text = "Email: patient@example.com";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("email") && f.classification() == DataClassification.C2);
    }

    @Test
    @DisplayName("should classify address as C2")
    void shouldClassifyAddressAsC2() {
        String text = "Address: 123 Main Street Kathmandu";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("address") && f.classification() == DataClassification.C2);
    }

    @Test
    @DisplayName("should classify medical record as C3")
    void shouldClassifyMedicalRecordAsC3() {
        String text = "Diagnosis: Type 2 Diabetes";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("medicalRecord") && f.classification() == DataClassification.C3);
    }

    @Test
    @DisplayName("should classify sensitive condition as C4")
    void shouldClassifySensitiveConditionAsC4() {
        String text = "Patient has HIV positive status";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).anyMatch(f -> f.fieldName().equals("sensitiveCondition") && f.classification() == DataClassification.C4);
    }

    @Test
    @DisplayName("should redact C4 data when max allowed is C3")
    void shouldRedactC4DataWhenMaxAllowedIsC3() {
        String text = "Patient has HIV positive status and diabetes";
        String redacted = classifier.redact(text, DataClassification.C3);
        
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("HIV");
    }

    @Test
    @DisplayName("should redact C3 data when max allowed is C2")
    void shouldRedactC3DataWhenMaxAllowedIsC2() {
        String text = "Diagnosis: Type 2 Diabetes with medication";
        String redacted = classifier.redact(text, DataClassification.C2);
        
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("diagnosis");
    }

    @Test
    @DisplayName("should redact C2 data when max allowed is C1")
    void shouldRedactC2DataWhenMaxAllowedIsC1() {
        String text = "Contact: 12345678, patient@example.com";
        String redacted = classifier.redact(text, DataClassification.C1);
        
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("12345678");
        assertThat(redacted).doesNotContain("patient@example.com");
    }

    @Test
    @DisplayName("should return C4 as highest classification when sensitive condition present")
    void shouldReturnC4AsHighestClassificationWhenSensitiveConditionPresent() {
        String text = "Patient has HIV positive status";
        DataClassification classification = classifier.getHighestClassification(text);
        
        assertThat(classification).isEqualTo(DataClassification.C4);
    }

    @Test
    @DisplayName("should return C3 as highest classification when medical record present")
    void shouldReturnC3AsHighestClassificationWhenMedicalRecordPresent() {
        String text = "Diagnosis: Type 2 Diabetes";
        DataClassification classification = classifier.getHighestClassification(text);
        
        assertThat(classification).isEqualTo(DataClassification.C3);
    }

    @Test
    @DisplayName("should return C2 as highest classification when only contact info present")
    void shouldReturnC2AsHighestClassificationWhenOnlyContactInfoPresent() {
        String text = "Phone: 1234567890";
        DataClassification classification = classifier.getHighestClassification(text);
        
        assertThat(classification).isEqualTo(DataClassification.C2);
    }

    @Test
    @DisplayName("should return C1 as highest classification when no PII present")
    void shouldReturnC1AsHighestClassificationWhenNoPiiPresent() {
        String text = "Patient visit summary";
        DataClassification classification = classifier.getHighestClassification(text);
        
        assertThat(classification).isEqualTo(DataClassification.C1);
    }

    @Test
    @DisplayName("should return empty list when no PII detected")
    void shouldReturnEmptyListWhenNoPiiDetected() {
        String text = "General medical information";
        List<PiiClassifier.PiiField> piiFields = classifier.classify(text);
        
        assertThat(piiFields).isEmpty();
    }
}
