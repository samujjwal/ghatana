/**
 * Feature 2.33: Policy-driven Export
 *
 * Provides policy-based export control with:
 * - Field-level redaction based on sensitivity policies
 * - Watermarking for confidential exports
 * - Cryptographic signing for tamper detection
 * - Multi-format support (JSON, CSV, PDF metadata)
 * - Audit trail integration
 *
 * @module exportPolicy
 */

/**
 * Sensitivity levels for data classification
 */
export type SensitivityLevel = 'public' | 'internal' | 'confidential' | 'restricted';

/**
 * Redaction strategies for sensitive fields
 */
export type RedactionStrategy = 'remove' | 'mask' | 'hash' | 'placeholder';

/**
 * Export formats supported by the policy engine
 */
export type ExportFormat = 'json' | 'csv' | 'pdf' | 'markdown';

/**
 * Watermark positioning in exported documents
 */
export type WatermarkPosition = 'header' | 'footer' | 'diagonal' | 'background';

/**
 * Field-level redaction rule
 */
export interface RedactionRule {
  /** Field path (dot-notation for nested fields) */
  fieldPath: string;
  /** Minimum sensitivity level that triggers redaction */
  minSensitivity: SensitivityLevel;
  /** How to redact the field */
  strategy: RedactionStrategy;
  /** Optional custom placeholder text */
  placeholder?: string;
  /** Optional reason for redaction (included in audit log) */
  reason?: string;
}

/**
 * Watermark configuration
 */
export interface WatermarkConfig {
  /** Watermark text template (supports variables: {id}, {timestamp}, {user}) */
  template: string;
  /** Where to place the watermark */
  position: WatermarkPosition;
  /** Opacity (0-1) */
  opacity: number;
  /** Font size for text watermarks */
  fontSize?: number;
  /** Color (hex or rgba) */
  color?: string;
}

/**
 * Signing configuration for tamper detection
 */
export interface SigningConfig {
  /** Algorithm to use (e.g., 'RS256', 'HS256') */
  algorithm: 'RS256' | 'HS256' | 'ES256';
  /** Private key or secret for signing */
  key: string;
  /** Optional key ID for key rotation */
  keyId?: string;
  /** Include full certificate chain */
  includeCertChain?: boolean;
}

/**
 * Export policy definition
 */
export interface ExportPolicy {
  /** Unique policy identifier */
  id: string;
  /** Human-readable policy name */
  name: string;
  /** Description of the policy purpose */
  description?: string;
  /** Sensitivity level this policy applies to */
  sensitivity: SensitivityLevel;
  /** Redaction rules for this policy */
  redactionRules: RedactionRule[];
  /** Optional watermark configuration */
  watermark?: WatermarkConfig;
  /** Optional signing configuration */
  signing?: SigningConfig;
  /** Formats this policy applies to (empty = all formats) */
  applicableFormats?: ExportFormat[];
  /** Whether to include redaction audit trail in export metadata */
  includeAuditTrail?: boolean;
  /** Custom metadata to include in export */
  metadata?: Record<string, unknown>;
}

/**
 * Context for an export operation
 */
