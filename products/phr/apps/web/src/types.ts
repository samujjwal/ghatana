// Re-export shared PHR DTO contracts from platform
export {
  PatientProfile,
  PatientRecordSummary,
  ConsentGrant,
  AppointmentSummary,
  LabResultSummary,
  MedicationSummary,
  DashboardData,
  SessionContext,
  AuditEvent,
} from '@ghatana/phr-dto';

export interface PhrReleaseReadinessSection {
  label: string;
  status: string;
  runtimeProven: boolean;
  message: string;
  details?: unknown;
}

export interface PhrReleaseReadiness {
  product: 'phr';
  tenantId: string;
  principalId: string;
  role: string;
  environment: string;
  generatedAt: string;
  targetCommitSha: string;
  runtimeTruthBlocked: boolean;
  requiredSections: string[];
  releaseReadiness?: {
    status?: string;
    overallScore?: number;
    blockingIssues?: string[];
    warnings?: string[];
  };
  sections: Record<string, PhrReleaseReadinessSection>;
}

// ─── Audit ────────────────────────────────────────────────────────────────

export interface AuditEvent {
  id: string;
  tenantId: string;
  eventType: string;
  principal: string;
  resourceType: string;
  resourceId: string | null;
  timestamp: string;
  success: boolean;
  details: Record<string, string>;
}

export interface AuditEventsPage {
  events: AuditEvent[];
  total: number;
  page: number;
  pageSize: number;
}

// ─── Consent management ───────────────────────────────────────────────────

export interface ConsentGrantRequest {
  patientId: string;
  recipientId: string;
  purpose: string;
  scope: {
    resourceTypes: string[];
    allDocuments?: boolean;
    specificDocumentIds?: string[];
    actions?: string[];
  };
  expiresAt: string;
}

export interface ConsentRevokeResult {
  grantId: string;
  status: 'REVOKED';
}

// ─── Appointment creation ─────────────────────────────────────────────────

export interface AppointmentRequest {
  specialty: string;
  preferredDate: string;
  notes?: string;
}

export interface AppointmentCreateResult {
  id: string;
  status: 'requested' | 'confirmed' | 'cancelled';
  specialty: string;
  preferredDate: string;
  createdAt: string;
}

// ─── Emergency access ─────────────────────────────────────────────────────

export interface EmergencyAccessRequest {
  patientId: string;
  reason: string;
  clinicianId: string;
}

export interface EmergencyAccessEvent {
  id: string;
  patientId: string;
  clinicianId: string;
  reason: string;
  status: 'PENDING' | 'REVIEWED' | 'EXPIRED';
  accessedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  reviewNote?: string;
}

export interface EmergencyReviewRequest {
  eventId: string;
  reviewNote: string;
  reviewerId: string;
}

// ─── Auth session ─────────────────────────────────────────────────────────

export interface PhrSession {
  principalId: string;
  tenantId: string;
  role: 'patient' | 'caregiver' | 'fchv' | 'clinician' | 'admin';
  name: string;
  expiresAt: string;
}

export interface PhrLoginRequest {
  nationalId: string;
  password: string;
}

// ─── Extended profile ─────────────────────────────────────────────────────────

export interface PatientProfileExtended extends PatientProfile {
  birthDate?: string;
  preferredLanguage?: string;
  facilityId?: string;
  mrn?: string;
  gender?: string;
}

export interface PatientProfileUpdateRequest {
  emergencyContact?: string;
  preferredLanguage?: string;
  facilityId?: string;
}

// ─── Timeline ─────────────────────────────────────────────────────────────────

export interface TimelineEvent {
  id: string;
  date: string;
  type: 'visit' | 'lab' | 'immunization' | 'medication' | 'consent' | 'document';
  title: string;
  summary: string;
  /** ISO datetime of the event; preferred field for display. */
  occurredAt: string;
  /** Short description or additional context for the event. */
  description?: string;
  resourceId?: string;
}

// ─── Conditions ───────────────────────────────────────────────────────────────

