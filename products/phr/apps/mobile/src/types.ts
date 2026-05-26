export interface MobilePatientProfile {
  id: string;
  name: string;
  age: number;
  bloodType: string;
  district: string;
}

export interface MobileRecord {
  id: string;
  title: string;
  summary: string;
  fhirPreview: string;
}

export interface MobileConsent {
  id: string;
  grantee: string;
  purpose: string;
  active: boolean;
}

export interface MobileNotificationItem {
  id: string;
  title: string;
  detail: string;
}

export interface MobileDashboard {
  patient: MobilePatientProfile;
  records: MobileRecord[];
  consents: MobileConsent[];
  notifications: MobileNotificationItem[];
}

// ─── Auth session ─────────────────────────────────────────────────────────

export interface MobileSession {
  principalId: string;
  tenantId: string;
  role: 'patient' | 'caregiver' | 'clinician' | 'admin';
  name: string;
  expiresAt: string;
}