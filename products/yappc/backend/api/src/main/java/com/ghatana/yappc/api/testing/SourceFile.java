/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.testing;

/**
 * SourceFile.
 *
 * @doc.type record
 * @doc.purpose source file
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SourceFile(String path, String language, int methodCount) {}
