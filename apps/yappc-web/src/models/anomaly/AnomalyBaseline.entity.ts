/**
 * Immutable entity representing baseline metrics for anomaly detection.
 *
 * <p><b>Purpose</b><br>
 * Stores baseline values (expected normal behavior) calculated by Java
 * BaselineCalculator for use in anomaly scoring. Baselines are computed
 * from historical data and updated periodically.
 *
 * <p><b>Persistence</b><br>
 * PostgreSQL table with indexes on metricType and resourceId for fast
 * baseline lookups during anomaly scoring.
 *
 * <p><b>Update Strategy</b><br>
 * 1. Initial baseline: Computed from first N days of historical data
 * 2. Periodic updates: Weekly/daily refresh via BaselineCalculator
 * 3. Adaptive: Automatically adjusts with seasonal patterns detected
 * 4. Versioning: Previous baseline retained for comparison
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const baseline = AnomalyBaseline.create({
 *   metricType: "network_traffic_bytes",
 *   resourceId: "subnet-12345",
 *   baselineValue: 1000000, // 1MB/sec
 *   threshold: 3500000,     // 3.5MB/sec threshold for anomaly
 *   confidenceInterval: 0.95,
 *   calculatedAt: new Date()
 * });
 *
 * // Check if value is anomalous
 * const isAnomaly = value > baseline.threshold;
 * }</pre>
 *
 * @doc.type entity
 * @doc.purpose Stores baseline metrics for anomaly detection thresholds
 * @doc.layer product
 * @doc.pattern Entity
 */

/**
 * Type of metric being baselined.
 */
export type MetricType =
  | "network_traffic_bytes"
  | "network_connections"
  | "cpu_utilization"
  | "memory_utilization"
  | "disk_io_rate"
  | "process_count"
  | "failed_logins"
  | "privilege_escalations"
  | "api_request_rate"
  | "data_transfer_bytes"
  | "custom";

/**
 * Status of baseline calculation.
 */
export type BaselineStatus =
  | "CALCULATING"
  | "ACTIVE"
  | "STALE"
  | "INVALIDATED";

/**
 * Result of baseline validation.
 */
export interface BaselineValidationResult {
  readonly isValid: boolean;
  readonly errors: readonly string[];
}

/**
 * Immutable baseline metric entity.
 */
export class AnomalyBaseline {
  private readonly _id: string;
  private readonly _metricType: MetricType;
  private readonly _resourceId: string;
  private readonly _baselineValue: number;
  private readonly _threshold: number;
  private readonly _standardDeviation: number;
  private readonly _confidenceInterval: number; // 0.0-1.0, typically 0.95
  private readonly _status: BaselineStatus;
  private readonly _calculatedAt: Date;
  private readonly _validUntil: Date; // When baseline should be recalculated
  private readonly _previousBaseline: number | null; // For comparison
  private readonly _dataPointsUsed: number; // How many data points used in calculation
  private readonly _javaServiceExecutionId: string; // Reference to Java BaselineCalculator execution
  private readonly _createdAt: Date;
  private readonly _updatedAt: Date;

  /**
   * Creates a new AnomalyBaseline instance.
   *
   * @param id Unique identifier
   * @param metricType Type of metric being baselined
   * @param resourceId ID of resource (e.g., subnet, instance, process)
   * @param baselineValue Expected normal value
   * @param threshold Upper threshold for anomaly detection
   * @param standardDeviation Statistical standard deviation
   * @param confidenceInterval Confidence level (typically 0.95)
   * @param status Current baseline status
   * @param calculatedAt When baseline was calculated
   * @param validUntil When baseline should be recalculated
   * @param previousBaseline Previous baseline value for trend
   * @param dataPointsUsed Number of data points in calculation
   * @param javaServiceExecutionId Reference to Java service execution
   * @param createdAt When record was created
   * @param updatedAt When record was last updated
   */
  private constructor(
    id: string,
    metricType: MetricType,
    resourceId: string,
    baselineValue: number,
    threshold: number,
    standardDeviation: number,
    confidenceInterval: number,
    status: BaselineStatus,
    calculatedAt: Date,
    validUntil: Date,
    previousBaseline: number | null,
    dataPointsUsed: number,
    javaServiceExecutionId: string,
    createdAt: Date,
    updatedAt: Date
  ) {
    this._id = id;
    this._metricType = metricType;
    this._resourceId = resourceId;
    this._baselineValue = baselineValue;
    this._threshold = threshold;
    this._standardDeviation = standardDeviation;
    this._confidenceInterval = confidenceInterval;
    this._status = status;
    this._calculatedAt = calculatedAt;
    this._validUntil = validUntil;
    this._previousBaseline = previousBaseline;
    this._dataPointsUsed = dataPointsUsed;
    this._javaServiceExecutionId = javaServiceExecutionId;
    this._createdAt = createdAt;
    this._updatedAt = updatedAt;
  }

