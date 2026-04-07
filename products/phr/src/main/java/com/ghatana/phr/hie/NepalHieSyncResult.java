package com.ghatana.phr.hie;

/**
 * @doc.type record
 * @doc.purpose Outcome of a Nepal HIE patient summary submission attempt
 * @doc.layer product
 * @doc.pattern DTO
 */
public record NepalHieSyncResult(
    String patientId,
    String messageControlId,
    String acknowledgementCode,
    boolean accepted,
    String message,
    String hl7Message
) {}