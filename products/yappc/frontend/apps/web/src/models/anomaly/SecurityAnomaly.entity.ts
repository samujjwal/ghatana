/**
 * Immutable entity representing a detected security anomaly.
 *
 * <p><b>Purpose</b><br>
 * Stores detected security anomalies identified by Java ML algorithms
 * (Isolation Forest, trend detection, risk scoring).
 *
 * <p><b>Persistence</b><br>
 * PostgreSQL table with indexes on type, severity, detectedAt for querying anomalies
 * by classification and recency.
 *
 * <p><b>Lifecycle</b><br>
 * 1. Created by AnomalyDetectionService after Java ML analysis
 * 2. Linked to javaServiceExecutionId for traceability
 * 3. Status transitions: DETECTED → ACKNOWLEDGED → MITIGATED → RESOLVED
 * 4. Updated with investigation notes and remediation steps
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const anomaly = SecurityAnomaly.create({
 *   type: AnomalyType.NETWORK_SPIKE,
 *   severity: 0.85,
 *   baseline: 100,
 *   observed: 350,
 *   description: "Unexpected spike in outbound traffic",
 *   javaServiceExecutionId: "exec-12345"
 * });
 *
 * const validation = anomaly.validate();
 * if (validation.isValid) {
 *   await repository.save(anomaly);
 * }
 * }</pre>
 *
 * @doc.type entity
 * @doc.purpose Represents detected security anomalies from ML algorithms
 * @doc.layer product
 * @doc.pattern Entity
 */
export type AnomalyType =
  | "NETWORK_SPIKE"
  | "FAILED_AUTHENTICATION"
  | "UNUSUAL_DATA_ACCESS"
  | "PRIVILEGE_ESCALATION"
  | "MALWARE_SIGNATURE"
  | "DDoS_PATTERN"
  | "CRYPTOGRAPHIC_ANOMALY"
  | "RESOURCE_EXHAUSTION"
  | "POLICY_VIOLATION"
  | "UNKNOWN";

export type AnomalyStatus =
  | "DETECTED"
  | "ACKNOWLEDGED"
  | "INVESTIGATING"
  | "MITIGATED"
  | "RESOLVED"
  | "FALSE_POSITIVE";

/**
 * Severity score 0.0-1.0 with interpretation:
 * 0.0-0.3: Low (investigate when convenient)
 * 0.3-0.6: Medium (investigate soon)
 * 0.6-0.8: High (investigate immediately)
 * 0.8-1.0: Critical (activate incident response)
 */
export type SeverityLevel = number & { readonly __brand: "SeverityLevel" };

/**
 * Result of anomaly validation.
 */
export interface ValidationResult {
  readonly isValid: boolean;
  readonly errors: readonly string[];
}

/**
 * Metadata about the Java ML service execution that detected this anomaly.
 */
export interface JavaExecutionMetadata {
  readonly executionId: string;
  readonly algorithm: "ISOLATION_FOREST" | "TREND_DETECTOR" | "RISK_SCORER";
  readonly executedAt: Date;
  readonly processingTimeMs: number;
  readonly confidence: number; // 0.0-1.0
}

/**
 * Immutable security anomaly entity.
 */
export class SecurityAnomaly {
  private readonly _id: string;
  private readonly _type: AnomalyType;
  private readonly _severity: SeverityLevel;
  private readonly _baseline: number;
  private readonly _observed: number;
  private readonly _description: string;
  private readonly _detectedAt: Date;
  private readonly _status: AnomalyStatus;
  private readonly _javaServiceExecutionId: string;
  private readonly _javaExecutionMetadata: JavaExecutionMetadata;
  private readonly _investigationNotes: string[];
  private readonly _remediationSteps: string[];
  private readonly _relatedResourceIds: string[];
  private readonly _createdAt: Date;
  private readonly _updatedAt: Date;

  /**
   * Creates a new SecurityAnomaly instance.
   *
   * @param id Unique identifier
   * @param type Classification of anomaly (NETWORK_SPIKE, etc.)
   * @param severity Computed severity 0.0-1.0 from Java ML service
   * @param baseline Expected metric value (from BaselineCalculator)
   * @param observed Actual metric value
   * @param description Human-readable description of anomaly
   * @param detectedAt When anomaly was detected
   * @param status Current status in lifecycle
   * @param javaServiceExecutionId Reference to Java service execution for traceability
   * @param javaExecutionMetadata Metadata about ML execution
   * @param investigationNotes Investigation findings
   * @param remediationSteps Remediation actions taken or planned
   * @param relatedResourceIds IDs of affected resources
   * @param createdAt Timestamp of creation
   * @param updatedAt Timestamp of last update
   */
  private constructor(
    id: string,
    type: AnomalyType,
    severity: SeverityLevel,
    baseline: number,
    observed: number,
    description: string,
    detectedAt: Date,
    status: AnomalyStatus,
    javaServiceExecutionId: string,
    javaExecutionMetadata: JavaExecutionMetadata,
    investigationNotes: string[],
    remediationSteps: string[],
    relatedResourceIds: string[],
    createdAt: Date,
    updatedAt: Date
  ) {
    this._id = id;
    this._type = type;
    this._severity = severity;
    this._baseline = baseline;
    this._observed = observed;
    this._description = description;
    this._detectedAt = detectedAt;
    this._status = status;
    this._javaServiceExecutionId = javaServiceExecutionId;
    this._javaExecutionMetadata = javaExecutionMetadata;
    this._investigationNotes = investigationNotes;
    this._remediationSteps = remediationSteps;
    this._relatedResourceIds = relatedResourceIds;
    this._createdAt = createdAt;
    this._updatedAt = updatedAt;
  }

