/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.FileDiff;
import java.util.List;

/**

 * @doc.type class

 * @doc.purpose Handles diff manager operations

 * @doc.layer core

 * @doc.pattern Manager

 */

public final class DiffManager {
    private DiffManager() {}

    public static List<FileDiff> computeNoop() {
        return List.of();
    }
}
