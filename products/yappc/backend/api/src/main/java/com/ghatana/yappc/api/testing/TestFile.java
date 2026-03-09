package com.ghatana.yappc.api.testing;

/**
 * TestFile.
 *
 * @doc.type record
 * @doc.purpose test file
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestFile(
    String path,
    String sourcePath,
    int testCount
) {}
