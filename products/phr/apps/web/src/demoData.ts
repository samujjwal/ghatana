import type {
  AppointmentSummary,
  ConsentGrant,
  DashboardData,
  LabResultSummary,
  MedicationSummary,
  PatientProfile,
  PatientRecordSummary,
} from './types';

const patient: PatientProfile = {
  id: 'patient-nepal-001',
  name: 'Aarati Shrestha',
  age: 34,
  bloodType: 'O+',
  location: 'Kathmandu',
  emergencyContact: 'Suman Shrestha · +977-9812345678',
};

const records: PatientRecordSummary[] = [
  {
    id: 'record-visit-001',
    title: 'Emergency department discharge summary',
    category: 'visit',
    updatedAt: '2026-04-05T09:20:00Z',
    resourceType: 'Patient',
    fhirJson: JSON.stringify({
      resourceType: 'Patient',
      id: patient.id,
      name: [{ text: patient.name }],
      address: [{ city: patient.location }],
    }, null, 2),
  },
  {
    id: 'record-lab-001',
    title: 'HbA1c trend',
    category: 'lab',
    updatedAt: '2026-04-04T06:00:00Z',
    resourceType: 'Observation',
    fhirJson: JSON.stringify({
      resourceType: 'Observation',
      id: 'obs-001',
      subject: { reference: `Patient/${patient.id}` },
      code: { text: 'HbA1c' },
      valueQuantity: { value: 6.8, unit: '%' },
    }, null, 2),
  },
  {
    id: 'record-med-001',
    title: 'Metformin prescription',
    category: 'medication',
    updatedAt: '2026-04-03T12:30:00Z',
    resourceType: 'MedicationRequest',
    fhirJson: JSON.stringify({
      resourceType: 'MedicationRequest',
      id: 'med-001',
      subject: { reference: `Patient/${patient.id}` },
      medicationCodeableConcept: { text: 'Metformin 500mg' },
      dosageInstruction: [{ text: '1 tablet twice daily' }],
    }, null, 2),
  },
];

const consents: ConsentGrant[] = [
  {
    id: 'consent-001',
    recipient: 'Grande International Hospital',
    purpose: 'Specialist follow-up consultation',
    status: 'active',
    expiresAt: '2026-06-01',
  },
  {
    id: 'consent-002',
    recipient: 'Patan Hospital Lab',
    purpose: 'Lab result synchronization',
    status: 'expiring',
    expiresAt: '2026-04-15',
  },
];

const appointments: AppointmentSummary[] = [
  {
    id: 'appt-001',
    provider: 'Dr. Nisha Sharma',
    specialty: 'Endocrinology',
    startsAt: '2026-04-10T10:30:00+05:45',
    location: 'Lalitpur Community Clinic',
  },
  {
    id: 'appt-002',
    provider: 'Dr. Ramesh Basnet',
    specialty: 'Primary care',
    startsAt: '2026-04-18T14:00:00+05:45',
    location: 'Telemedicine follow-up',
  },
];

const labs: LabResultSummary[] = [
  {
    id: 'lab-001',
    name: 'HbA1c',
    status: 'attention',
    value: '6.8 %',
    collectedAt: '2026-04-04',
  },
  {
    id: 'lab-002',
    name: 'Creatinine',
    status: 'normal',
    value: '0.9 mg/dL',
    collectedAt: '2026-04-01',
  },
];

const medications: MedicationSummary[] = [
  {
    id: 'med-001',
    medication: 'Metformin',
    dosage: '500mg',
    schedule: '08:00 / 20:00',
    adherence: 0.92,
  },
  {
    id: 'med-002',
    medication: 'Vitamin D3',
    dosage: '1000 IU',
    schedule: '09:00',
    adherence: 0.98,
  },
];

export const demoDashboard: DashboardData = {
  patient,
  records,
  consents,
  appointments,
  labs,
  medications,
};
