/**
 * Trust Center Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Compliance, privacy, and trust management
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface TrustCenterService {
  /** Get compliance status */
  getComplianceStatus(tenantId: string): Promise<ComplianceStatus>;
  
  /** Get privacy settings */
  getPrivacySettings(tenantId: string): Promise<PrivacySettings>;
  
  /** Update privacy settings */
  updatePrivacySettings(tenantId: string, settings: PrivacySettings): Promise<PrivacySettings>;
  
  /** Get data retention policies */
  getRetentionPolicies(tenantId: string): Promise<RetentionPolicy[]>;
  
  /** Update retention policy */
  updateRetentionPolicy(policyId: string, policy: Partial<RetentionPolicy>): Promise<RetentionPolicy>;
  
  /** Get audit logs */
  getAuditLogs(options?: AuditLogOptions): Promise<AuditLogResult>;
  
  /** Export audit logs */
  exportAuditLogs(options: AuditLogExportOptions): Promise<Blob>;
  
  /** Get consent records */
  getConsentRecords(tenantId: string): Promise<ConsentRecord[]>;
  
  /** Record consent */
  recordConsent(tenantId: string, userId: string, consentType: string, granted: boolean): Promise<ConsentRecord>;
  
  /** Get security report */
  getSecurityReport(tenantId: string): Promise<SecurityReport>;
  
  /** Get data processing agreements */
  getDataProcessingAgreements(tenantId: string): Promise<DataProcessingAgreement[]>;
}

/** Compliance status */
export interface ComplianceStatus {
  tenantId: string;
  overallStatus: 'compliant' | 'non_compliant' | 'pending_review';
  frameworks: FrameworkCompliance[];
  lastAssessment: string;
  nextAssessmentDue: string;
  issues: ComplianceIssue[];
}

/** Framework compliance */
export interface FrameworkCompliance {
  framework: string;
  version: string;
  status: 'compliant' | 'non_compliant' | 'partial';
  controls: ControlStatus[];
  score: number;
}

/** Control status */
export interface ControlStatus {
  controlId: string;
  name: string;
  status: 'pass' | 'fail' | 'partial' | 'not_applicable';
  evidence?: string;
  lastTested?: string;
}

/** Compliance issue */
export interface ComplianceIssue {
  id: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  framework: string;
  control: string;
  description: string;
  remediation: string;
  dueDate: string;
}

/** Privacy settings */
export interface PrivacySettings {
  tenantId: string;
  dataMinimization: boolean;
  purposeLimitation: boolean;
  storageLimitation: boolean;
  accuracyEnabled: boolean;
  accountabilityEnabled: boolean;
  transparencyEnabled: boolean;
  dpoContact?: string;
  privacyPolicyUrl?: string;
  cookieConsent: boolean;
  analyticsConsent: boolean;
  marketingConsent: boolean;
}

/** Retention policy */
export interface RetentionPolicy {
  id: string;
  tenantId: string;
  name: string;
  dataType: string;
  retentionPeriod: number;
  retentionUnit: 'days' | 'months' | 'years';
  action: 'delete' | 'archive' | 'anonymize';
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Audit log options */
export interface AuditLogOptions {
  tenantId?: string;
  userId?: string;
  action?: string;
  resource?: string;
  startDate?: string;
  endDate?: string;
  severity?: 'info' | 'warning' | 'error' | 'critical';
  page?: number;
  limit?: number;
}

/** Audit log result */
export interface AuditLogResult {
  logs: AuditLogEntry[];
  total: number;
  page: number;
  limit: number;
}

/** Audit log entry */
export interface AuditLogEntry {
  id: string;
  timestamp: string;
  tenantId: string;
  userId: string;
  userName: string;
  action: string;
  resource: string;
  resourceId: string;
  details: Record<string, unknown>;
  ipAddress: string;
  userAgent: string;
  severity: 'info' | 'warning' | 'error' | 'critical';
  success: boolean;
  errorMessage?: string;
}

/** Audit log export options */
export interface AuditLogExportOptions {
  tenantId?: string;
  startDate?: string;
  endDate?: string;
  format: 'csv' | 'json' | 'pdf';
  includeDetails: boolean;
}

/** Consent record */
export interface ConsentRecord {
  id: string;
  tenantId: string;
  userId: string;
  consentType: string;
  granted: boolean;
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  version: string;
  withdrawnAt?: string;
}

/** Security report */
export interface SecurityReport {
  tenantId: string;
  generatedAt: string;
  score: number;
  findings: SecurityFinding[];
  recommendations: SecurityRecommendation[];
}

/** Security finding */
export interface SecurityFinding {
  id: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  category: string;
  title: string;
  description: string;
  remediation: string;
  resources: string[];
}

/** Security recommendation */
export interface SecurityRecommendation {
  id: string;
  priority: number;
  title: string;
  description: string;
  effort: 'low' | 'medium' | 'high';
  impact: 'low' | 'medium' | 'high';
}

/** Data processing agreement */
export interface DataProcessingAgreement {
  id: string;
  tenantId: string;
  processorName: string;
  processorAddress: string;
  processingActivities: string[];
  dataCategories: string[];
  retentionPeriod: string;
  securityMeasures: string[];
  subProcessors: string[];
  signedAt: string;
  validUntil: string;
  status: 'active' | 'expired' | 'terminated';
}
