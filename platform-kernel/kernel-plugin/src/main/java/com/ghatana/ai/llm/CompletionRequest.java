package com.ghatana.ai.llm;

/**
 * @doc.type record
 * @doc.purpose Minimal completion request type used by extracted kernel plugin AI SPI.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record CompletionRequest(String prompt) {
}