export interface ExportContext {
  /** User performing the export */
  userId: string;
  /** Timestamp of export */
  timestamp: number;
  /** Target format */
  format: ExportFormat;
  /** Sensitivity level of the data being exported */
  sensitivity: SensitivityLevel;
  /** Optional export identifier for tracking */
  exportId?: string;
  /** Additional context metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Result of applying redaction rules
 */
export interface RedactionResult {
  /** Redacted data */
  data: unknown;
  /** Fields that were redacted */
  redactedFields: RedactedField[];
  /** Whether any redaction occurred */
  hasRedactions: boolean;
}

/**
 * Information about a redacted field
 */
export interface RedactedField {
  /** Field path that was redacted */
  fieldPath: string;
  /** Strategy used */
  strategy: RedactionStrategy;
  /** Original value type */
  originalType: string;
  /** Reason for redaction */
  reason?: string;
}

/**
 * Watermark application result
 */
export interface WatermarkResult {
  /** Watermarked data/content */
  data: unknown;
  /** Applied watermark text */
  watermarkText: string;
  /** Position where watermark was applied */
  position: WatermarkPosition;
  /** Watermark metadata */
  metadata: {
    appliedAt: number;
    template: string;
    exportId?: string;
  };
}

/**
 * Signature result for tamper detection
 */
export interface SignatureResult {
  /** Original data */
  data: unknown;
  /** Cryptographic signature */
  signature: string;
  /** Algorithm used */
  algorithm: string;
  /** Optional key ID */
  keyId?: string;
  /** Timestamp of signing */
  signedAt: number;
  /** Hash of the signed data */
  dataHash: string;
}

/**
 * Complete export bundle with policy enforcement
 */
export interface SecureExportBundle {
  /** Processed data (redacted, watermarked) */
  data: unknown;
  /** Applied policy information */
  policy: {
    id: string;
    name: string;
    sensitivity: SensitivityLevel;
  };
  /** Export context */
  context: ExportContext;
  /** Redaction summary */
  redactions?: {
    count: number;
    fields: RedactedField[];
  };
  /** Watermark information */
  watermark?: {
    text: string;
    position: WatermarkPosition;
  };
  /** Signature for verification */
  signature?: SignatureResult;
  /** Audit trail entries */
  auditTrail?: AuditEntry[];
  /** Export metadata */
  metadata: {
    exportedAt: number;
    format: ExportFormat;
    version: string;
  };
}

/**
 * Audit entry for export operations
 */
export interface AuditEntry {
  /** Timestamp of the action */
  timestamp: number;
  /** Action type */
  action: 'redact' | 'watermark' | 'sign' | 'export';
  /** User who performed the action */
  userId: string;
  /** Details about the action */
  details: Record<string, unknown>;
  /** Optional policy reference */
  policyId?: string;
}

/**
 * Export policy store state
 */
export interface ExportPolicyStore {
  /** Registered policies by ID */
  policies: Map<string, ExportPolicy>;
  /** Active policy assignments by sensitivity level */
  activePolicies: Map<SensitivityLevel, string>;
  /** Audit log of export operations */
  auditLog: AuditEntry[];
  /** Export statistics */
  statistics: {
    totalExports: number;
    byFormat: Record<ExportFormat, number>;
    bySensitivity: Record<SensitivityLevel, number>;
    redactionCount: number;
  };
}

/**
 * Sensitivity level hierarchy (lower index = less sensitive)
 */
const SENSITIVITY_HIERARCHY: SensitivityLevel[] = ['public', 'internal', 'confidential', 'restricted'];

/**
 * Default redaction placeholder by strategy
 */
const DEFAULT_PLACEHOLDERS: Record<RedactionStrategy, string> = {
  remove: '',
  mask: '***REDACTED***',
  hash: '[HASH]',
  placeholder: '[CLASSIFIED]',
};

/**
 * Creates a new export policy store
 */
export function createExportPolicyStore(): ExportPolicyStore {
  return {
    policies: new Map(),
    activePolicies: new Map(),
    auditLog: [],
    statistics: {
      totalExports: 0,
      byFormat: { json: 0, csv: 0, pdf: 0, markdown: 0 },
      bySensitivity: { public: 0, internal: 0, confidential: 0, restricted: 0 },
      redactionCount: 0,
    },
  };
}

/**
 * Registers a new export policy
 */
export function registerPolicy(store: ExportPolicyStore, policy: ExportPolicy): ExportPolicyStore {
  if (store.policies.has(policy.id)) {
    throw new Error(`Policy with ID '${policy.id}' already exists`);
  }

  return {
    ...store,
    policies: new Map(store.policies).set(policy.id, policy),
  };
}

/**
 * Sets the active policy for a sensitivity level
 */
export function setActivePolicy(
  store: ExportPolicyStore,
  sensitivity: SensitivityLevel,
  policyId: string
): ExportPolicyStore {
  if (!store.policies.has(policyId)) {
    throw new Error(`Policy '${policyId}' not found`);
  }

  const policy = store.policies.get(policyId)!;
  if (policy.sensitivity !== sensitivity) {
    throw new Error(
      `Policy '${policyId}' has sensitivity '${policy.sensitivity}' but trying to activate for '${sensitivity}'`
    );
  }

  return {
    ...store,
    activePolicies: new Map(store.activePolicies).set(sensitivity, policyId),
  };
}

/**
 * Gets the active policy for a sensitivity level
 */
export function getActivePolicy(store: ExportPolicyStore, sensitivity: SensitivityLevel): ExportPolicy | undefined {
  const policyId = store.activePolicies.get(sensitivity);
  return policyId ? store.policies.get(policyId) : undefined;
}

/**
 * Checks if a field should be redacted based on sensitivity
 */
function shouldRedactField(rule: RedactionRule, sensitivity: SensitivityLevel): boolean {
  const ruleLevel = SENSITIVITY_HIERARCHY.indexOf(rule.minSensitivity);
  const currentLevel = SENSITIVITY_HIERARCHY.indexOf(sensitivity);
  return currentLevel >= ruleLevel;
}

/**
 * Gets the value at a field path in an object
 */
function getFieldValue(obj: unknown, path: string): unknown {
  if (typeof obj !== 'object' || obj === null) return undefined;

  const parts = path.split('.');
  let current: unknown = obj;

  for (const part of parts) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[part];
  }

