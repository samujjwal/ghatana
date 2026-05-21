package com.ghatana.phr.healthcare.privacy;

import com.ghatana.phr.healthcare.domain.DataClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PII classifier for healthcare data.
 *
 * <p>This classifier identifies and classifies PII (Personally Identifiable Information)
 * in healthcare records according to data classification levels C1-C4.</p>
 *
 * @doc.type class
 * @doc.purpose PII classification and redaction for healthcare data
 * @doc.layer domain-pack
 * @doc.pattern Classifier
 */
public final class PiiClassifier {

    // PII patterns for classification
    private static final Pattern NHS_ID_PATTERN = Pattern.compile(
        "\\b(?:NHS[-\\s:]*)?\\d{8}\\b"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?\\d{1,3}[-. ]?)?(?:\\d{10}|\\d{3}[-. ]\\d{3}[-. ]\\d{4})\\b"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("\\b\\d+\\s+[A-Za-z]+(?:\\s+[A-Za-z]+)*\\b");
    private static final Pattern MEDICAL_RECORD_PATTERN = Pattern.compile("\\b(?:diagnosis|condition|medication|treatment|lab result)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_CONDITION_PATTERN = Pattern.compile(
        "\\b(?:HIV|AIDS|mental health|psychiatric|substance abuse|genetic|reproductive|pregnancy|abortion)\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Classifies a text string and returns the detected PII fields with their classification levels.
     *
     * @param text the text to classify
     * @return list of detected PII fields with their classification levels
     */
    public List<PiiField> classify(String text) {
        Objects.requireNonNull(text, "text must not be null");
        
        List<PiiField> piiFields = new ArrayList<>();
        
        // Detect NHS ID (C2)
        if (NHS_ID_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("nhsId", "NHS ID", DataClassification.C2));
        }
        
        // Detect phone numbers (C2)
        if (PHONE_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("phone", "Phone Number", DataClassification.C2));
        }
        
        // Detect email addresses (C2)
        if (EMAIL_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("email", "Email Address", DataClassification.C2));
        }
        
        // Detect addresses (C2)
        if (ADDRESS_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("address", "Physical Address", DataClassification.C2));
        }
        
        // Detect medical records (C3)
        if (MEDICAL_RECORD_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("medicalRecord", "Medical Record", DataClassification.C3));
        }
        
        // Detect sensitive conditions (C4)
        if (SENSITIVE_CONDITION_PATTERN.matcher(text).find()) {
            piiFields.add(new PiiField("sensitiveCondition", "Sensitive Health Condition", DataClassification.C4));
        }
        
        return piiFields;
    }

    /**
     * Redacts PII from the given text based on the maximum allowed classification level.
     *
     * @param text the text to redact
     * @param maxAllowedClassification the maximum classification level allowed to remain visible
     * @return the redacted text
     */
    public String redact(String text, DataClassification maxAllowedClassification) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(maxAllowedClassification, "maxAllowedClassification must not be null");
        
        String redacted = text;
        
        // Redact C4 data if max allowed is lower
        if (maxAllowedClassification.ordinal() < DataClassification.C4.ordinal()) {
            redacted = SENSITIVE_CONDITION_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
        }
        
        // Redact C3 data if max allowed is lower
        if (maxAllowedClassification.ordinal() < DataClassification.C3.ordinal()) {
            redacted = MEDICAL_RECORD_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
        }
        
        // Redact C2 data if max allowed is lower
        if (maxAllowedClassification.ordinal() < DataClassification.C2.ordinal()) {
            redacted = NHS_ID_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
            redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
            redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
            redacted = ADDRESS_PATTERN.matcher(redacted).replaceAll("[REDACTED]");
        }
        
        return redacted;
    }

    /**
     * Returns the highest data classification level found in the text.
     *
     * @param text the text to analyze
     * @return the highest classification level found
     */
    public DataClassification getHighestClassification(String text) {
        Objects.requireNonNull(text, "text must not be null");
        
        List<PiiField> piiFields = classify(text);
        
        return piiFields.stream()
            .map(PiiField::classification)
            .max((c1, c2) -> Integer.compare(c1.ordinal(), c2.ordinal()))
            .orElse(DataClassification.C1);
    }

    /**
     * PII field with its classification level.
     */
    public record PiiField(
        String fieldName,
        String description,
        DataClassification classification
    ) {}
}
