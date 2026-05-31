// Re-export shared PHR DTO contracts from platform
// Note: Using local definitions due to workspace linking issues with @ghatana/phr-dto
export interface PatientProfile {
  id: string;
  name: string;
  age: number;
  bloodType: string;
  location: string;
  emergencyContact: string;
}

export interface PatientRecordSummary {
  id: string;
  title: string;
  category: string;
  updatedAt: string;
  resourceType: string;
  fhirJson?: string;
  redacted?: boolean;
  provenance?: Record<string, unknown>;
}

export interface PatientRecordAccessAudit {
  accessedAt: string;
  accessedBy: string;
  accessRole?: string;
  correlationId?: string;
  policyReason?: string;
  requiresAudit?: boolean;
}

export interface PatientRecordDetail {
  record: PatientRecordSummary;
  fhirJson: string;
  accessAudit: PatientRecordAccessAudit;
}

export interface ConsentGrant {
  id: string;
  recipient: string;
  purpose: string;
  status: 'active' | 'expiring' | 'revoked';
  expiresAt: string;
}

export interface AppointmentSummary {
  id: string;
  appointmentId?: string;
  patientId?: string;
  provider: string;
  providerId?: string;
  specialty: string;
  startsAt: string;
  scheduledTime?: string;
  durationMinutes?: number;
  appointmentType?: string;
  slotId?: string;
  location: string;
  status?: 'requested' | 'confirmed' | 'completed' | 'cancelled';
  reminderSent?: boolean;
}

export interface LabResultSummary {
  id: string;
  name: string;
  status: 'normal' | 'attention';
  value: string;
  collectedAt: string;
}

export interface MedicationSummary {
  id: string;
  medication: string;
  dosage: string;
  schedule?: string;
  adherence?: number;
  route?: string | null;
  routeSource?: string;
  frequency?: string;
  status?: 'active' | 'history' | 'stopped';
  prescriberId?: string;
  warnings?: string[];
  interactions?: string[];
  prescribedAt?: string;
  startDate?: string;
  expiresAt?: string;
  endDate?: string;
  refillsRemaining?: number;
  adherenceSource?: string;
  adherenceStatus?: {
    measured: boolean;
    source: string;
  };
}

export interface MedicationHistoryEntry {
  date: string;
  action: string;
}

export interface MedicationDetail extends MedicationSummary {
  interactions?: string[];
  warnings?: string[];
  history?: MedicationHistoryEntry[];
}

export interface DashboardData {
  tenantId: string;
  principalId: string;
  role: string;
  correlationId: string;
  profileSummary: {
    name: string;
    email?: string | null;
    providerId?: string | null;
    active: boolean;
  };
  nextAppointment: {
    appointmentId: string;
    scheduledTime: string;
    provider: string;
    type: string;
  } | null;
  medications: {
    activeCount: number;
    adherenceAlert: boolean;
  };
  recentObservations: {
    count: number;
    hasCritical: boolean;
  };
  activeConditions: {
    count: number;
    hasChronic: boolean;
  };
  documents: {
    totalCount: number;
    pendingOcr: number;
  };
  accessAlerts: {
    expiringConsents: number;
    emergencyAccessPending: boolean;
  };
  generatedAt: string;
}

export interface SessionContext {
  tenantId: string;
  principalId: string;
  role: 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';
  persona?: string;
  tier?: string;
  facilityId?: string;
  correlationId?: string;
  idempotencyKey?: string;
}

export interface AuditEvent {
  id: string;
  tenantId: string;
  eventType: string;
  principal: string;
  resourceType: string;
  resourceId: string | null;
  timestamp: string;
  success: boolean;
  details?: Record<string, unknown>;
}

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
  persona?: string;
  tier?: string;
  facilityId?: string;
  correlationId?: string;
}

export interface PhrLoginRequest {
  nationalId: string;
  password: string;
}

// ─── Extended profile ─────────────────────────────────────────────────────────

export interface PatientProfileExtended {
  id: string;
  name: string;
  age: number;
  bloodType: string;
  location: string;
  emergencyContact: string;
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
  vaccineName?: string;
  date: string;
  /** ISO date of vaccine administration; aliases `date` for wire response compatibility. */
  occurrenceDate: string;
  dose?: string;
  doseNumber?: number;
  /** Vaccine lot number. */
  lotNumber?: string;
  site?: string;
  cvxCode?: string;
  route?: string;
  seriesName?: string;
  status?: 'completed' | 'not-done' | 'entered-in-error' | 'due';
  source?: {
    system: string;
    administeredBy?: string;
  };
  nextDue?: {
    id: string;
    vaccine: string;
    vaccineName?: string;
    cvxCode?: string;
    seriesName?: string;
    doseNumber?: number;
    dueDate: string;
    status: string;
  };
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
  provenance?: Record<string, unknown>;
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
