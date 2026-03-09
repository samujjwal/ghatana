/**
 * Immutable entity representing threat intelligence data.
 *
 * <p><b>Purpose</b><br>
 * Stores vulnerability and threat information from external sources
 * (CVE database, threat feeds, etc.) used to correlate with detected anomalies
 * and assess risk.
 *
 * <p><b>Persistence</b><br>
 * PostgreSQL table with indexes on cveId and severity for fast lookups.
 * Synchronized with CVE database via automated updates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const threat = ThreatIntelligence.create({
 *   cveId: "CVE-2025-1234",
 *   title: "Critical RCE in OpenSSL",
 *   severity: 0.95,
 *   exploitAvailable: true,
 *   description: "Unauthenticated remote code execution in OpenSSL 3.0.x",
 *   mitigation: "Upgrade to OpenSSL 3.0.3 or later"
 * });
 *
 * // Enrich anomalies with threat data
 * const enriched = await threatService.enrichAnomaly(anomaly, threat);
 * }</pre>
 *
 * @doc.type entity
 * @doc.purpose Represents threat intelligence (CVEs, exploits, mitigations)
 * @doc.layer product
 * @doc.pattern Entity
 */

/**
 * Threat source indicating where this threat was reported/discovered.
 */
export type ThreatSource =
  | "NVD" // National Vulnerability Database
  | "MITRE"
  | "VENDOR_ADVISORY"
  | "EXPLOIT_DB"
  | "CUSTOM_FEED"
  | "THREAT_INTEL";

/**
 * Result of threat validation.
 */
export interface ThreatValidationResult {
  readonly isValid: boolean;
  readonly errors: readonly string[];
}

/**
 * Immutable threat intelligence entity.
 */
export class ThreatIntelligence {
  private readonly _id: string;
  private readonly _cveId: string;
  private readonly _title: string;
  private readonly _severity: number; // 0.0-1.0
  private readonly _exploitAvailable: boolean;
  private readonly _description: string;
  private readonly _mitigation: string;
  private readonly _affectedVersions: string[];
  private readonly _source: ThreatSource;
  private readonly _references: string[]; // URLs to additional resources
  private readonly _publishedAt: Date;
  private readonly _lastUpdatedAt: Date;
  private readonly _createdAt: Date;

  /**
   * Creates a new ThreatIntelligence instance.
   *
   * @param id Unique identifier
   * @param cveId CVE identifier (e.g., "CVE-2025-1234")
   * @param title Brief title of vulnerability
   * @param severity Severity score 0.0-1.0 from CVE database
   * @param exploitAvailable Whether exploit code is publicly available
   * @param description Detailed description
   * @param mitigation Recommended mitigation steps
   * @param affectedVersions List of affected software versions
   * @param source Origin of this threat intelligence
   * @param references URLs to additional resources
   * @param publishedAt When vulnerability was published
   * @param lastUpdatedAt When threat info was last updated
   * @param createdAt When record was created
   */
  private constructor(
    id: string,
    cveId: string,
    title: string,
    severity: number,
    exploitAvailable: boolean,
    description: string,
    mitigation: string,
    affectedVersions: string[],
    source: ThreatSource,
    references: string[],
    publishedAt: Date,
    lastUpdatedAt: Date,
    createdAt: Date
  ) {
    this._id = id;
    this._cveId = cveId;
    this._title = title;
    this._severity = severity;
    this._exploitAvailable = exploitAvailable;
    this._description = description;
    this._mitigation = mitigation;
    this._affectedVersions = affectedVersions;
    this._source = source;
    this._references = references;
    this._publishedAt = publishedAt;
    this._lastUpdatedAt = lastUpdatedAt;
    this._createdAt = createdAt;
  }

