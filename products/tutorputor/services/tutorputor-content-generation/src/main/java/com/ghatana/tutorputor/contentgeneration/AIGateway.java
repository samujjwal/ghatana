package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;

/**
 * AI Gateway interface for AI-powered generation.
 * Stub for compilation — implementation pending.
 *
 * @doc.type interface
 * @doc.purpose AI content generation gateway port
 * @doc.layer product
 * @doc.pattern Gateway, Port
 */
public interface AIGateway {
    Promise<String> generatePattern(String input);
}