  return current;
}

/**
 * Sets the value at a field path in an object (immutably)
 */
function setFieldValue(obj: unknown, path: string, value: unknown): unknown {
  if (typeof obj !== 'object' || obj === null) return obj;

  const parts = path.split('.');
  const copy = Array.isArray(obj) ? [...obj] : { ...obj };
  let current: Record<string, unknown> = copy as Record<string, unknown>;

  for (let i = 0; i < parts.length - 1; i++) {
    const part = parts[i];
    if (current[part] === null || current[part] === undefined) {
      current[part] = {};
    } else if (typeof current[part] === 'object') {
      current[part] = Array.isArray(current[part]) ? [...current[part]] : { ...current[part] };
    }
    current = current[part] as Record<string, unknown>;
  }

  current[parts[parts.length - 1]] = value;
  return copy;
}

/**
 * Deletes a field at a path in an object (immutably)
 */
function deleteFieldValue(obj: unknown, path: string): unknown {
  if (typeof obj !== 'object' || obj === null) return obj;

  const parts = path.split('.');
  const copy = Array.isArray(obj) ? [...obj] : { ...obj };
  let current: Record<string, unknown> = copy as Record<string, unknown>;
  const parents: unknown[] = [copy];

  for (let i = 0; i < parts.length - 1; i++) {
    const part = parts[i];
    if (current[part] === null || current[part] === undefined) {
      return copy; // Path doesn't exist, nothing to delete
    }
    if (typeof current[part] === 'object') {
      current[part] = Array.isArray(current[part]) ? [...current[part]] : { ...current[part] };
    }
    current = current[part] as Record<string, unknown>;
    parents.push(current);
  }

  delete current[parts[parts.length - 1]];
  return copy;
}

/**
 * Applies a single redaction rule to data
 */
function applyRedactionRule(data: unknown, rule: RedactionRule, sensitivity: SensitivityLevel): {
  data: unknown;
  redacted?: RedactedField;
} {
  if (!shouldRedactField(rule, sensitivity)) {
    return { data };
  }

  const originalValue = getFieldValue(data, rule.fieldPath);
  if (originalValue === undefined) {
    return { data }; // Field doesn't exist, nothing to redact
  }

  const originalType = Array.isArray(originalValue) ? 'array' : typeof originalValue;

  let redactedValue: unknown;
  let redactedData: unknown;
  switch (rule.strategy) {
    case 'remove':
      redactedData = deleteFieldValue(data, rule.fieldPath);
      break;
    case 'mask':
      redactedValue = rule.placeholder || DEFAULT_PLACEHOLDERS.mask;
      redactedData = setFieldValue(data, rule.fieldPath, redactedValue);
      break;
    case 'hash':
      // Simple hash simulation (in production, use crypto.createHash)
      redactedValue = `[HASH:${String(originalValue).length}]`;
      redactedData = setFieldValue(data, rule.fieldPath, redactedValue);
      break;
    case 'placeholder':
      redactedValue = rule.placeholder || DEFAULT_PLACEHOLDERS.placeholder;
      redactedData = setFieldValue(data, rule.fieldPath, redactedValue);
      break;
  }

  return {
    data: redactedData,
    redacted: {
      fieldPath: rule.fieldPath,
      strategy: rule.strategy,
      originalType,
      reason: rule.reason,
    },
  };
}

/**
 * Applies redaction policy to data
 */
export function applyRedaction(data: unknown, policy: ExportPolicy, context: ExportContext): RedactionResult {
  let processedData = data;
  const redactedFields: RedactedField[] = [];

  for (const rule of policy.redactionRules) {
    const result = applyRedactionRule(processedData, rule, context.sensitivity);
    processedData = result.data;
    if (result.redacted) {
      redactedFields.push(result.redacted);
    }
  }

  return {
    data: processedData,
    redactedFields,
    hasRedactions: redactedFields.length > 0,
  };
}

/**
 * Interpolates variables in a watermark template
 */
