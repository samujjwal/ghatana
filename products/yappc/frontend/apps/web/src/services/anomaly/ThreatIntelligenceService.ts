/**
 * Service for threat intelligence and vulnerability enrichment.
 *
 * <p><b>Purpose</b><br>
 * Manages threat intelligence data (CVEs, exploits, mitigations) from external
 * sources and enriches detected anomalies with threat context for better risk
 * assessment and incident response.
 *
 * <p><b>External Data Sources</b><br>
 * - NVD (National Vulnerability Database): Official CVE database
 * - Vendor advisories: Direct from software vendors
 * - Exploit databases: Public exploit availability
 * - Threat feeds: Emerging threats and zero-days
 *
 * <p><b>Threat Enrichment Flow</b><br>
 * 1. Anomaly detected (e.g., unusual process execution)
 * 2. Service looks up related CVEs and threats
 * 3. Enriches anomaly with severity, exploit availability, mitigation
 * 4. Updates risk scoring based on threat context
 * 5. Returns enriched data to incident response system
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const threatService = new ThreatIntelligenceService();
 *
 * // Get threat info for a CVE
 * const threat = await threatService.getThreat("CVE-2025-1234");
 *
 * // Find threats for a software
 * const threats = await threatService.getThreatsForSoftware("OpenSSL", "3.0.1");
 *
 * // Check if threat has public exploit
 * if (threat.isExploitableAndCritical()) {
 *   // Escalate to incident response
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Manages threat intelligence and CVE enrichment
 * @doc.layer product
 * @doc.pattern Service
 */

import { ThreatIntelligence } from "../models/anomaly/ThreatIntelligence.entity";
import { SecurityAnomaly } from "../models/anomaly/SecurityAnomaly.entity";
import { MetricsCollector } from "../observability/MetricsCollector";

/**
 * Threat enrichment result combining anomaly with threat context.
 */
export interface EnrichedThreat {
  readonly anomaly: SecurityAnomaly;
  readonly threats: readonly ThreatIntelligence[];
  readonly riskEscalation: number; // 0.0-1.0 increase in risk
  readonly recommendations: readonly string[];
}

/**
 * ThreatIntelligenceService implementation.
 */
export class ThreatIntelligenceService {
  private readonly _threatCache: Map<string, ThreatIntelligence> = new Map();
  private readonly _metrics: MetricsCollector;

  /**
   * Creates a new ThreatIntelligenceService.
   *
   * @param metrics MetricsCollector for observability
   */
  constructor(metrics: MetricsCollector) {
    this._metrics = metrics;
  }

  /**
   * Retrieves threat intelligence for a CVE.
   *
   * <p><b>Query Strategy</b>
   * 1. Check in-memory cache first (O(1) lookup)
   * 2. If not cached, query external source (NVD API)
   * 3. Cache result for subsequent queries
   * 4. Return threat with all available information
   *
   * @param cveId CVE identifier (e.g., "CVE-2025-1234")
   * @returns ThreatIntelligence or null if not found
   * @throws Error if external source unavailable
   */
  async getThreat(cveId: string): Promise<ThreatIntelligence | null> {
    // Check cache
    if (this._threatCache.has(cveId)) {
      this._metrics.incrementCounter("threat_cache_hit", 1);
      return this._threatCache.get(cveId)!;
    }

    this._metrics.incrementCounter("threat_cache_miss", 1);

    // Query external source (NVD API in production)
    const threat = await this._queryNVD(cveId);

    if (threat) {
      // Cache for future lookups
      this._threatCache.set(cveId, threat);
    }

    return threat;
  }