  /**
   * Creates a new SecurityAnomaly from creation parameters.
   *
   * @param params Creation parameters
   * @returns New SecurityAnomaly instance
   * @throws Error if validation fails
   */
  public static create(params: {
    readonly type: AnomalyType;
    readonly severity: number;
    readonly baseline: number;
    readonly observed: number;
    readonly description: string;
    readonly javaServiceExecutionId: string;
    readonly javaExecutionMetadata: JavaExecutionMetadata;
    readonly relatedResourceIds?: readonly string[];
  }): SecurityAnomaly {
    const id = `anomaly-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const now = new Date();

    // Validate severity
    if (params.severity < 0 || params.severity > 1) {
      throw new Error("Severity must be between 0.0 and 1.0");
    }

    const anomaly = new SecurityAnomaly(
      id,
      params.type,
      params.severity as SeverityLevel,
      params.baseline,
      params.observed,
      params.description,
      now,
      "DETECTED",
      params.javaServiceExecutionId,
      params.javaExecutionMetadata,
      [],
      [],
      params.relatedResourceIds ? Array.from(params.relatedResourceIds) : [],
      now,
      now
    );

    // Validate on creation
    const validation = anomaly.validate();
    if (!validation.isValid) {
      throw new Error(`Validation failed: ${validation.errors.join(", ")}`);
    }

    return anomaly;
  }

  /**
   * Validates this anomaly's state.
   *
   * @returns ValidationResult with isValid flag and any errors
   */
  public validate(): ValidationResult {
    const errors: string[] = [];

    // Type validation
    const validTypes: AnomalyType[] = [
      "NETWORK_SPIKE",
      "FAILED_AUTHENTICATION",
      "UNUSUAL_DATA_ACCESS",
      "PRIVILEGE_ESCALATION",
      "MALWARE_SIGNATURE",
      "DDoS_PATTERN",
      "CRYPTOGRAPHIC_ANOMALY",
      "RESOURCE_EXHAUSTION",
      "POLICY_VIOLATION",
      "UNKNOWN",
    ];
    if (!validTypes.includes(this._type)) {
      errors.push(`Invalid anomaly type: ${this._type}`);
    }

    // Severity validation
    if (this._severity < 0 || this._severity > 1) {
      errors.push("Severity must be between 0.0 and 1.0");
    }

    // Description validation
    if (!this._description || this._description.trim().length === 0) {
      errors.push("Description cannot be empty");
    }

    // Baseline/Observed validation
    if (this._baseline < 0) {
      errors.push("Baseline cannot be negative");
    }
    if (this._observed < 0) {
      errors.push("Observed value cannot be negative");
    }

    // Java execution metadata validation
    if (!this._javaExecutionMetadata.executionId) {
      errors.push("Java execution ID is required");
    }

    return {
      isValid: errors.length === 0,
      errors: errors as readonly string[],
    };
  }

  /**
   * Acknowledges this anomaly and adds investigation notes.
   *
   * @param notes Investigation notes to add
   * @returns New SecurityAnomaly with updated status and notes
   */
  public acknowledge(notes: string): SecurityAnomaly {
    return new SecurityAnomaly(
      this._id,
      this._type,
      this._severity,
      this._baseline,
      this._observed,
      this._description,
      this._detectedAt,
      "ACKNOWLEDGED",
      this._javaServiceExecutionId,
      this._javaExecutionMetadata,
      [...this._investigationNotes, notes],
      this._remediationSteps,
      this._relatedResourceIds,
      this._createdAt,
      new Date()
    );
  }

  /**
   * Adds a remediation step.
   *
   * @param step Remediation step to add
   * @returns New SecurityAnomaly with updated remediation steps
   */
  public addRemediationStep(step: string): SecurityAnomaly {
    return new SecurityAnomaly(
      this._id,
      this._type,
      this._severity,
      this._baseline,
      this._observed,
      this._description,
      this._detectedAt,
      "MITIGATED",
      this._javaServiceExecutionId,
      this._javaExecutionMetadata,
      this._investigationNotes,
      [...this._remediationSteps, step],
      this._relatedResourceIds,
      this._createdAt,
      new Date()
    );
  }

  /**
   * Marks this anomaly as resolved.
   *
   * @returns New SecurityAnomaly with status RESOLVED
   */
  public resolve(): SecurityAnomaly {
    return new SecurityAnomaly(
      this._id,
      this._type,
      this._severity,
      this._baseline,
      this._observed,
      this._description,
      this._detectedAt,
      "RESOLVED",
      this._javaServiceExecutionId,
      this._javaExecutionMetadata,
      this._investigationNotes,
      this._remediationSteps,
      this._relatedResourceIds,
      this._createdAt,
      new Date()
    );
  }

  /**
   * Marks this anomaly as a false positive.
   *
   * @returns New SecurityAnomaly with status FALSE_POSITIVE
   */
  public markFalsePositive(): SecurityAnomaly {
    return new SecurityAnomaly(
      this._id,
      this._type,
      this._severity,
      this._baseline,
      this._observed,
      this._description,
      this._detectedAt,
      "FALSE_POSITIVE",
      this._javaServiceExecutionId,
      this._javaExecutionMetadata,
      this._investigationNotes,
      this._remediationSteps,
      this._relatedResourceIds,
      this._createdAt,
      new Date()
    );
  }

  // Getters (readonly properties)

  public get id(): string {
    return this._id;
  }

  public get type(): AnomalyType {
    return this._type;
  }

  public get severity(): SeverityLevel {
    return this._severity;
  }

  public get baseline(): number {
    return this._baseline;
  }

  public get observed(): number {
    return this._observed;
  }

  public get description(): string {
    return this._description;
  }

  public get detectedAt(): Date {
    return new Date(this._detectedAt);
  }

  public get status(): AnomalyStatus {
    return this._status;
  }

  public get javaServiceExecutionId(): string {
    return this._javaServiceExecutionId;
  }

  public get javaExecutionMetadata(): Readonly<JavaExecutionMetadata> {
    return Object.freeze({ ...this._javaExecutionMetadata });
  }

  public get investigationNotes(): readonly string[] {
    return Object.freeze([...this._investigationNotes]);
  }

  public get remediationSteps(): readonly string[] {
    return Object.freeze([...this._remediationSteps]);
  }

  public get relatedResourceIds(): readonly string[] {
    return Object.freeze([...this._relatedResourceIds]);
  }

  public get createdAt(): Date {
    return new Date(this._createdAt);
  }

  public get updatedAt(): Date {
    return new Date(this._updatedAt);
  }

  /**
   * Calculates the deviation percentage from baseline.
   *
   * @returns Percentage deviation (e.g., 250 for 250% over baseline)
   */
  public deviationPercentage(): number {
    if (this._baseline === 0) {
      return this._observed === 0 ? 0 : 100; // Avoid division by zero
    }
    return ((this._observed - this._baseline) / this._baseline) * 100;
  }

  /**
   * Determines severity level category.
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
   * Converts to plain object for serialization.
   *
   * @returns Plain object representation
   */
  public toPlainObject(): Record<string, unknown> {
    return {
      id: this._id,
      type: this._type,
      severity: this._severity,
      severityCategory: this.severityCategory(),
      baseline: this._baseline,
      observed: this._observed,
      deviationPercentage: this.deviationPercentage(),
      description: this._description,
      detectedAt: this._detectedAt.toISOString(),
      status: this._status,
      javaServiceExecutionId: this._javaServiceExecutionId,
      javaExecutionMetadata: this._javaExecutionMetadata,
      investigationNotes: this._investigationNotes,
      remediationSteps: this._remediationSteps,
      relatedResourceIds: this._relatedResourceIds,
      createdAt: this._createdAt.toISOString(),
      updatedAt: this._updatedAt.toISOString(),
    };
  }
}

/**
 * Factory for creating SecurityAnomaly instances in tests.
 */
export class SecurityAnomalyFactory {
  /**
   * Creates a default SecurityAnomaly for testing.
   *
   * @param overrides Partial overrides for default values
   * @returns SecurityAnomaly instance with sensible defaults
   */
  public static create(
    overrides?: Partial<Parameters<typeof SecurityAnomaly.create>[0]>
  ): SecurityAnomaly {
    const defaults: Parameters<typeof SecurityAnomaly.create>[0] = {
      type: "NETWORK_SPIKE",
      severity: 0.75,
      baseline: 100,
      observed: 350,
      description: "Test anomaly for unit testing",
      javaServiceExecutionId: "test-exec-12345",
      javaExecutionMetadata: {
        executionId: "test-exec-12345",
        algorithm: "ISOLATION_FOREST",
        executedAt: new Date(),
        processingTimeMs: 145,
        confidence: 0.92,
      },
      relatedResourceIds: [],
      ...overrides,
    };

    return SecurityAnomaly.create(defaults);
  }

  /**
   * Creates a critical anomaly for testing.
   *
   * @returns SecurityAnomaly with critical severity
   */
  public static critical(): SecurityAnomaly {
    return this.create({
      severity: 0.95,
      type: "PRIVILEGE_ESCALATION",
      description: "Critical privilege escalation detected",
    });
  }

  /**
   * Creates a low-severity anomaly for testing.
   *
   * @returns SecurityAnomaly with low severity
   */
  public static low(): SecurityAnomaly {
    return this.create({
      severity: 0.15,
      type: "POLICY_VIOLATION",
      description: "Minor policy violation detected",
    });
  }
}
