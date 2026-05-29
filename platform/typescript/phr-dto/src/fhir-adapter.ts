/**
 * FHIR to UI data transformation adapter
 *
 * Converts FHIR (Fast Healthcare Interoperability Resources) data structures
 * into UI-friendly DTOs. This adapter centralizes all FHIR transformation logic,
 * making it easier to maintain and test.
 *
 * @doc.type module
 * @doc.purpose Transform FHIR data to UI DTOs
 * @doc.layer platform
 */

import { z } from 'zod';
import type { PatientRecordSummary, MobileRecord } from './schemas';

/**
 * FHIR Resource types we handle
 */
export type FhirResourceType = 
  | 'Observation'
  | 'Condition'
  | 'MedicationRequest'
  | 'DiagnosticReport'
  | 'DocumentReference'
  | 'Procedure'
  | 'Encounter';

/**
 * Minimal FHIR Resource interface
 */
export interface FhirResource {
  resourceType: string;
  id: string;
  meta?: {
    lastUpdated?: string;
  };
  status?: string;
  code?: {
    coding?: Array<{ code?: string; display?: string }>;
    text?: string;
  };
  subject?: {
    reference?: string;
  };
}

/**
 * FHIR Bundle interface
 */
export interface FhirBundle {
  resourceType: 'Bundle';
  type?: string;
  entry?: Array<{
    resource?: FhirResource;
  }>;
}

/**
 * Transform a FHIR resource to a patient record summary
 */
export function fhirToPatientRecordSummary(resource: FhirResource): PatientRecordSummary {
  const resourceType = resource.resourceType as FhirResourceType;
  const title = extractTitle(resource);
  const category = extractCategory(resourceType);
  const updatedAt = resource.meta?.lastUpdated || new Date().toISOString();
  const fhirJson = JSON.stringify(resource);
  const redacted = shouldRedact(resourceType);

  return {
    id: resource.id,
    title,
    category,
    updatedAt,
    resourceType,
    fhirJson,
    redacted,
    provenance: {
      source: 'fhir',
      transformedAt: new Date().toISOString(),
    },
  };
}

/**
 * Transform a FHIR resource to a mobile record (simplified)
 */
export function fhirToMobileRecord(resource: FhirResource): MobileRecord {
  const title = extractTitle(resource);
  const summary = extractSummary(resource);
  const fhirPreview = `${resource.resourceType}: ${title}`;

  return {
    id: resource.id,
    title,
    summary,
    fhirPreview,
  };
}

/**
 * Transform a FHIR bundle to patient record summaries
 */
export function fhirBundleToPatientRecordSummaries(bundle: FhirBundle): PatientRecordSummary[] {
  if (!bundle.entry) return [];
  
  return bundle.entry
    .map((entry) => entry.resource)
    .filter((resource): resource is FhirResource => resource !== undefined)
    .map(fhirToPatientRecordSummary);
}

/**
 * Transform a FHIR bundle to mobile records
 */
export function fhirBundleToMobileRecords(bundle: FhirBundle): MobileRecord[] {
  if (!bundle.entry) return [];
  
  return bundle.entry
    .map((entry) => entry.resource)
    .filter((resource): resource is FhirResource => resource !== undefined)
    .map(fhirToMobileRecord);
}

/**
 * Extract a human-readable title from a FHIR resource
 */
function extractTitle(resource: FhirResource): string {
  if (resource.code?.text) return resource.code.text;
  if (resource.code?.coding?.[0]?.display) return resource.code.coding[0].display;
  if (resource.status) return `${resource.resourceType} - ${resource.status}`;
  return resource.resourceType;
}

/**
 * Extract a category based on resource type
 */
function extractCategory(resourceType: FhirResourceType): string {
  const categoryMap: Record<FhirResourceType, string> = {
    Observation: 'Lab Result',
    Condition: 'Diagnosis',
    MedicationRequest: 'Medication',
    DiagnosticReport: 'Report',
    DocumentReference: 'Document',
    Procedure: 'Procedure',
    Encounter: 'Visit',
  };
  return categoryMap[resourceType] || 'Other';
}

/**
 * Extract a summary for mobile display
 */
function extractSummary(resource: FhirResource): string {
  const title = extractTitle(resource);
  const status = resource.status ? ` (${resource.status})` : '';
  return `${title}${status}`;
}

/**
 * Determine if a resource should be redacted based on type
 */
function shouldRedact(resourceType: FhirResourceType): boolean {
  // Certain sensitive resource types should be redacted by default
  const sensitiveTypes: FhirResourceType[] = [
    'Condition', // Diagnoses may contain sensitive info
  ];
  return sensitiveTypes.includes(resourceType);
}

/**
 * Validate FHIR resource structure
 */
export const FhirResourceSchema = z.object({
  resourceType: z.string(),
  id: z.string(),
  meta: z.object({
    lastUpdated: z.string().optional(),
  }).optional(),
  status: z.string().optional(),
  code: z.object({
    coding: z.array(z.object({
      code: z.string().optional(),
      display: z.string().optional(),
    })).optional(),
    text: z.string().optional(),
  }).optional(),
  subject: z.object({
    reference: z.string().optional(),
  }).optional(),
});

/**
 * Validate FHIR bundle structure
 */
export const FhirBundleSchema = z.object({
  resourceType: z.literal('Bundle'),
  type: z.string().optional(),
  entry: z.array(z.object({
    resource: FhirResourceSchema.optional(),
  })).optional(),
});

/**
 * Safe transform with validation
 */
export function safeFhirToPatientRecordSummary(data: unknown): PatientRecordSummary | null {
  const result = FhirResourceSchema.safeParse(data);
  if (!result.success) return null;
  return fhirToPatientRecordSummary(result.data);
}

/**
 * Safe bundle transform with validation
 */
export function safeFhirBundleToPatientRecordSummaries(data: unknown): PatientRecordSummary[] {
  const result = FhirBundleSchema.safeParse(data);
  if (!result.success) return [];
  return fhirBundleToPatientRecordSummaries(result.data);
}
