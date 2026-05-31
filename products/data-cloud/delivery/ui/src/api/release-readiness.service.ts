/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * Release Readiness Service — API client for product release readiness data.
 *
 * Provides CRUD operations for product release readiness evidence in Data Cloud.
 * Supports PHR, DMOS, and other products in the product family.
 *
 * @doc.purpose Release readiness API client
 * @doc.layer data-cloud-ui
 */

export interface ReleaseReadiness {
  id?: string;
  productId: string;
  productVersion: string;
  releaseTarget: "production" | "staging" | "development";
  releaseVerdict: "pass" | "fail";
  averageScore?: number;
  releaseTargetScore?: number;
  generatedAt: string;
  evidence: Record<string, unknown>;
  blockingGaps: Array<Record<string, unknown>>;
  belowTargetDimensions: Array<Record<string, unknown>>;
  tenantId: string;
  commitSha: string;
  evidenceEnvironment: "production" | "staging" | "development";
  createdAt: string;
  updatedAt: string;
}

export interface ReleaseReadinessStats {
  totalReleases: number;
  passedReleases: number;
  failedReleases: number;
  averageScore: number;
  byProduct: Record<string, { total: number; passed: number; failed: number }>;
  byTarget: Record<string, { total: number; passed: number; failed: number }>;
}

export interface ReleaseReadinessQuery {
  productId?: string;
  productVersion?: string;
  releaseTarget?: "production" | "staging" | "development";
  releaseVerdict?: "pass" | "fail";
  limit?: number;
  offset?: number;
}

const API_BASE = "/api/v1/release-readiness";

export const releaseReadinessService = {
  /**
   * Produce release readiness evidence for a product.
   */
  async produceReleaseReadiness(
    readiness: Omit<ReleaseReadiness, "id" | "createdAt" | "updatedAt">,
  ): Promise<ReleaseReadiness> {
    const response = await fetch(`${API_BASE}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(readiness),
    });

    if (!response.ok) {
      throw new Error(
        `Failed to produce release readiness: ${response.statusText}`,
      );
    }

    return response.json();
  },

  /**
   * Get release readiness for a specific product version and target.
   */
  async getReleaseReadiness(
    productId: string,
    productVersion: string,
    releaseTarget: string,
    tenantId: string,
  ): Promise<ReleaseReadiness | null> {
    const response = await fetch(
      `${API_BASE}/${productId}/${productVersion}/${releaseTarget}?tenantId=${tenantId}`,
    );

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(
        `Failed to get release readiness: ${response.statusText}`,
      );
    }

    return response.json();
  },

  /**
   * List release readiness records for a product.
   */
  async listReleaseReadiness(
    query: ReleaseReadinessQuery & { tenantId: string },
  ): Promise<ReleaseReadiness[]> {
    const params = new URLSearchParams();
    if (query.productId) params.set("productId", query.productId);
    if (query.productVersion)
      params.set("productVersion", query.productVersion);
    if (query.releaseTarget) params.set("releaseTarget", query.releaseTarget);
    if (query.releaseVerdict)
      params.set("releaseVerdict", query.releaseVerdict);
    if (query.tenantId) params.set("tenantId", query.tenantId);
    if (query.limit) params.set("limit", query.limit.toString());
    if (query.offset) params.set("offset", query.offset.toString());

    const response = await fetch(`${API_BASE}?${params.toString()}`);

    if (!response.ok) {
      throw new Error(
        `Failed to list release readiness: ${response.statusText}`,
      );
    }

    return response.json();
  },

  /**
   * Get release readiness statistics.
   */
  async getReleaseReadinessStats(
    tenantId: string,
  ): Promise<ReleaseReadinessStats> {
    const response = await fetch(`${API_BASE}/stats?tenantId=${tenantId}`);

    if (!response.ok) {
      throw new Error(
        `Failed to get release readiness stats: ${response.statusText}`,
      );
    }

    return response.json();
  },

  /**
   * Delete release readiness record.
   */
  async deleteReleaseReadiness(id: string, tenantId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${id}?tenantId=${tenantId}`, {
      method: "DELETE",
    });

    if (!response.ok) {
      throw new Error(
        `Failed to delete release readiness: ${response.statusText}`,
      );
    }
  },
};
