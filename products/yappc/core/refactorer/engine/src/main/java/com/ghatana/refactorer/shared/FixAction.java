/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for fix action

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record FixAction(
        String engine,
        String description,
        Path targetFile,
        Optional<String> rangeOpt,
        Map<String, String> params) {}
