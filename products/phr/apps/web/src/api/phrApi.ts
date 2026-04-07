import { z } from 'zod';
import { demoDashboard } from '../mockData';
import type { DashboardData } from '../types';

const dashboardSchema = z.object({
  patient: z.object({
    id: z.string(),
    name: z.string(),
    age: z.number(),
    bloodType: z.string(),
    location: z.string(),
    emergencyContact: z.string(),
  }),
  records: z.array(z.object({
    id: z.string(),
    title: z.string(),
    category: z.enum(['visit', 'lab', 'immunization', 'medication']),
    updatedAt: z.string(),
    resourceType: z.string(),
    fhirJson: z.string(),
  })),
  consents: z.array(z.object({
    id: z.string(),
    recipient: z.string(),
    purpose: z.string(),
    status: z.enum(['active', 'expiring', 'revoked']),
    expiresAt: z.string(),
  })),
  appointments: z.array(z.object({
    id: z.string(),
    provider: z.string(),
    specialty: z.string(),
    startsAt: z.string(),
    location: z.string(),
  })),
  labs: z.array(z.object({
    id: z.string(),
    name: z.string(),
    status: z.enum(['normal', 'attention']),
    value: z.string(),
    collectedAt: z.string(),
  })),
  medications: z.array(z.object({
    id: z.string(),
    medication: z.string(),
    dosage: z.string(),
    schedule: z.string(),
    adherence: z.number(),
  })),
});

export async function fetchDashboardData(): Promise<DashboardData> {
  return dashboardSchema.parse(demoDashboard);
}

export async function exportPatientBundle(): Promise<string> {
  return JSON.stringify({
    status: 'queued',
    message: 'Patient summary prepared for Nepal HIE submission',
  });
}