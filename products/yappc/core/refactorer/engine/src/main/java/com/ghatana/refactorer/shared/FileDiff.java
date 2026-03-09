/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for file diff

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record FileDiff(String file, String beforeHash, String afterHash, String patch) {}
