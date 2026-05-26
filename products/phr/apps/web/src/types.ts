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
