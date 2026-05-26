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
  category: 'visit' | 'lab' | 'immunization' | 'medication';
  updatedAt: string;
  resourceType: string;
  fhirJson: string;
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
  provider: string;
  specialty: string;
  startsAt: string;
  location: string;
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
  schedule: string;
  adherence: number;
}

export interface DashboardData {
  patient: PatientProfile;
  records: PatientRecordSummary[];
  consents: ConsentGrant[];
  appointments: AppointmentSummary[];
  labs: LabResultSummary[];
  medications: MedicationSummary[];
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
  granteeId: string;
  purpose: string;
  resourceTypes: string[];
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
  role: 'patient' | 'caregiver' | 'clinician' | 'admin';
  name: string;
  expiresAt: string;
}

export interface PhrLoginRequest {
  nationalId: string;
  password: string;
}