function interpolateWatermark(template: string, context: ExportContext): string {
  return template
    .replace(/{id}/g, context.exportId || 'N/A')
    .replace(/{timestamp}/g, new Date(context.timestamp).toISOString())
    .replace(/{user}/g, context.userId);
}

/**
 * Applies watermark to exported data
 */
export function applyWatermark(data: unknown, config: WatermarkConfig, context: ExportContext): WatermarkResult {
  const watermarkText = interpolateWatermark(config.template, context);

  // For structured data, add watermark as metadata
  let watermarkedData = data;
  if (typeof data === 'object' && data !== null && !Array.isArray(data)) {
    watermarkedData = {
      ...data,
      __watermark: {
        text: watermarkText,
        position: config.position,
        opacity: config.opacity,
        appliedAt: context.timestamp,
      },
    };
  }

  return {
    data: watermarkedData,
    watermarkText,
    position: config.position,
    metadata: {
      appliedAt: context.timestamp,
      template: config.template,
      exportId: context.exportId,
    },
  };
}

/**
 * Creates a simple hash of data (for demonstration; use crypto in production)
 */
function hashData(data: unknown): string {
  const str = JSON.stringify(data);
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash).toString(36);
}

/**
 * Signs data for tamper detection
 */
export function signData(data: unknown, config: SigningConfig, context: ExportContext): SignatureResult {
  const dataHash = hashData(data);

  // Simplified signature (in production, use crypto.sign with proper keys)
  const signaturePayload = `${config.algorithm}:${dataHash}:${context.timestamp}:${context.userId}`;
  const signature = `SIG_${hashData(signaturePayload)}_${config.keyId || 'default'}`;

  return {
    data,
    signature,
    algorithm: config.algorithm,
    keyId: config.keyId,
    signedAt: context.timestamp,
    dataHash,
  };
}

/**
 * Verifies a signature
 */
export function verifySignature(bundle: SecureExportBundle, config: SigningConfig): boolean {
  if (!bundle.signature) {
    return false;
  }

  // Recreate the expected signature
  const expectedHash = hashData(bundle.data);
  if (bundle.signature.dataHash !== expectedHash) {
    return false;
  }

  const expectedPayload = `${bundle.signature.algorithm}:${expectedHash}:${bundle.signature.signedAt}:${bundle.context.userId}`;
  const expectedSignature = `SIG_${hashData(expectedPayload)}_${bundle.signature.keyId || 'default'}`;

  return bundle.signature.signature === expectedSignature;
}

/**
 * Creates an audit entry
 */
function createAuditEntry(
  action: AuditEntry['action'],
  userId: string,
  details: Record<string, unknown>,
  policyId?: string
): AuditEntry {
  return {
    timestamp: Date.now(),
    action,
    userId,
    details,
    policyId,
  };
}

/**
 * Performs a secure export with full policy enforcement
 */
