package com.ghatana.yappc.agent;

import java.util.List;

/**
 * Result of input validation.
 *
 * @param ok true if validation passed
 * @param errors list of validation error messages
 * @doc.type record
 * @doc.purpose Validation result with error details
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidationResult(boolean ok, List<String> errors) {
  public static ValidationResult success() {
    return new ValidationResult(true, List.of());
  }

  public static ValidationResult empty() {
    return success();
  }

  public static ValidationResult fail(String... errs) {
    return new ValidationResult(false, List.of(errs));
  }

  /** Convenience alias for {@link #ok()} for readability. */
  public boolean isValid() {
    return ok;
  }
}
