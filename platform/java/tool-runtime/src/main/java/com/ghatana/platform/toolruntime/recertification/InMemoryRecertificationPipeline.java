/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link RecertificationPipeline} implementation.
 *
 * <p>On campaign creation, the pipeline auto-populates synthetic recertification items
 * according to the requested {@link RecertificationScope}. In production, this would
 * query live permission and policy registries to enumerate real items.
 *
 * <p>Suitable for development and testing. Production deployments should replace this
 * with an implementation backed by durable storage and real governance registries.
 *
 * @doc.type class
 * @doc.purpose In-memory recertification pipeline for dev/test
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryRecertificationPipeline implements RecertificationPipeline {

    private final Map<String, RecertificationCampaign> campaigns = new ConcurrentHashMap<>();
    private final Map<String, List<RecertificationItem>> itemsByCampaign = new ConcurrentHashMap<>();

    // ---- RecertificationPipeline ----------------------------------------

    @Override
    public Promise<RecertificationCampaign> createCampaign(
            String tenantId, String campaignName, RecertificationScope scope) {
        String campaignId = UUID.randomUUID().toString();
        List<RecertificationItem> items = generateItems(campaignId, scope);
        itemsByCampaign.put(campaignId, Collections.synchronizedList(new ArrayList<>(items)));

        RecertificationCampaign campaign = new RecertificationCampaign(
            campaignId, tenantId, campaignName, scope,
            CampaignStatus.IN_PROGRESS, items.size(), 0, 0,
            Instant.now(), null);
        campaigns.put(campaignId, campaign);
        return Promise.of(campaign);
    }

    @Override
    public Promise<RecertificationCampaign> getCampaign(String campaignId) {
        RecertificationCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) {
            return Promise.ofException(
                new IllegalArgumentException("No campaign found: " + campaignId));
        }
        return Promise.of(campaign);
    }

    @Override
    public Promise<List<RecertificationCampaign>> listCampaigns(String tenantId) {
        List<RecertificationCampaign> list = campaigns.values().stream()
            .filter(c -> c.tenantId().equals(tenantId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
        return Promise.of(list);
    }

    @Override
    public Promise<List<RecertificationItem>> getItems(String campaignId) {
        List<RecertificationItem> items = itemsByCampaign.get(campaignId);
        if (items == null) {
            return Promise.ofException(
                new IllegalArgumentException("No campaign found: " + campaignId));
        }
        return Promise.of(List.copyOf(items));
    }

    @Override
    public Promise<RecertificationItem> certify(
            String campaignId, String itemId, String certifierId) {
        return decide(campaignId, itemId, ItemDecision.CERTIFIED, certifierId, null);
    }

    @Override
    public Promise<RecertificationItem> revoke(
            String campaignId, String itemId, String certifierId, String reason) {
        if (reason == null || reason.isBlank()) {
            return Promise.ofException(
                new IllegalArgumentException("Revocation reason must not be blank"));
        }
        return decide(campaignId, itemId, ItemDecision.REVOKED, certifierId, reason);
    }

    @Override
    public Promise<RecertificationReport> generateReport(String campaignId) {
        RecertificationCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) {
            return Promise.ofException(
                new IllegalArgumentException("No campaign found: " + campaignId));
        }
        List<RecertificationItem> items = List.copyOf(itemsByCampaign.getOrDefault(campaignId, List.of()));

        long certified = items.stream().filter(i -> i.decision() == ItemDecision.CERTIFIED).count();
        long revoked   = items.stream().filter(i -> i.decision() == ItemDecision.REVOKED).count();
        long pending   = items.stream().filter(i -> i.decision() == ItemDecision.PENDING).count();

        List<RecertificationItem> revokedItems = items.stream()
            .filter(i -> i.decision() == ItemDecision.REVOKED)
            .collect(Collectors.toList());

        double certRate = items.isEmpty() ? 1.0
            : (double) certified / items.size();

        // Mark campaign complete if all items have been reviewed
        if (pending == 0 && campaign.status() == CampaignStatus.IN_PROGRESS) {
            RecertificationCampaign completed = new RecertificationCampaign(
                campaign.campaignId(), campaign.tenantId(), campaign.campaignName(),
                campaign.scope(), CampaignStatus.COMPLETED,
                campaign.totalItems(), (int) certified, (int) revoked,
                campaign.createdAt(), Instant.now());
            campaigns.put(campaignId, completed);
            campaign = completed;
        }

        RecertificationReport report = new RecertificationReport(
            campaign.campaignId(), campaign.tenantId(), campaign.campaignName(),
            campaign.scope(),
            items.size(), (int) certified, (int) revoked, (int) pending,
            certRate, revokedItems, Instant.now());
        return Promise.of(report);
    }

    // ---- Internals -------------------------------------------------------

    private Promise<RecertificationItem> decide(
            String campaignId, String itemId,
            ItemDecision decision, String certifierId, String notes) {
        List<RecertificationItem> items = itemsByCampaign.get(campaignId);
        if (items == null) {
            return Promise.ofException(
                new IllegalArgumentException("No campaign found: " + campaignId));
        }

        synchronized (items) {
            for (int i = 0; i < items.size(); i++) {
                RecertificationItem item = items.get(i);
                if (item.itemId().equals(itemId)) {
                    if (item.decision() != ItemDecision.PENDING) {
                        return Promise.ofException(
                            new IllegalStateException(
                                "Item " + itemId + " already reviewed: " + item.decision()));
                    }
                    RecertificationItem updated = new RecertificationItem(
                        item.itemId(), item.campaignId(), item.itemType(),
                        item.resourceId(), item.resourceName(), item.metadata(),
                        decision, certifierId, notes, Instant.now());
                    items.set(i, updated);
                    updateCampaignCounts(campaignId);
                    return Promise.of(updated);
                }
            }
        }
        return Promise.ofException(
            new IllegalArgumentException("No item found: " + itemId + " in campaign " + campaignId));
    }

    private void updateCampaignCounts(String campaignId) {
        RecertificationCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) return;
        List<RecertificationItem> items = itemsByCampaign.getOrDefault(campaignId, List.of());
        long certified = items.stream().filter(i -> i.decision() == ItemDecision.CERTIFIED).count();
        long revoked   = items.stream().filter(i -> i.decision() == ItemDecision.REVOKED).count();
        campaigns.put(campaignId, new RecertificationCampaign(
            campaign.campaignId(), campaign.tenantId(), campaign.campaignName(),
            campaign.scope(), campaign.status(),
            campaign.totalItems(), (int) certified, (int) revoked,
            campaign.createdAt(), campaign.completedAt()));
    }

    /**
     * Generates synthetic items appropriate to the given scope.
     * In production, this would query live registries.
     */
    private static List<RecertificationItem> generateItems(
            String campaignId, RecertificationScope scope) {
        List<RecertificationItem> items = new ArrayList<>();
        if (scope == RecertificationScope.AGENT_PERMISSIONS || scope == RecertificationScope.FULL) {
            items.add(item(campaignId, "agent-permission",
                "agent-001:read-events", "Agent-001 read-events grant",
                Map.of("agentId", "agent-001", "permission", "read-events")));
            items.add(item(campaignId, "agent-permission",
                "agent-002:write-memory", "Agent-002 write-memory grant",
                Map.of("agentId", "agent-002", "permission", "write-memory")));
        }
        if (scope == RecertificationScope.TOOL_REGISTRATIONS || scope == RecertificationScope.FULL) {
            items.add(item(campaignId, "tool-registration",
                "tool:web-search", "Web Search tool registration",
                Map.of("toolId", "web-search", "riskLevel", "MEDIUM")));
            items.add(item(campaignId, "tool-registration",
                "tool:file-io", "File I/O tool registration",
                Map.of("toolId", "file-io", "riskLevel", "HIGH")));
        }
        if (scope == RecertificationScope.POLICIES || scope == RecertificationScope.FULL) {
            items.add(item(campaignId, "policy",
                "policy:rate-limit-v1", "Rate limit policy v1",
                Map.of("policyId", "rate-limit-v1", "status", "active")));
        }
        if (scope == RecertificationScope.DATA_ACCESS_CONSENTS || scope == RecertificationScope.FULL) {
            items.add(item(campaignId, "data-consent",
                "consent:tenant-1:analytics", "Analytics data consent",
                Map.of("purpose", "analytics", "grantedAt", "2026-01-01")));
        }
        return items;
    }

    private static RecertificationItem item(
            String campaignId, String itemType,
            String resourceId, String resourceName,
            Map<String, Object> metadata) {
        return new RecertificationItem(
            UUID.randomUUID().toString(), campaignId, itemType,
            resourceId, resourceName, metadata,
            ItemDecision.PENDING, null, null, null);
    }
}