  /**
   * Creates a new ThreatIntelligence from creation parameters.
   *
   * @param params Creation parameters
   * @returns New ThreatIntelligence instance
   * @throws Error if validation fails
   */
  public static create(params: {
    readonly cveId: string;
    readonly title: string;
    readonly severity: number;
    readonly exploitAvailable: boolean;
    readonly description: string;
    readonly mitigation: string;
    readonly affectedVersions?: readonly string[];
    readonly source?: ThreatSource;
    readonly references?: readonly string[];
    readonly publishedAt?: Date;
  }): ThreatIntelligence {
    const id = `threat-${params.cveId}`;
    const now = new Date();

    // Validate severity
    if (params.severity < 0 || params.severity > 1) {
      throw new Error("Severity must be between 0.0 and 1.0");
    }

    const threat = new ThreatIntelligence(
      id,
      params.cveId,
      params.title,
      params.severity,
      params.exploitAvailable,
      params.description,
      params.mitigation,
      params.affectedVersions ? Array.from(params.affectedVersions) : [],
      params.source ?? "NVD",
      params.references ? Array.from(params.references) : [],
      params.publishedAt ?? now,
      now,
      now
    );

    // Validate on creation
    const validation = threat.validate();
    if (!validation.isValid) {
      throw new Error(`Validation failed: ${validation.errors.join(", ")}`);
    }

    return threat;
  }

  /**
   * Validates this threat's state.
   *
   * @returns ThreatValidationResult with isValid flag and errors
   */
  public validate(): ThreatValidationResult {
    const errors: string[] = [];

    // CVE ID format validation
    if (!/^CVE-\d{4}-\d{4,}$/.test(this._cveId)) {
      errors.push(
        `Invalid CVE ID format: ${this._cveId} (expected CVE-YYYY-NNNNN)`
      );
    }

    // Title validation
    if (!this._title || this._title.trim().length === 0) {
      errors.push("Title cannot be empty");
    }
    if (this._title.length > 500) {
      errors.push("Title cannot exceed 500 characters");
    }

    // Severity validation
    if (this._severity < 0 || this._severity > 1) {
      errors.push("Severity must be between 0.0 and 1.0");
    }

    // Description validation
    if (!this._description || this._description.trim().length === 0) {
      errors.push("Description cannot be empty");
    }

    // Mitigation validation
    if (!this._mitigation || this._mitigation.trim().length === 0) {
      errors.push("Mitigation cannot be empty");
    }

    // Reference URLs validation
    for (const ref of this._references) {
      try {
        new URL(ref);
      } catch {
        errors.push(`Invalid reference URL: ${ref}`);
      }
    }

    return {
      isValid: errors.length === 0,
      errors: errors as readonly string[],
    };
  }

  /**
   * Determines if exploit is available and critical.
   *
   * @returns true if exploit available AND severity >= 0.8
   */
  public isExploitableAndCritical(): boolean {
    return this._exploitAvailable && this._severity >= 0.8;
  }

  /**
   * Updates affected versions (creates new instance due to immutability).
   *
   * @param versions New affected versions list
   * @returns New ThreatIntelligence with updated versions
   */
  public withAffectedVersions(versions: readonly string[]): ThreatIntelligence {
    return new ThreatIntelligence(
      this._id,
      this._cveId,
      this._title,
      this._severity,
      this._exploitAvailable,
      this._description,
      this._mitigation,
      Array.from(versions),
      this._source,
      this._references,
      this._publishedAt,
      new Date(),
      this._createdAt
    );
  }

  /**
   * Adds reference URL to this threat.
   *
   * @param url Reference URL to add
   * @returns New ThreatIntelligence with additional reference
   */
  public withReference(url: string): ThreatIntelligence {
    return new ThreatIntelligence(
      this._id,
      this._cveId,
      this._title,
      this._severity,
      this._exploitAvailable,
      this._description,
      this._mitigation,
      this._affectedVersions,
      this._source,
      [...this._references, url],
      this._publishedAt,
      new Date(),
      this._createdAt
    );
  }

  // Getters (readonly properties)

  public get id(): string {
    return this._id;
  }

