package com.ghatana.datacloud.entity.observability;

/**
 * Enumeration of trace flags indicating sampling decisions.
 *
 * <p><b>Purpose</b><br>
 * Indicates whether a trace should be sampled and included in trace
 * collection backends. Follows OpenTelemetry trace flags specification.
 *
 * <p><b>Values</b><br>
 * - SAMPLED: Include trace in collection (bit 0 = 1)
 * - NOT_SAMPLED: Exclude trace from collection (bit 0 = 0)
 *
 * @doc.type enum
 * @doc.purpose Trace sampling flag enumeration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum TraceFlags {
  /** Trace should be sampled and collected. */
  SAMPLED("01"),

  /** Trace should not be sampled. */
  NOT_SAMPLED("00");

  private final String hex;

  TraceFlags(String hex) {
    this.hex = hex;
  }

  /** Returns the hex representation (00 or 01). */
  public String getHex() {
    return hex;
  }

  /** Returns TraceFlags from hex string. */
  public static TraceFlags fromHex(String hex) {
    if ("01".equals(hex) || "1".equals(hex)) {
      return SAMPLED;
    }
    return NOT_SAMPLED;
  }

  /** Returns TraceFlags from boolean (true = SAMPLED, false = NOT_SAMPLED). */
  public static TraceFlags fromBoolean(boolean sampled) {
    return sampled ? SAMPLED : NOT_SAMPLED;
  }
}
