package com.ghatana.refactorer.shared;

/*
 * This file intentionally does not declare a public type named `Severity` anymore.
 * It was replaced during the consolidation effort so that the canonical
 * `com.ghatana.platform.domain.domain.Severity` is the only public enum with the simple
 * name `Severity` across the repository.
 *
 * If any module still requires legacy helpers, prefer importing
 * `com.ghatana.platform.domain.domain.Severity` or add a small, explicit adapter in the
 * consuming module.
 */

/**
 * Placeholder for legacy Severity references.
 *
 * <p>The canonical severity enum is now at
 * {@code com.ghatana.platform.domain.domain.Severity}.
 *
 * @doc.type class
 * @doc.purpose Placeholder for legacy refactorer Severity usages
 * @doc.layer product
 * @doc.pattern ValueObject
 */
final class RefactorerSeverityPlaceholder {
    private RefactorerSeverityPlaceholder() {
        // no instances
    }
}
