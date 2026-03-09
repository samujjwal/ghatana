/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.FileDiff;
import com.ghatana.refactorer.shared.VersionInfo;
import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for run report

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record RunReport(
        String exitReason, int passes, VersionInfo versions, List<FileDiff> diffs) {}