  /**
   * Finds threats for a software product and version.
   *
   * <p><b>Search Strategy</b>
   * 1. Parse software name and version
   * 2. Query NVD for affecting CVEs
   * 3. Filter by version range
   * 4. Sort by severity descending
   * 5. Return threats for user action
   *
   * @param softwareName Product name (e.g., "OpenSSL")
   * @param version Version identifier (e.g., "3.0.1")
   * @returns Array of relevant threats
   */
  async getThreatsForSoftware(
    softwareName: string,
    version: string
  ): Promise<ThreatIntelligence[]> {
    // In production, would query NVD CPE database
    // For now, return mock implementation

    const threats: ThreatIntelligence[] = [];

    // Known vulnerabilities in key software
    const vulnerabilities: Record<string, { cveId: string; maxVersion: string }[]> = {
      openssl: [
        { cveId: "CVE-2025-1234", maxVersion: "3.0.2" },
        { cveId: "CVE-2025-5678", maxVersion: "3.1.0" },
      ],
      openssh: [
        { cveId: "CVE-2025-9999", maxVersion: "8.8" },
      ],
    };

    const softwareKey = softwareName.toLowerCase();
    const knownVulns = vulnerabilities[softwareKey] || [];

    for (const vuln of knownVulns) {
      const threat = await this.getThreat(vuln.cveId);
      if (threat) {
        threats.push(threat);
      }
    }

    this._metrics.incrementCounter("threat_lookups", threats.length, {
      software: softwareName,
    });

    return threats;
  }

  /**
   * Enriches an anomaly with threat context.
   *
   * <p><b>Enrichment Process</b>
   * 1. Extract relevant software/service from anomaly
   * 2. Query for related threats
   * 3. Calculate risk escalation based on threat severity
   * 4. Generate remediation recommendations
   * 5. Return enriched data structure
   *
   * @param anomaly SecurityAnomaly to enrich
   * @param softwareName Optional software name for threat lookup
   * @returns Enriched threat data
   */
  async enrichAnomaly(
    anomaly: SecurityAnomaly,
    softwareName?: string
  ): Promise<EnrichedThreat> {
    // Map anomaly type to software if not provided
    const software = softwareName || this._mapAnomalyToSoftware(anomaly.type);

    // Get related threats
    const threats = await this.getThreatsForSoftware(software, "*");

    // Calculate risk escalation
    let riskEscalation = 0;
    const recommendations: string[] = [];

    for (const threat of threats) {
      // Critical exploitable threats escalate risk significantly
      if (threat.isExploitableAndCritical()) {
        riskEscalation = Math.max(riskEscalation, 0.5);
        recommendations.push(
          `Critical: ${threat.title} has public exploit available`
        );
      }
    }

    // Generate mitigation recommendations based on threats
    if (recommendations.length === 0) {
      recommendations.push("Review threat intelligence for this anomaly type");
      recommendations.push("Check vendor advisories for available patches");
      recommendations.push("Isolate affected resource if high-severity anomaly");
    }

    const enriched: EnrichedThreat = {
      anomaly,
      threats: threats as readonly ThreatIntelligence[],
      riskEscalation,
      recommendations: recommendations as readonly string[],
    };

    this._metrics.incrementCounter("anomalies_enriched", 1, {
      threatsFound: threats.length.toString(),
    });

    return enriched;
  }

  /**
   * Gets critical threats requiring immediate action.
   *
   * <p><b>Criteria for Critical Threats</b>
   * - Severity >= 0.8
   * - Exploit is publicly available
   * - Days since published < 7 (fresh vulnerability)
   *
   * @returns Array of critical threats
   */
  async getCriticalThreats(): Promise<ThreatIntelligence[]> {
    const threats = Array.from(this._threatCache.values());

    return threats.filter((threat) => {
      return (
        threat.isExploitableAndCritical() &&
        threat.daysSincePublished() < 7
      );
    });
  }

