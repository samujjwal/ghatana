package com.ghatana.datacloud.entity.observability;

/**
 * Enumeration of span execution statuses.
 *
 * <p><b>Purpose</b><br>
 * Defines the possible completion states for a distributed tracing span,
 * following OpenTelemetry span status specification.
 *
 * <p><b>Values</b><br>
 * - UNSET: Initial state, operation in progress
 * - OK: Operation completed successfully
 * - ERROR: Operation failed or completed with error
 * - DEADLINE_EXCEEDED: Operation did not complete within deadline
 * - UNKNOWN: Unable to determine operation status
 *
 * @doc.type enum
 * @doc.purpose OpenTelemetry span status enumeration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum SpanStatus {
  /** Initial/unset status — operation in progress. */
  UNSET("unset"),

  /** Operation completed successfully. */
  OK("ok"),

  /** Operation failed or completed with error. */
  ERROR("error"),

  /** Operation did not complete within deadline. */
  DEADLINE_EXCEEDED("deadline_exceeded"),

  /** Unable to determine operation status. */
  UNKNOWN("unknown");

  private final String code;

  SpanStatus(String code) {
    this.code = code;
  }

  /** Returns the code string representation. */
  public String getCode() {
    return code;
  }

  /** Returns SpanStatus from code string, or UNKNOWN if not found. */
  public static SpanStatus fromCode(String code) {
    for (SpanStatus status : values()) {
      if (status.code.equalsIgnoreCase(code)) {
        return status;
      }
    }
    return UNKNOWN;
  }
}
