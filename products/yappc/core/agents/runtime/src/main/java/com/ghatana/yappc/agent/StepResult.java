package com.ghatana.yappc.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of step execution with status, output, and metadata.
 *
 * @param <O>       output type
 * @param status    execution status
 * @param output    step output (null if failed)
 * @param warnings  non-fatal warning messages
 * @param errors    fatal error messages
 * @param metadata  execution metadata (timings, provenance, etc.)
 * @param startedAt execution start timestamp
 * @param endedAt   execution end timestamp
 * @doc.type record
 * @doc.purpose Comprehensive step execution result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StepResult<O>(
    Status status,
    O output,
    List<String> warnings,
    List<String> errors,
    Map<String, Object> metadata,
    Instant startedAt,
    Instant endedAt) {
  /**
   * Step execution status.
   *
   * @doc.type enum
   * @doc.purpose Step execution outcome states
   * @doc.layer product
   * @doc.pattern Enumeration
   */
  public enum Status {
    /** Step completed successfully */
    SUCCESS,
    /** Step failed with errors */
    FAILED,
    /** Step waiting for human review (HITL) */
    WAITING_REVIEW
  }

  public static <O> StepResult<O> success(O out, Map<String, Object> md, Instant s, Instant e) {
    return new StepResult<>(Status.SUCCESS, out, List.of(), List.of(), md, s, e);
  }

  public static <O> StepResult<O> failed(
      List<String> errors, Map<String, Object> md, Instant s, Instant e) {
    return new StepResult<>(Status.FAILED, null, List.of(), errors, md, s, e);
  }

  public static <O> StepResult<O> waitingReview(
      O out, Map<String, Object> md, Instant s, Instant e) {
    return new StepResult<>(Status.WAITING_REVIEW, out, List.of(), List.of(), md, s, e);
  }

  public Map<String, Object> metrics() {
    return metadata != null ? metadata : Map.of();
  }

  public long durationMs() {
    if (startedAt == null || endedAt == null)
      return 0L;
    return java.time.Duration.between(startedAt, endedAt).toMillis();
  }

  public boolean success() {
    return status == Status.SUCCESS;
  }

  /** Convenience alias for {@link #success()} for readability. */
  public boolean isSuccess() {
    return success();
  }
}