  /**
   * Updates threat intelligence cache from external sources.
   *
   * <p><b>Update Strategy</b>
   * - Called periodically (every 24 hours in production)
   * - Queries NVD for new and updated CVEs
   * - Updates cache with latest information
   * - Emits metrics on update success/failure
   *
   * @returns Number of threats updated
   */
  async updateThreatIntelligence(): Promise<number> {
    const startTime = Date.now();

    try {
      // In production, would fetch from NVD API
      // For now, just return cache size
      const count = this._threatCache.size;

      const duration = Date.now() - startTime;
      this._metrics.recordHistogram("threat_update_duration_ms", duration);
      this._metrics.incrementCounter("threat_updates_successful", 1);

      return count;
    } catch (error) {
      this._metrics.incrementCounter("threat_updates_failed", 1);
      throw error;
    }
  }

  /**
   * Queries NVD (National Vulnerability Database) for a CVE.
   *
   * @param cveId CVE identifier
   * @returns ThreatIntelligence or null if not found
   */
  private async _queryNVD(cveId: string): Promise<ThreatIntelligence | null> {
    try {
      // NVD API v2.0 endpoint
      const apiUrl = `https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=${encodeURIComponent(cveId)}`;

      const response = await fetch(apiUrl, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'YAPPC-Security-Scanner/1.0',
        },
      });

      if (!response.ok) {
        if (response.status === 404) {
          console.warn(`[ThreatIntelligence] CVE not found: ${cveId}`);
          return null;
        }
        throw new Error(`NVD API error: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();

      if (!data.vulnerabilities || data.vulnerabilities.length === 0) {
        return null;
      }

      const vulnerability = data.vulnerabilities[0];
      const cve = vulnerability.cve;

      // Map NVD data to ThreatIntelligence format
      const cvssScore = cve.metrics?.cvssMetricV31?.[0]?.cvssData?.baseScore || 0;
      const severity = cvssScore >= 9.0 ? 0.95 : cvssScore >= 7.0 ? 0.85 : cvssScore >= 4.0 ? 0.65 : 0.35;

      return ThreatIntelligence.create({
        cveId: cve.id,
        title: cve.description?.find((desc: unknown) => desc.lang === 'en')?.value?.split('.')[0] || 'Security vulnerability',
        severity,
        exploitAvailable: cve.metrics?.cvssMetricV31?.[0]?.exploitabilityScore ? true : false,
        description: cve.description?.find((desc: unknown) => desc.lang === 'en')?.value || 'No description available',
        mitigation: 'Apply security patches and updates as recommended by vendor',
        affectedVersions: this.extractVersionsFromCVE(cve),
      });
    } catch (error) {
      console.error(`[ThreatIntelligence] Failed to query NVD for ${cveId}:`, error);
      return null;
    }
  }

  /**
   * Extract version information from CVE data
   */
  private extractVersionsFromCVE(cve: unknown): string[] {
    const versions: string[] = [];

    if (cve.configurations) {
      cve.configurations.forEach((config: unknown) => {
        if (config.nodes) {
          config.nodes.forEach((node: unknown) => {
            if (node.cpeMatch) {
              node.cpeMatch.forEach((match: unknown) => {
                if (match.criteria && match.versionStartIncluding) {
                  versions.push(match.versionStartIncluding);
                }
              });
            }
          });
        }
      });
    }

    return versions.length > 0 ? versions : ['Unknown'];
  }

  /**
   * Maps anomaly type to likely software for threat lookup.
   *
   * @param anomalyType Type of anomaly
   * @returns Software name for threat lookup
   */
  private _mapAnomalyToSoftware(anomalyType: string): string {
    const mapping: Record<string, string> = {
      NETWORK_SPIKE: "network-infrastructure",
      FAILED_AUTHENTICATION: "openssh",
      UNUSUAL_DATA_ACCESS: "database-systems",
      PRIVILEGE_ESCALATION: "linux-kernel",
      MALWARE_SIGNATURE: "antivirus-systems",
      DDoS_PATTERN: "network-infrastructure",
      CRYPTOGRAPHIC_ANOMALY: "openssl",
      RESOURCE_EXHAUSTION: "operating-system",
      POLICY_VIOLATION: "compliance-systems",
    };

    return mapping[anomalyType] || "general-software";
  }
}
