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

export interface MobileOfflineCacheStatus {
  lastSyncAt: number | null;
  isOffline: boolean;
  isStale: boolean;
}

export interface MobileEmergencyData {
  patientName: string;
  bloodType: string;
  allergies: string[];
  medications: string[];
  emergencyContact: string;
}

// Auth session

export interface MobileSession {
  principalId: string;
  tenantId: string;
  role: "patient" | "caregiver" | "fchv" | "clinician" | "admin";
  name: string;
  expiresAt: string;
  persona?: string;
  tier?: string;
  facilityId?: string;
  correlationId?: string;
}