  public get cveId(): string {
    return this._cveId;
  }

  public get title(): string {
    return this._title;
  }

  public get severity(): number {
    return this._severity;
  }

  public get exploitAvailable(): boolean {
    return this._exploitAvailable;
  }

  public get description(): string {
    return this._description;
  }

  public get mitigation(): string {
    return this._mitigation;
  }

  public get affectedVersions(): readonly string[] {
    return Object.freeze([...this._affectedVersions]);
  }

  public get source(): ThreatSource {
    return this._source;
  }

  public get references(): readonly string[] {
    return Object.freeze([...this._references]);
  }

  public get publishedAt(): Date {
    return new Date(this._publishedAt);
  }

  public get lastUpdatedAt(): Date {
    return new Date(this._lastUpdatedAt);
  }

  public get createdAt(): Date {
    return new Date(this._createdAt);
  }

  /**
   * Determines severity category.
   *
   * @returns "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
   */
  public severityCategory(): "LOW" | "MEDIUM" | "HIGH" | "CRITICAL" {
    if (this._severity >= 0.8) return "CRITICAL";
    if (this._severity >= 0.6) return "HIGH";
    if (this._severity >= 0.3) return "MEDIUM";
    return "LOW";
  }

  /**
   * Returns days since vulnerability was published.
   *
   * @returns Number of days
   */
  public daysSincePublished(): number {
    const now = new Date();
    const diff = now.getTime() - this._publishedAt.getTime();
    return Math.floor(diff / (1000 * 60 * 60 * 24));
  }

  /**
   * Converts to plain object for serialization.
   *
   * @returns Plain object representation
   */
  public toPlainObject(): Record<string, unknown> {
    return {
      id: this._id,
      cveId: this._cveId,
      title: this._title,
      severity: this._severity,
      severityCategory: this.severityCategory(),
      exploitAvailable: this._exploitAvailable,
      isExploitableAndCritical: this.isExploitableAndCritical(),
      description: this._description,
      mitigation: this._mitigation,
      affectedVersions: this._affectedVersions,
      source: this._source,
      references: this._references,
      publishedAt: this._publishedAt.toISOString(),
      daysSincePublished: this.daysSincePublished(),
      lastUpdatedAt: this._lastUpdatedAt.toISOString(),
      createdAt: this._createdAt.toISOString(),
    };
  }
}

/**
 * Factory for creating ThreatIntelligence instances in tests.
 */
export class ThreatIntelligenceFactory {
  /**
   * Creates a default ThreatIntelligence for testing.
   *
   * @param overrides Partial overrides for default values
   * @returns ThreatIntelligence instance with sensible defaults
   */
  public static create(
    overrides?: Partial<Parameters<typeof ThreatIntelligence.create>[0]>
  ): ThreatIntelligence {
    const defaults: Parameters<typeof ThreatIntelligence.create>[0] = {
      cveId: "CVE-2025-0001",
      title: "Test vulnerability",
      severity: 0.75,
      exploitAvailable: false,
      description: "Test threat for unit testing",
      mitigation: "Apply security patch",
      affectedVersions: ["1.0.0", "1.0.1"],
      source: "NVD",
      ...overrides,
    };

    return ThreatIntelligence.create(defaults);
  }

  /**
   * Creates a critical exploitable threat.
   *
   * @returns ThreatIntelligence with critical severity and exploit available
   */
  public static criticalExploitable(): ThreatIntelligence {
    return this.create({
      cveId: "CVE-2025-9999",
      title: "Critical RCE vulnerability",
      severity: 0.95,
      exploitAvailable: true,
    });
  }

  /**
   * Creates a low-severity non-exploitable threat.
   *
   * @returns ThreatIntelligence with low severity and no exploit
   */
  public static lowNonExploitable(): ThreatIntelligence {
    return this.create({
      cveId: "CVE-2025-0001",
      severity: 0.15,
      exploitAvailable: false,
    });
  }
}