  /**
   * Creates a new AnomalyBaseline from creation parameters.
   *
   * @param params Creation parameters
   * @returns New AnomalyBaseline instance
   * @throws Error if validation fails
   */
  public static create(params: {
    readonly metricType: MetricType;
    readonly resourceId: string;
    readonly baselineValue: number;
    readonly threshold: number;
    readonly standardDeviation?: number;
    readonly confidenceInterval?: number;
    readonly calculatedAt?: Date;
    readonly validityDays?: number; // Days until recalculation needed (default 7)
    readonly javaServiceExecutionId: string;
    readonly dataPointsUsed?: number;
    readonly previousBaseline?: number;
  }): AnomalyBaseline {
    const id = `baseline-${params.metricType}-${params.resourceId}`;
    const now = new Date();
    const validityDays = params.validityDays ?? 7;
    const validUntil = new Date(now.getTime() + validityDays * 24 * 60 * 60 * 1000);

    const baseline = new AnomalyBaseline(
      id,
      params.metricType,
      params.resourceId,
      params.baselineValue,
      params.threshold,
      params.standardDeviation ?? 0,
      params.confidenceInterval ?? 0.95,
      "ACTIVE",
      params.calculatedAt ?? now,
      validUntil,
      params.previousBaseline ?? null,
      params.dataPointsUsed ?? 1000,
      params.javaServiceExecutionId,
      now,
      now
    );

    // Validate on creation
    const validation = baseline.validate();
    if (!validation.isValid) {
      throw new Error(`Validation failed: ${validation.errors.join(", ")}`);
    }

    return baseline;
  }

  /**
   * Validates this baseline's state.
   *
   * @returns BaselineValidationResult with isValid flag and errors
   */
  public validate(): BaselineValidationResult {
    const errors: string[] = [];

    // Metric type validation
    const validTypes: MetricType[] = [
      "network_traffic_bytes",
      "network_connections",
      "cpu_utilization",
      "memory_utilization",
      "disk_io_rate",
      "process_count",
      "failed_logins",
      "privilege_escalations",
      "api_request_rate",
      "data_transfer_bytes",
      "custom",
    ];
    if (!validTypes.includes(this._metricType)) {
      errors.push(`Invalid metric type: ${this._metricType}`);
    }

    // Resource ID validation
    if (!this._resourceId || this._resourceId.trim().length === 0) {
      errors.push("Resource ID cannot be empty");
    }

    // Baseline value validation
    if (this._baselineValue < 0) {
      errors.push("Baseline value cannot be negative");
    }

    // Threshold validation
    if (this._threshold < 0) {
      errors.push("Threshold cannot be negative");
    }
    if (this._threshold < this._baselineValue) {
      errors.push("Threshold should be >= baseline value");
    }

    // Standard deviation validation
    if (this._standardDeviation < 0) {
      errors.push("Standard deviation cannot be negative");
    }

    // Confidence interval validation
    if (this._confidenceInterval <= 0 || this._confidenceInterval > 1) {
      errors.push("Confidence interval must be between 0 and 1");
    }

    // Data points validation
    if (this._dataPointsUsed < 100) {
      errors.push("At least 100 data points required for reliable baseline");
    }

    return {
      isValid: errors.length === 0,
      errors: errors as readonly string[],
    };
  }

  /**
   * Checks if baseline is stale and needs recalculation.
   *
   * @returns true if validUntil has passed
   */
  public isStale(): boolean {
    return new Date() > this._validUntil;
  }

  /**
   * Checks if given value is anomalous (above threshold).
   *
   * @param value Value to check
   * @returns true if value > threshold
   */
  public isAnomaly(value: number): boolean {
    return value > this._threshold;
  }

  /**
   * Calculates anomaly score (deviation from baseline in standard deviations).
   *
   * @param value Observed value
   * @returns Score: 0 if <= baseline, increases with deviation
   */
  public calculateAnomalyScore(value: number): number {
    if (this._standardDeviation === 0) {
      return value > this._baselineValue ? 1 : 0;
    }

    // Z-score: how many standard deviations away from baseline
    const deviation = value - this._baselineValue;
    const zScore = deviation / this._standardDeviation;

    // Clamp to 0-1 range
    return Math.max(0, Math.min(1, zScore / 3)); // 3 sigma = 1.0
  }

  /**
   * Returns margin of safety (how much above baseline before anomaly).
   *
   * @returns Absolute margin value
   */
  public marginOfSafety(): number {
    return this._threshold - this._baselineValue;
  }

  /**
   * Returns margin of safety as percentage above baseline.
   *
   * @returns Percentage (e.g., 250 = 250% above baseline)
   */
  public marginOfSafetyPercent(): number {
    if (this._baselineValue === 0) return 0;
    return ((this._threshold - this._baselineValue) / this._baselineValue) * 100;
  }