export function secureExport(
  store: ExportPolicyStore,
  data: unknown,
  context: ExportContext
): { store: ExportPolicyStore; bundle: SecureExportBundle } {
  const policy = getActivePolicy(store, context.sensitivity);
  if (!policy) {
    throw new Error(`No active policy found for sensitivity level '${context.sensitivity}'`);
  }

  // Check if policy applies to this format
  if (policy.applicableFormats && !policy.applicableFormats.includes(context.format)) {
    throw new Error(`Policy '${policy.id}' does not support format '${context.format}'`);
  }

  const auditTrail: AuditEntry[] = [];
  let processedData = data;

  // 1. Apply redaction
  const redactionResult = applyRedaction(processedData, policy, context);
  processedData = redactionResult.data;

  if (redactionResult.hasRedactions) {
    auditTrail.push(
      createAuditEntry(
        'redact',
        context.userId,
        {
          fieldsRedacted: redactionResult.redactedFields.length,
          fields: redactionResult.redactedFields.map((f) => f.fieldPath),
        },
        policy.id
      )
    );
  }

  // 2. Apply watermark if configured
  let watermarkResult: WatermarkResult | undefined;
  if (policy.watermark) {
    watermarkResult = applyWatermark(processedData, policy.watermark, context);
    processedData = watermarkResult.data;

    auditTrail.push(
      createAuditEntry(
        'watermark',
        context.userId,
        {
          watermarkText: watermarkResult.watermarkText,
          position: watermarkResult.position,
        },
        policy.id
      )
    );
  }

  // 3. Sign data if configured
  let signatureResult: SignatureResult | undefined;
  if (policy.signing) {
    signatureResult = signData(processedData, policy.signing, context);

    auditTrail.push(
      createAuditEntry(
        'sign',
        context.userId,
        {
          algorithm: signatureResult.algorithm,
          dataHash: signatureResult.dataHash,
        },
        policy.id
      )
    );
  }

  // 4. Create export audit entry
  auditTrail.push(
    createAuditEntry(
      'export',
      context.userId,
      {
        format: context.format,
        sensitivity: context.sensitivity,
        exportId: context.exportId,
      },
      policy.id
    )
  );

  // 5. Build secure export bundle
  const bundle: SecureExportBundle = {
    data: processedData,
    policy: {
      id: policy.id,
      name: policy.name,
      sensitivity: policy.sensitivity,
    },
    context,
    redactions: redactionResult.hasRedactions
      ? {
          count: redactionResult.redactedFields.length,
          fields: redactionResult.redactedFields,
        }
      : undefined,
    watermark: watermarkResult
      ? {
          text: watermarkResult.watermarkText,
          position: watermarkResult.position,
        }
      : undefined,
    signature: signatureResult,
    auditTrail: policy.includeAuditTrail ? auditTrail : undefined,
    metadata: {
      exportedAt: context.timestamp,
      format: context.format,
      version: '1.0.0',
      ...policy.metadata,
    },
  };

  // 6. Update store statistics and audit log
  const updatedStore: ExportPolicyStore = {
    ...store,
    auditLog: [...store.auditLog, ...auditTrail],
    statistics: {
      totalExports: store.statistics.totalExports + 1,
      byFormat: {
        ...store.statistics.byFormat,
        [context.format]: store.statistics.byFormat[context.format] + 1,
      },
      bySensitivity: {
        ...store.statistics.bySensitivity,
        [context.sensitivity]: store.statistics.bySensitivity[context.sensitivity] + 1,
      },
      redactionCount: store.statistics.redactionCount + redactionResult.redactedFields.length,
    },
  };

  return { store: updatedStore, bundle };
}

/**
 * Gets export statistics
 */
export function getExportStatistics(store: ExportPolicyStore): ExportPolicyStore['statistics'] {
  return store.statistics;
}

/**
 * Gets audit trail entries for a specific export
 */
export function getExportAuditTrail(store: ExportPolicyStore, exportId: string): AuditEntry[] {
  return store.auditLog.filter((entry) => (entry.details.exportId as string) === exportId);
}

/**
 * Gets all policies for a sensitivity level
 */
export function getPoliciesForSensitivity(store: ExportPolicyStore, sensitivity: SensitivityLevel): ExportPolicy[] {
  return Array.from(store.policies.values()).filter((policy) => policy.sensitivity === sensitivity);
}

/**
 * Removes a policy (if not currently active)
 */
export function removePolicy(store: ExportPolicyStore, policyId: string): ExportPolicyStore {
  const policy = store.policies.get(policyId);
  if (!policy) {
    throw new Error(`Policy '${policyId}' not found`);
  }

  // Check if policy is currently active
  const isActive = Array.from(store.activePolicies.values()).includes(policyId);
  if (isActive) {
    throw new Error(`Cannot remove active policy '${policyId}'. Deactivate it first.`);
  }

  const updatedPolicies = new Map(store.policies);
  updatedPolicies.delete(policyId);

  return {
    ...store,
    policies: updatedPolicies,
  };
}

/**
 * Updates an existing policy
 */
export function updatePolicy(store: ExportPolicyStore, policyId: string, updates: Partial<ExportPolicy>): ExportPolicyStore {
  const existingPolicy = store.policies.get(policyId);
  if (!existingPolicy) {
    throw new Error(`Policy '${policyId}' not found`);
  }

  // Prevent ID changes
  if (updates.id && updates.id !== policyId) {
    throw new Error('Cannot change policy ID');
  }

  const updatedPolicy: ExportPolicy = {
    ...existingPolicy,
    ...updates,
    id: policyId, // Ensure ID remains unchanged
  };

  return {
    ...store,
    policies: new Map(store.policies).set(policyId, updatedPolicy),
  };
}

/**
 * Clears audit log entries older than specified days
 */
export function pruneAuditLog(store: ExportPolicyStore, daysToKeep: number): ExportPolicyStore {
  const cutoffTime = Date.now() - daysToKeep * 24 * 60 * 60 * 1000;

  return {
    ...store,
    auditLog: store.auditLog.filter((entry) => entry.timestamp >= cutoffTime),
  };
}