export interface ConditionSummary {
  id: string;
  /** Display name of the condition (SNOMED / free text). */
  name: string;
  /** Human-readable display label; aliases `name` for compatibility with ICD-10 wire responses. */
  display: string;
  /** ICD-10 or SNOMED code. */
  code?: string;
  status: 'active' | 'resolved' | 'chronic';
  onsetDate?: string;
  resolvedDate?: string;
  icdCode?: string;
}

// ─── Observations ─────────────────────────────────────────────────────────────

export interface ObservationSummary {
  id: string;
  name: string;
  value: string;
  unit?: string;
  status: 'normal' | 'attention' | 'critical' | 'abnormal' | 'pending';
  recordedAt: string;
  /** ISO date of observation; aliases `recordedAt` for wire response compatibility. */
  effectiveDate: string;
  loincCode?: string;
}

// ─── Immunizations ────────────────────────────────────────────────────────────

export interface ImmunizationSummary {
  id: string;
  vaccine: string;
  date: string;
  /** ISO date of vaccine administration; aliases `date` for wire response compatibility. */
  occurrenceDate: string;
  dose?: string;
  /** Vaccine lot number. */
  lotNumber?: string;
  site?: string;
  cvxCode?: string;
  status?: 'completed' | 'not-done' | 'entered-in-error' | 'due';
}

// ─── Documents ────────────────────────────────────────────────────────────────

export interface DocumentSummary {
  id: string;
  title: string;
  category?: 'lab' | 'referral' | 'discharge' | 'imaging' | 'other';
  uploadedAt: string;
  mimeType?: string;
  sizeKb?: number;
  /** MIME content-type for display (e.g. "application/pdf"). */
  contentType?: string;
  /** OCR pipeline status. */
  ocrStatus?: 'pending' | 'processing' | 'ready' | 'failed';
}

export interface DocumentUploadResult {
  id: string;
  status: 'pending_ocr' | 'processed' | 'failed';
  ocrAvailable: boolean;
}

export interface OcrReviewDocument {
  id: string;
  title: string;
  ocrText: string;
  /** OCR-extracted raw text; aliases `ocrText` for wire response compatibility. */
  extractedText: string;
  correctedText?: string;
  confidence: number;
  status: 'pending_review' | 'confirmed' | 'rejected';
}

export interface DocumentDetail extends DocumentSummary {
  description?: string;
  uploadedBy: string;
  versions?: DocumentVersion[];
  auditLog?: AuditEntry[];
}

export interface DocumentVersion {
  versionId: string;
  versionNumber: number;
  createdAt: string;
  createdBy: string;
  changeNote?: string;
}

export interface AuditEntry {
  id: string;
  action: string;
  timestamp: string;
  performedBy: string;
  details?: Record<string, unknown>;
}

// ─── Notifications ────────────────────────────────────────────────────────────

export interface NotificationSummary {
  id: string;
  type: 'consent_expiry' | 'appointment_reminder' | 'lab_result' | 'emergency_access' | 'system';
  title: string;
  body: string;
  readAt?: string | null;
  createdAt: string;
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export interface PatientRosterEntry {
  id: string;
  name: string;
  mrn: string;
  lastVisit?: string;
  condition?: string;
  alertCount: number;
  /** Patient age in years. */
  age?: number;
  /** Current clinical status. */
  status?: string;
  /** ISO datetime of next scheduled appointment. */
  nextAppointment?: string;
}

// ─── Caregiver ────────────────────────────────────────────────────────────────

export interface DependentEntry {
  id: string;
  name: string;
  relationship: string;
  birthDate?: string;
  alertCount: number;
  /** Approximate age in years derived from birthDate. */
  age?: number;
}

// ─── FCHV ─────────────────────────────────────────────────────────────────────

export interface FchvPatientEntry {
  id: string;
  name: string;
  village: string;
  lastContact?: string;
  riskLevel: 'low' | 'medium' | 'high';
  /** Number of outstanding actions (referrals, follow-ups). */
  pendingActions: number;
}
