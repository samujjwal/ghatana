/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking LLM token usage per tenant.
 * Provides cost visibility for all LLM calls with billing integration support.
 *
 * @doc.type class
 * @doc.purpose Token usage tracking for LLM calls with cost visibility
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public final class TokenUsageTrackingService {

    private final Map<String, TenantUsage> tenantUsageMap = new ConcurrentHashMap<>();
    private final TokenCostCalculator costCalculator;
    private final int maxEntriesPerTenant;

    /**
     * Creates a token usage tracking service with default cost calculator.
     */
    public TokenUsageTrackingService() {
        this(new DefaultTokenCostCalculator(), 10000);
    }

    /**
     * Creates a token usage tracking service with custom cost calculator.
     *
     * @param costCalculator custom cost calculator
     * @param maxEntriesPerTenant maximum usage entries to retain per tenant
     */
    public TokenUsageTrackingService(TokenCostCalculator costCalculator, int maxEntriesPerTenant) {
        this.costCalculator = costCalculator;
        this.maxEntriesPerTenant = maxEntriesPerTenant;
    }

    /**
     * Records token usage for a completion request.
     *
     * @param tenantId tenant identifier
     * @param provider LLM provider name
     * @param model model name
     * @param result completion result with token information
     */
    public void recordUsage(String tenantId, String provider, String model, CompletionResult result) {
        TenantUsage tenantUsage = tenantUsageMap.computeIfAbsent(tenantId, k -> new TenantUsage(k));
        
        UsageEntry entry = new UsageEntry(
            java.util.UUID.randomUUID().toString(),
            provider,
            model,
            result.getPromptTokens(),
            result.getCompletionTokens(),
            result.getTokensUsed(),
            costCalculator.calculateCost(provider, model, result.getPromptTokens(), result.getCompletionTokens()),
            Instant.now()
        );

        tenantUsage.addEntry(entry);
    }

    /**
     * Gets total token usage for a tenant.
     *
     * @param tenantId tenant identifier
     * @return total usage summary
     */
    public UsageSummary getUsageSummary(String tenantId) {
        TenantUsage tenantUsage = tenantUsageMap.get(tenantId);
        if (tenantUsage == null) {
            return new UsageSummary(tenantId, 0, 0, 0, 0.0, 0);
        }
        return tenantUsage.getSummary();
    }

    /**
     * Gets usage for a tenant within a time range.
     *
     * @param tenantId tenant identifier
     * @param from start of time range
     * @param to end of time range
     * @return usage summary for the time range
     */
    public UsageSummary getUsageSummary(String tenantId, Instant from, Instant to) {
        TenantUsage tenantUsage = tenantUsageMap.get(tenantId);
        if (tenantUsage == null) {
            return new UsageSummary(tenantId, 0, 0, 0, 0.0, 0);
        }
        return tenantUsage.getSummary(from, to);
    }

    /**
     * Gets usage for a specific provider.
     *
     * @param tenantId tenant identifier
     * @param provider provider name
     * @return usage summary for the provider
     */
    public UsageSummary getProviderUsage(String tenantId, String provider) {
        TenantUsage tenantUsage = tenantUsageMap.get(tenantId);
        if (tenantUsage == null) {
            return new UsageSummary(tenantId, 0, 0, 0, 0.0, 0);
        }
        return tenantUsage.getProviderSummary(provider);
    }

    /**
     * Clears usage data for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void clearUsage(String tenantId) {
        tenantUsageMap.remove(tenantId);
    }

    /**
     * Clears all usage data.
     */
    public void clearAllUsage() {
        tenantUsageMap.clear();
    }

    /**
     * Gets the cost calculator.
     *
     * @return the cost calculator
     */
    public TokenCostCalculator getCostCalculator() {
        return costCalculator;
    }

    /**
     * Tenant usage container.
     */
    private static class TenantUsage {
        private final String tenantId;
        private final java.util.List<UsageEntry> entries = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final Map<String, AtomicLong> providerRequestCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> providerTokenCounts = new ConcurrentHashMap<>();

        TenantUsage(String tenantId) {
            this.tenantId = tenantId;
        }

        void addEntry(UsageEntry entry) {
            entries.add(entry);
            
            // Prune if over limit
            if (entries.size() > 10000) {
                entries.remove(0);
            }

            // Update provider stats
            providerRequestCounts.computeIfAbsent(entry.provider(), k -> new AtomicLong(0)).incrementAndGet();
            providerTokenCounts.computeIfAbsent(entry.provider(), k -> new AtomicLong(0)).addAndGet(entry.totalTokens());
        }

        UsageSummary getSummary() {
            long totalPromptTokens = entries.stream().mapToLong(UsageEntry::promptTokens).sum();
            long totalCompletionTokens = entries.stream().mapToLong(UsageEntry::completionTokens).sum();
            long totalTokens = entries.stream().mapToLong(UsageEntry::totalTokens).sum();
            double totalCost = entries.stream().mapToDouble(UsageEntry::cost).sum();
            
            return new UsageSummary(tenantId, totalPromptTokens, totalCompletionTokens, totalTokens, totalCost, entries.size());
        }

        UsageSummary getSummary(Instant from, Instant to) {
            java.util.List<UsageEntry> filtered = entries.stream()
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .toList();

            long totalPromptTokens = filtered.stream().mapToLong(UsageEntry::promptTokens).sum();
            long totalCompletionTokens = filtered.stream().mapToLong(UsageEntry::completionTokens).sum();
            long totalTokens = filtered.stream().mapToLong(UsageEntry::totalTokens).sum();
            double totalCost = filtered.stream().mapToDouble(UsageEntry::cost).sum();

            return new UsageSummary(tenantId, totalPromptTokens, totalCompletionTokens, totalTokens, totalCost, filtered.size());
        }

        UsageSummary getProviderSummary(String provider) {
            java.util.List<UsageEntry> filtered = entries.stream()
                .filter(e -> e.provider().equals(provider))
                .toList();

            long totalPromptTokens = filtered.stream().mapToLong(UsageEntry::promptTokens).sum();
            long totalCompletionTokens = filtered.stream().mapToLong(UsageEntry::completionTokens).sum();
            long totalTokens = filtered.stream().mapToLong(UsageEntry::totalTokens).sum();
            double totalCost = filtered.stream().mapToDouble(UsageEntry::cost).sum();

            return new UsageSummary(tenantId, totalPromptTokens, totalCompletionTokens, totalTokens, totalCost, filtered.size());
        }
    }

    /**
     * Usage entry record.
     *
     * @param id unique entry identifier
     * @param provider LLM provider
     * @param model model name
     * @param promptTokens prompt token count
     * @param completionTokens completion token count
     * @param totalTokens total token count
     * @param cost estimated cost
     * @param timestamp when the usage occurred
     */
    public record UsageEntry(
        String id,
        String provider,
        String model,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double cost,
        Instant timestamp
    ) {}

    /**
     * Usage summary for a tenant or provider.
     *
     * @param tenantId tenant identifier
     * @param promptTokens total prompt tokens
     * @param completionTokens total completion tokens
     * @param totalTokens total tokens
     * @param totalCost total estimated cost
     * @param requestCount number of requests
     */
    public record UsageSummary(
        String tenantId,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double totalCost,
        long requestCount
    ) {}

    /**
     * Interface for calculating token costs.
     */
    public interface TokenCostCalculator {
        double calculateCost(String provider, String model, long promptTokens, long completionTokens);
    }

    /**
     * Default cost calculator with standard pricing.
     */
    public static class DefaultTokenCostCalculator implements TokenCostCalculator {
        private static final Map<String, Double> PROMPT_PRICES = Map.of(
            "openai", 0.00003,  // $0.03 per 1K prompt tokens
            "anthropic", 0.000025  // $0.025 per 1K prompt tokens
        );
        
        private static final Map<String, Double> COMPLETION_PRICES = Map.of(
            "openai", 0.00006,  // $0.06 per 1K completion tokens
            "anthropic", 0.0001  // $0.10 per 1K completion tokens
        );

        @Override
        public double calculateCost(String provider, String model, long promptTokens, long completionTokens) {
            double promptPrice = PROMPT_PRICES.getOrDefault(provider.toLowerCase(), 0.00003);
            double completionPrice = COMPLETION_PRICES.getOrDefault(provider.toLowerCase(), 0.00006);
            
            double promptCost = (promptTokens / 1000.0) * promptPrice;
            double completionCost = (completionTokens / 1000.0) * completionPrice;
            
            return promptCost + completionCost;
        }
    }
}
