/**
 * Zod schemas for PHR DTO validation
 *
 * Provides runtime validation for all PHR API responses using Zod.
 * These schemas ensure data integrity and type safety at API boundaries.
 *
 * @doc.type module
 * @doc.purpose Runtime validation schemas for PHR API responses
 * @doc.layer platform
 */

import { z } from 'zod';

/**
 * Patient profile schema
 */
export const PatientProfileSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  age: z.number().int().min(0).max(150),
  bloodType: z.string().min(1),
  location: z.string().min(1),
  emergencyContact: z.string().min(1),
});

/**
 * Mobile patient profile schema (subset)
 */
export const MobilePatientProfileSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  age: z.number().int().min(0).max(150),
  bloodType: z.string().min(1),
  district: z.string().min(1),
});

/**
 * Consent grant schema
 */
export const ConsentGrantSchema = z.object({
  id: z.string().min(1),
  recipient: z.string().min(1),
  purpose: z.string().min(1),
  status: z.enum(['active', 'expiring', 'revoked']),
  expiresAt: z.string().datetime(),
});

/**
 * Mobile consent schema (simplified)
 */
export const MobileConsentSchema = z.object({
  id: z.string().min(1),
  grantee: z.string().min(1),
  purpose: z.string().min(1),
  active: z.boolean(),
});

/**
 * Patient record summary schema
 */
export const PatientRecordSummarySchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  category: z.string().min(1),
  updatedAt: z.string().datetime(),
  resourceType: z.string().min(1),
  fhirJson: z.string().optional(),
  redacted: z.boolean().optional(),
  provenance: z.record(z.unknown()).optional(),
});

/**
 * Mobile record schema (simplified)
 */
export const MobileRecordSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  summary: z.string().min(1),
  fhirPreview: z.string().min(1),
});

/**
 * Notification item schema
 */
export const NotificationItemSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  detail: z.string().min(1),
  timestamp: z.string().datetime().optional(),
  read: z.boolean().optional(),
});

/**
 * Mobile notification schema (simplified)
 */
export const MobileNotificationItemSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  detail: z.string().min(1),
});

/**
 * Appointment summary schema
 */
export const AppointmentSummarySchema = z.object({
  id: z.string().min(1),
  provider: z.string().min(1),
  specialty: z.string().min(1),
  startsAt: z.string().datetime(),
  location: z.string().min(1),
  status: z.enum(['requested', 'confirmed', 'completed', 'cancelled']).optional(),
  reminderSent: z.boolean().optional(),
});

/**
 * Lab result summary schema
 */
export const LabResultSummarySchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  status: z.enum(['normal', 'attention']),
  value: z.string().min(1),
  collectedAt: z.string().datetime(),
});

/**
 * Medication summary schema
 */
export const MedicationSummarySchema = z.object({
  id: z.string().min(1),
  medication: z.string().min(1),
  dosage: z.string().min(1),
  schedule: z.string().min(1),
  adherence: z.number().min(0).max(100),
  status: z.enum(['active', 'history', 'stopped']).optional(),
});

/**
 * Dashboard data schema
 */
export const DashboardDataSchema = z.object({
  patient: PatientProfileSchema,
  records: z.array(PatientRecordSummarySchema),
  consents: z.array(ConsentGrantSchema),
  appointments: z.array(AppointmentSummarySchema),
  labs: z.array(LabResultSummarySchema),
  medications: z.array(MedicationSummarySchema),
});

/**
 * Mobile dashboard schema (simplified)
 */
export const MobileDashboardSchema = z.object({
  patient: MobilePatientProfileSchema,
  records: z.array(MobileRecordSchema),
  consents: z.array(MobileConsentSchema),
  notifications: z.array(MobileNotificationItemSchema),
});

/**
 * Emergency data schema for mobile
 */
export const MobileEmergencyDataSchema = z.object({
  patientName: z.string().min(1),
  bloodType: z.string().min(1),
  allergies: z.array(z.string().min(1)),
  medications: z.array(z.string().min(1)),
  emergencyContact: z.string().min(1),
});

/**
 * Session context schema
 */
export const SessionContextSchema = z.object({
  tenantId: z.string().min(1),
  principalId: z.string().min(1),
  role: z.enum(['patient', 'caregiver', 'clinician', 'admin', 'fchv']),
  persona: z.string().optional(),
  tier: z.string().optional(),
  facilityId: z.string().optional(),
  correlationId: z.string().optional(),
  idempotencyKey: z.string().optional(),
});

/**
 * Mobile session schema (simplified)
 */
export const MobileSessionSchema = z.object({
  principalId: z.string().min(1),
  tenantId: z.string().min(1),
  role: z.enum(['patient', 'caregiver', 'fchv', 'clinician', 'admin']),
  name: z.string().min(1),
  expiresAt: z.string().datetime(),
  persona: z.string().optional(),
  tier: z.string().optional(),
});

/**
 * Audit event schema
 */
export const AuditEventSchema = z.object({
  id: z.string().min(1),
  tenantId: z.string().min(1),
  eventType: z.string().min(1),
  principal: z.string().min(1),
  resourceType: z.string().min(1),
  resourceId: z.string().nullable(),
  timestamp: z.string().datetime(),
  details: z.record(z.unknown()).optional(),
});

/**
 * Type inference helpers
 */
export type PatientProfile = z.infer<typeof PatientProfileSchema>;
export type MobilePatientProfile = z.infer<typeof MobilePatientProfileSchema>;
export type ConsentGrant = z.infer<typeof ConsentGrantSchema>;
export type MobileConsent = z.infer<typeof MobileConsentSchema>;
export type PatientRecordSummary = z.infer<typeof PatientRecordSummarySchema>;
export type MobileRecord = z.infer<typeof MobileRecordSchema>;
export type NotificationItem = z.infer<typeof NotificationItemSchema>;
export type MobileNotificationItem = z.infer<typeof MobileNotificationItemSchema>;
export type AppointmentSummary = z.infer<typeof AppointmentSummarySchema>;
export type LabResultSummary = z.infer<typeof LabResultSummarySchema>;
export type MedicationSummary = z.infer<typeof MedicationSummarySchema>;
export type DashboardData = z.infer<typeof DashboardDataSchema>;
export type MobileDashboard = z.infer<typeof MobileDashboardSchema>;
export type MobileEmergencyData = z.infer<typeof MobileEmergencyDataSchema>;
export type SessionContext = z.infer<typeof SessionContextSchema>;
export type MobileSession = z.infer<typeof MobileSessionSchema>;
export type AuditEvent = z.infer<typeof AuditEventSchema>;