  /**
   * Creates updated baseline (due to immutability).
   *
   * @param newBaselineValue New baseline value
   * @param newThreshold New threshold
   * @returns New AnomalyBaseline with updated values
   */
  public updateBaseline(
    newBaselineValue: number,
    newThreshold: number
  ): AnomalyBaseline {
    return new AnomalyBaseline(
      this._id,
      this._metricType,
      this._resourceId,
      newBaselineValue,
      newThreshold,
      this._standardDeviation,
      this._confidenceInterval,
      "ACTIVE",
      new Date(),
      new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
      this._baselineValue, // Keep old as previous
      this._dataPointsUsed,
      this._javaServiceExecutionId,
      this._createdAt,
      new Date()
    );
  }

  /**
   * Marks baseline as invalidated (needs recalculation).
   *
   * @returns New AnomalyBaseline with status INVALIDATED
   */
  public invalidate(): AnomalyBaseline {
    return new AnomalyBaseline(
      this._id,
      this._metricType,
      this._resourceId,
      this._baselineValue,
      this._threshold,
      this._standardDeviation,
      this._confidenceInterval,
      "INVALIDATED",
      this._calculatedAt,
      this._validUntil,
      this._previousBaseline,
      this._dataPointsUsed,
      this._javaServiceExecutionId,
      this._createdAt,
      new Date()
    );
  }

  // Getters (readonly properties)

  public get id(): string {
    return this._id;
  }

  public get metricType(): MetricType {
    return this._metricType;
  }

  public get resourceId(): string {
    return this._resourceId;
  }

  public get baselineValue(): number {
    return this._baselineValue;
  }

  public get threshold(): number {
    return this._threshold;
  }

  public get standardDeviation(): number {
    return this._standardDeviation;
  }

  public get confidenceInterval(): number {
    return this._confidenceInterval;
  }

  public get status(): BaselineStatus {
    return this._status;
  }

  public get calculatedAt(): Date {
    return new Date(this._calculatedAt);
  }

  public get validUntil(): Date {
    return new Date(this._validUntil);
  }

  public get previousBaseline(): number | null {
    return this._previousBaseline;
  }

  public get dataPointsUsed(): number {
    return this._dataPointsUsed;
  }

  public get javaServiceExecutionId(): string {
    return this._javaServiceExecutionId;
  }

  public get createdAt(): Date {
    return new Date(this._createdAt);
  }

  public get updatedAt(): Date {
    return new Date(this._updatedAt);
  }

  /**
   * Converts to plain object for serialization.
   *
   * @returns Plain object representation
   */
  public toPlainObject(): Record<string, unknown> {
    return {
      id: this._id,
      metricType: this._metricType,
      resourceId: this._resourceId,
      baselineValue: this._baselineValue,
      threshold: this._threshold,
      standardDeviation: this._standardDeviation,
      confidenceInterval: this._confidenceInterval,
      status: this._status,
      isStale: this.isStale(),
      calculatedAt: this._calculatedAt.toISOString(),
      validUntil: this._validUntil.toISOString(),
      previousBaseline: this._previousBaseline,
      marginOfSafety: this.marginOfSafety(),
      marginOfSafetyPercent: this.marginOfSafetyPercent(),
      dataPointsUsed: this._dataPointsUsed,
      javaServiceExecutionId: this._javaServiceExecutionId,
      createdAt: this._createdAt.toISOString(),
      updatedAt: this._updatedAt.toISOString(),
    };
  }
}

/**
 * Factory for creating AnomalyBaseline instances in tests.
 */
export class AnomalyBaselineFactory {
  /**
   * Creates a default AnomalyBaseline for testing.
   *
   * @param overrides Partial overrides for default values
   * @returns AnomalyBaseline with sensible defaults
   */
  public static create(
    overrides?: Partial<Parameters<typeof AnomalyBaseline.create>[0]>
  ): AnomalyBaseline {
    const defaults: Parameters<typeof AnomalyBaseline.create>[0] = {
      metricType: "network_traffic_bytes",
      resourceId: "test-resource-1",
      baselineValue: 100,
      threshold: 350,
      standardDeviation: 50,
      confidenceInterval: 0.95,
      javaServiceExecutionId: "baseline-exec-test",
      dataPointsUsed: 1000,
      ...overrides,
    };

    return AnomalyBaseline.create(defaults);
  }

  /**
   * Creates baseline for CPU utilization.
   *
   * @returns AnomalyBaseline for CPU metric
   */
  public static cpuUtilization(): AnomalyBaseline {
    return this.create({
      metricType: "cpu_utilization",
      baselineValue: 45, // 45% normal
      threshold: 85, // Alert at 85%
      standardDeviation: 15,
    });
  }

  /**
   * Creates baseline for network traffic.
   *
   * @returns AnomalyBaseline for network metric
   */
  public static networkTraffic(): AnomalyBaseline {
    return this.create({
      metricType: "network_traffic_bytes",
      baselineValue: 1000000, // 1MB/sec
      threshold: 5000000, // 5MB/sec alert
      standardDeviation: 500000,
    });
  }
}
