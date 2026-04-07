package com.ghatana.phr.hl7;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type record
 * @doc.purpose Canonical parsed view of an inbound HL7 ORU lab result message
 * @doc.layer product
 * @doc.pattern DTO
 */
public record Hl7LabResultMessage(
    String patientId,
    String orderId,
    String sendingFacility,
    String loincCode,
    String observationName,
    BigDecimal value,
    String unit,
    String referenceRange,
    String interpretation,
    String status,
    Instant observedAt
) {}