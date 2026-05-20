import type { MobileDashboard } from '../types';

export const demoDashboard: MobileDashboard = {
  patient: {
    id: 'patient-nepal-001',
    name: 'Aarati Shrestha',
    age: 34,
    bloodType: 'O+',
    district: 'Kathmandu',
  },
  records: [
    {
      id: 'mobile-record-001',
      title: 'Diabetes review note',
      summary: 'Clinician recommends tighter follow-up and updated diet counseling.',
      fhirPreview: '{"resourceType":"Observation","code":{"text":"HbA1c"}}',
    },
    {
      id: 'mobile-record-002',
      title: 'Medication refill',
      summary: 'Metformin refill queued for pharmacy pickup.',
      fhirPreview: '{"resourceType":"MedicationRequest","status":"active"}',
    },
  ],
  consents: [
    {
      id: 'mobile-consent-001',
      grantee: 'Emergency responder network',
      purpose: 'Emergency treatment view',
      active: true,
    },
    {
      id: 'mobile-consent-002',
      grantee: 'National lab exchange',
      purpose: 'Result synchronization',
      active: true,
    },
  ],
  notifications: [
    {
      id: 'mobile-note-001',
      title: 'Lab result available',
      detail: 'HbA1c results were synchronized from Patan Hospital Lab.',
    },
    {
      id: 'mobile-note-002',
      title: 'Consent expires soon',
      detail: 'Renew your lab exchange consent before 15 April.',
    },
  ],
};
