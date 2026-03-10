package com.ghatana.yappc.agent;

/**
 * Budget constraints for step execution.
 *
 * @param maxTokens maximum LLM tokens allowed
 * @param maxCostUsd maximum cost in USD
 * @param maxWallTimeMs maximum wall clock time in milliseconds
 * @doc.type record
 * @doc.purpose Budget limits for resource-constrained execution
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Budget(long maxTokens, double maxCostUsd, long maxWallTimeMs) {}
