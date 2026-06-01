import { describe, expect, it } from 'vitest';
import {
  BackendDashboardSchema,
  EmergencyAccessRequestSchema,
  OcrReviewDocumentSchema,
  PhrSessionSchema,
  TimelinePageSchema,
} from '../phrApiSchemas';

describe('PHR API schemas', () => {
  it('validates backend dashboard contracts used by the dashboard projection', () => {
    const parsed = BackendDashboardSchema.parse({
      tenantId: 'tenant-1',
      principalId: 'patient-1',
      role: 'patient',
      correlationId: 'corr-1',
      profileSummary: {
        name: 'Patient One',
        email: null,
        providerId: null,
        active: true,
      },
      nextAppointment: null,
      medications: {
        activeCount: 2,
        adherenceAlert: false,
      },
      recentObservations: {
        count: 4,
        hasCritical: false,
      },
      activeConditions: {
        count: 1,
        hasChronic: true,
      },
      documents: {
        totalCount: 3,
        pendingOcr: 1,
      },
      accessAlerts: {
        expiringConsents: 0,
        emergencyAccessPending: false,
      },
      generatedAt: '2026-05-28T00:00:00.000Z',
    });

    expect(parsed.medications.activeCount).toBe(2);
  });

  it('rejects incomplete emergency and session responses', () => {
    expect(() => EmergencyAccessRequestSchema.parse({
      patientId: 'patient-1',
      reason: 'help',
      clinicianId: 'clinician-1',
    })).toThrow();

    expect(() => PhrSessionSchema.parse({
      principalId: 'user-1',
      tenantId: 'tenant-1',
      role: 'superuser',
      name: 'User One',
      expiresAt: '2026-05-28T00:00:00.000Z',
    })).toThrow();
  });

  it('validates paged timeline payloads', () => {
    const parsed = TimelinePageSchema.parse({
      patientId: 'patient-1',
      items: [{
        id: 'event-1',
        occurredAt: '2026-05-28T00:00:00.000Z',
        eventType: 'observation',
        title: 'Observation recorded',
        description: 'Observation summary',
        details: { source: 'backend' },
      }],
      count: 1,
      generatedAt: '2026-05-28T00:00:00.000Z',
    });

    expect(parsed.items[0]?.details).toEqual({ source: 'backend' });
  });

  it('preserves OCR provenance returned by the review lifecycle', () => {
    const parsed = OcrReviewDocumentSchema.parse({
      id: 'doc-1',
      documentId: 'doc-1',
      title: 'Lab report',
      ocrText: 'Extracted text',
      extractedText: 'Extracted text',
      confidence: 0.91,
      status: 'confirmed',
      provenance: {
        source: 'document-ocr-service',
        reviewedAt: '2026-05-28T00:00:00.000Z',
        correlationId: 'corr-1',
      },
    });

    expect(parsed.provenance?.source).toBe('document-ocr-service');
  });
});
