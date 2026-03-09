/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for version info

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record VersionInfo(String polyfixVersion, String gitCommit) {}
