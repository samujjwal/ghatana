import { z } from 'zod';
import type {
  AppointmentCreateResult,
  AppointmentRequest,
  AppointmentSummary,
  AuditEvent,
  AuditEventsPage,
  ConditionSummary,
  ConsentGrant,
  ConsentGrantRequest,
  ConsentRevokeResult,
  DashboardData,
  DependentEntry,
  DocumentDetail,
  DocumentSummary,
  DocumentUploadResult,
  EmergencyAccessEvent,
  EmergencyAccessRequest,
  EmergencyReviewRequest,
  FchvPatientEntry,
  ImmunizationSummary,
  LabResultSummary,
  MedicationSummary,
  NotificationSummary,
  ObservationSummary,
  OcrReviewDocument,
  PatientProfile,
  PatientProfileExtended,
  PatientProfileUpdateRequest,
  PatientRecordSummary,
  PatientRosterEntry,
  PhrLoginRequest,
  PhrReleaseReadiness,
  PhrSession,
  TimelineEvent,
} from '../types';
import { t } from '../i18n/phrI18n';
import {
  FhirConsentSchema,
  fhirConsentToGrant,
} from './fhirMappers';
import {
  AppointmentBookingResultSchema,
  AppointmentCancelResultSchema,
  AppointmentCreateResultSchema,
  AppointmentRequestSchema,
  AppointmentSummarySchema,
  AuditEventsPageSchema,
  BackendConsentGrantRequestSchema,
  BackendDashboardSchema,
  BackendMedicationPrescriptionSchema,
  ConditionSummarySchema,
  ConsentGrantSchema,
  ConsentRevokeResultSchema,
  dashboardSchema,
  DependentEntrySchema,
  DocumentDetailSchema,
  DocumentSummarySchema,
  DocumentUploadInitResultSchema,
  DocumentUploadResultSchema,
  DownloadDocumentResponseSchema,
  EmergencyAccessEventSchema,
  EmergencyAccessRequestSchema,
  ExportPatientBundleSchema,
  FchvPatientEntrySchema,
  ImmunizationSummarySchema,
  MedicationDetailSchema,
  MedicationSummarySchema,
  NotificationSummarySchema,
  ObservationSummarySchema,
  OcrRejectResultSchema,
  OcrReviewDocumentSchema,
  PatientProfileExtendedSchema,
  PatientRecordListSchema,
  PatientRecordAccessSchema,
  PatientRosterEntrySchema,
  PhrReleaseReadinessSchema,
  PhrSessionSchema,
  ProviderAvailabilitySchema,
  TimelinePageSchema,
} from './phrApiSchemas';
import { API_BASE_URL, buildPhrHeaders, phrFetch, PhrApiError, type PhrRole, type SessionContext, withIdempotency } from './requestApi';

type MutationSessionContext = SessionContext & { idempotencyKey?: string };

async function fhirGet(resourceType: string, id: string | undefined, context: SessionContext): Promise<unknown> {
  const path = id !== undefined ? `/fhir/${resourceType}/${id}` : `/fhir/${resourceType}`;
  return phrFetch(path, {
    accept: 'application/fhir+json',
    context,
  });
}
// --- Public ---

export async function fetchDashboardData(context: SessionContext): Promise<DashboardData> {
  const dashboard = await phrFetch('/api/v1/dashboard', { context, expectedSchema: BackendDashboardSchema });
  
  // Map backend dashboard contract to frontend DashboardData structure
  // Backend provides summary counts and alerts; frontend renders these directly
  return {
    patient: {
      id: dashboard.principalId,
      name: dashboard.profileSummary.name,
      age: 0, // Backend does not provide age in summary
      bloodType: t('common.unknown'), // Backend does not provide blood type in summary
      location: dashboard.tenantId,
      emergencyContact: t('common.unknown'), // Backend does not provide emergency contact in summary
    },
    records: [], // Backend summary does not include individual records
    consents: [], // Backend summary does not include individual consents (only counts in accessAlerts)
    appointments: [], // Backend provides nextAppointment as summary, not full list
    labs: [], // Backend summary does not include individual labs (only counts in recentObservations)
    medications: [], // Backend provides activeCount and adherenceAlert, not individual medications
  };
}

export async function exportPatientBundle(context: SessionContext): Promise<string> {
  return phrFetch('/fhir/Patient/current/$export', {
    method: 'POST',
    context: withIdempotency(context),
    contentType: 'application/json',
    expectedSchema: ExportPatientBundleSchema,
  });
}

export async function fetchReleaseReadiness(options: {
  environment: 'local' | 'dev' | 'staging' | 'prod';
  role: PhrRole;
  tenantId: string;
  principalId: string;
}): Promise<PhrReleaseReadiness> {
  const url = new URL(`${API_BASE_URL}/api/v1/release-readiness`);
  url.searchParams.set('environment', options.environment);
  return phrFetch(`${url.pathname}${url.search}`, {
    context: {
      tenantId: options.tenantId,
      principalId: options.principalId,
      role: options.role,
      persona: options.role,
      tier: 'clinical',
    },
    expectedSchema: PhrReleaseReadinessSchema,
  });
}

export async function fetchAuditEvents(options: {
  patientId?: string;
  filter?: 'all' | 'access' | 'consent' | 'emergency';
  page?: number;
  pageSize?: number;
  tenantId: string;
  principalId: string;
  role: PhrRole;
}): Promise<AuditEventsPage> {
  const context: SessionContext = {
    tenantId: options.tenantId,
    principalId: options.principalId,
    role: options.role,
  };
  const url = new URL(`${API_BASE_URL}/api/v1/audit/events`);
  if (options.patientId) url.searchParams.set('patientId', options.patientId);
  if (options.filter && options.filter !== 'all') url.searchParams.set('filter', options.filter);
  url.searchParams.set('page', String(options.page ?? 0));
  url.searchParams.set('pageSize', String(options.pageSize ?? 50));

  return phrFetch(`${url.pathname}${url.search}`, {
    context,
    expectedSchema: AuditEventsPageSchema,
  });
}

export async function createConsentGrant(
  request: ConsentGrantRequest,
  context: MutationSessionContext,
): Promise<ConsentGrant> {
  const backendRequest = {
    patientId: request.patientId,
    recipientId: request.recipientId,
    scope: {
      resourceTypes: request.scope.resourceTypes,
      allDocuments: request.scope.allDocuments,
      specificDocumentIds: request.scope.specificDocumentIds,
      actions: request.scope.actions,
    },
    expiresAt: request.expiresAt,
  };
  const validated = BackendConsentGrantRequestSchema.parse(backendRequest);
  const consentGrantResponseSchema = z.union([
    FhirConsentSchema.transform(fhirConsentToGrant),
    ConsentGrantSchema,
  ]);
  return phrFetch('/api/v1/consents/grants', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
    expectedSchema: consentGrantResponseSchema,
  });
}

export async function revokeConsentGrant(
  grantId: string,
  patientId: string,
  context: MutationSessionContext,
): Promise<ConsentRevokeResult> {
  if (!grantId || !patientId) {
    throw new PhrApiError('grantId and patientId are required to revoke consent', 400, 'Consent');
  }
  const url = new URL(`${API_BASE_URL}/api/v1/consents/grants/${encodeURIComponent(grantId)}/revoke`);
  url.searchParams.set('patientId', patientId);
  const data = await phrFetch(`${url.pathname}${url.search}`, {
    method: 'POST',
    context: withIdempotency(context),
    expectedSchema: ConsentRevokeResultSchema,
  });
  return data;
}

export async function createAppointmentRequest(
  request: AppointmentRequest,
  context: MutationSessionContext,
): Promise<AppointmentCreateResult> {
  const validated = AppointmentRequestSchema.parse(request);
  const data = await phrFetch('/api/v1/appointments', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
    expectedSchema: AppointmentCreateResultSchema,
  });
  return data;
}

export async function requestEmergencyAccess(
  request: EmergencyAccessRequest,
  context: MutationSessionContext,
): Promise<EmergencyAccessEvent> {
  const validated = EmergencyAccessRequestSchema.parse(request);
  const data = await phrFetch('/api/v1/emergency/access', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
    expectedSchema: EmergencyAccessEventSchema,
  });
  return data;
}

export async function reviewEmergencyAccess(
  review: EmergencyReviewRequest,
  context: MutationSessionContext,
): Promise<EmergencyAccessEvent> {
  const data = await phrFetch(`/api/v1/emergency/reviews/${encodeURIComponent(review.eventId)}`, {
    method: 'POST',
    body: JSON.stringify({ reviewNote: review.reviewNote, reviewerId: review.reviewerId }),
    context: withIdempotency(context),
    expectedSchema: EmergencyAccessEventSchema,
  });
  return data;
}

export async function loginWithCredentials(request: PhrLoginRequest): Promise<PhrSession> {
  if (!request.nationalId.trim() || !request.password) {
    throw new PhrApiError('National ID and password are required', 400);
  }
  const data = await phrFetch('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ nationalId: request.nationalId, password: request.password }),
    context: { idempotencyKey: crypto.randomUUID() },
    expectedSchema: PhrSessionSchema,
  });
  return data;
}

export async function logoutSession(context: SessionContext): Promise<void> {
  await phrFetch('/api/v1/auth/logout', {
    method: 'POST',
    context,
  });
}

// --- Patient ---

export async function fetchPatientProfile(context: SessionContext): Promise<PatientProfileExtended> {
  const data = await phrFetch('/api/v1/profile', { context, expectedSchema: PatientProfileExtendedSchema });
  return data;
}

export async function updatePatientProfile(
  update: PatientProfileUpdateRequest,
  context: MutationSessionContext,
): Promise<PatientProfileExtended> {
  const data = await phrFetch('/api/v1/profile', {
    method: 'PUT',
    body: JSON.stringify(update),
    context: withIdempotency(context),
    expectedSchema: PatientProfileExtendedSchema,
  });
  return data;
}

// --- Timeline ---

export async function fetchTimeline(
  principalId: string,
  context: SessionContext,
  filters?: {
    category?: string;
  },
): Promise<TimelineEvent[]> {
  const path = filters?.category
    ? `/api/v1/records/timeline/${encodeURIComponent(principalId)}/category/${encodeURIComponent(filters.category)}`
    : `/api/v1/records/timeline/${encodeURIComponent(principalId)}`;

  const body = await phrFetch(path, { context, expectedSchema: TimelinePageSchema });

  return body.items.map(item => {
    const type = toTimelineEventType(item.eventType);
    return {
      id: item.id,
      date: item.occurredAt.slice(0, 10),
      type,
      title: item.title,
      summary: item.description,
      occurredAt: item.occurredAt,
      description: item.description,
    };
  });
}

function toTimelineEventType(eventType: string): TimelineEvent['type'] {
  if (
    eventType === 'visit'
    || eventType === 'lab'
    || eventType === 'immunization'
    || eventType === 'medication'
    || eventType === 'consent'
    || eventType === 'document'
  ) {
    return eventType;
  }
  return 'document';
}

// --- Conditions ---

export async function fetchConditions(principalId: string, context: SessionContext): Promise<ConditionSummary[]> {
  const body = await phrFetch(`/api/v1/clinical/conditions?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(ConditionSummarySchema), count: z.number() }) });
  return body.items;
}

export async function fetchConditionDetail(conditionId: string, principalId: string, context: SessionContext): Promise<ConditionSummary> {
  return await phrFetch(`/api/v1/clinical/conditions/${encodeURIComponent(conditionId)}?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: ConditionSummarySchema });
}

// --- Observations ---

export async function fetchObservations(principalId: string, context: SessionContext): Promise<ObservationSummary[]> {
  const body = await phrFetch(`/api/v1/clinical/observations?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(ObservationSummarySchema) }) });
  return body.items;
}

export async function fetchObservationDetail(observationId: string, principalId: string, context: SessionContext): Promise<ObservationSummary> {
  return await phrFetch(`/api/v1/clinical/observations/${encodeURIComponent(observationId)}?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: ObservationSummarySchema });
}

// --- Labs ---

export async function fetchLabs(principalId: string, context: SessionContext): Promise<ObservationSummary[]> {
  const body = await phrFetch(`/api/v1/clinical/labs?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(ObservationSummarySchema) }) });
  return body.items;
}

// --- Immunizations ---

export async function fetchImmunizations(principalId: string, context: SessionContext): Promise<ImmunizationSummary[]> {
  const body = await phrFetch(`/api/v1/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(ImmunizationSummarySchema) }) });
  return body.items;
}

// --- Documents ---

export async function fetchDocuments(principalId: string, context: SessionContext): Promise<DocumentSummary[]> {
  const body = await phrFetch(`/api/v1/records/documents?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(DocumentSummarySchema) }) });
  return body.items;
}

export async function fetchDocumentDetail(documentId: string, principalId: string, context: SessionContext): Promise<DocumentDetail> {
  return await phrFetch(`/api/v1/records/documents/${encodeURIComponent(documentId)}?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: DocumentDetailSchema });
}

export async function downloadDocument(
  documentId: string,
  patientId: string,
  context: SessionContext,
): Promise<{ downloadUrl: string; expiresAt: string }> {
  return await phrFetch(`/api/v1/records/documents/${encodeURIComponent(documentId)}/download?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    context: withIdempotency(context),
    expectedSchema: DownloadDocumentResponseSchema,
  });
}

export async function uploadDocument(
  patientId: string,
  file: File,
  metadata: { title: string; category?: string; description?: string },
  context: MutationSessionContext,
  options: { signal?: AbortSignal; onProgress?: (progress: number) => void } = {},
): Promise<{ id: string; status: string; ocrStatus: string }> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('title', metadata.title);
  if (metadata.category) formData.append('category', metadata.category);
  if (metadata.description) formData.append('description', metadata.description);

  const uploadContext = withIdempotency(context);
  if (options.onProgress) {
    return uploadDocumentWithProgress(patientId, formData, uploadContext, options);
  }

  const data = await phrFetch(`/api/v1/records/documents?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    body: formData,
    context: uploadContext,
    expectedSchema: DocumentUploadInitResultSchema,
    contentType: '',
    signal: options.signal,
  });
  return data;
}

function uploadDocumentWithProgress(
  patientId: string,
  formData: FormData,
  context: SessionContext,
  options: { signal?: AbortSignal; onProgress?: (progress: number) => void },
): Promise<{ id: string; status: string; ocrStatus: string }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const path = `/api/v1/records/documents?patientId=${encodeURIComponent(patientId)}`;
    xhr.open('POST', `${API_BASE_URL}${path}`);

    const headers = buildPhrHeaders(context);
    for (const [name, value] of Object.entries(headers)) {
      xhr.setRequestHeader(name, value);
    }

    const abortUpload = (): void => {
      xhr.abort();
      reject(new DOMException('Document upload cancelled', 'AbortError'));
    };

    if (options.signal?.aborted) {
      abortUpload();
      return;
    }
    options.signal?.addEventListener('abort', abortUpload, { once: true });

    xhr.upload.onprogress = (event: ProgressEvent<EventTarget>): void => {
      if (event.lengthComputable && event.total > 0) {
        options.onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };

    xhr.onload = (): void => {
      options.signal?.removeEventListener('abort', abortUpload);
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new PhrApiError(`PHR request failed: POST ${path} returned ${xhr.status}`, xhr.status));
        return;
      }
      try {
        const parsed = xhr.responseText.length > 0 ? JSON.parse(xhr.responseText) : undefined;
        options.onProgress?.(100);
        resolve(DocumentUploadInitResultSchema.parse(parsed));
      } catch (error: unknown) {
        reject(error instanceof Error ? error : new Error('Invalid document upload response'));
      }
    };

    xhr.onerror = (): void => {
      options.signal?.removeEventListener('abort', abortUpload);
      reject(new PhrApiError(`PHR request failed: POST ${path} network error`, 0));
    };

    xhr.onabort = (): void => {
      options.signal?.removeEventListener('abort', abortUpload);
    };

    xhr.send(formData);
  });
}

// --- Medications ---

export async function fetchMedications(principalId: string, context: SessionContext): Promise<MedicationSummary[]> {
  const body = await phrFetch(`/api/v1/clinical/medications?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(BackendMedicationPrescriptionSchema) }) });
  return body.items.map(toMedicationSummary);
}

export async function fetchMedicationDetail(
  patientId: string,
  medicationId: string,
  context: SessionContext,
): Promise<MedicationSummary & { interactions: string[]; warnings: string[]; history: Array<{ date: string; action: string }> }> {
  const prescription = await phrFetch(`/api/v1/clinical/medications/prescriptions/${encodeURIComponent(medicationId)}?patientId=${encodeURIComponent(patientId)}`, { context, expectedSchema: BackendMedicationPrescriptionSchema });
  const medication = toMedicationSummary(prescription);

  return MedicationDetailSchema.parse({
    ...medication,
    interactions: [],
    warnings: prescription.refillsRemaining === 0 ? [t('medicationDetail.warning.noRefills')] : [],
    history: prescription.prescribedAt ? [{ date: prescription.prescribedAt, action: t('medicationDetail.action.prescribed') }] : [],
  });
}

function toMedicationSummary(prescription: z.infer<typeof BackendMedicationPrescriptionSchema>): MedicationSummary {
  return {
    id: prescription.id,
    medication: prescription.medicationName,
    dosage: prescription.dosage,
    schedule: prescription.indication ?? t('common.unknown'),
    adherence: 100,
    status: prescription.status?.toLowerCase() === 'discontinued' ? 'stopped' : 'active',
  };
}

// --- Appointments ---

export async function fetchAppointments(principalId: string, context: SessionContext): Promise<AppointmentSummary[]> {
  const body = await phrFetch(`/api/v1/appointments?patientId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(AppointmentSummarySchema) }) });
  return body.items;
}

// fetchProviders removed - /api/v1/providers not in route contract

export async function bookAppointment(
  patientId: string,
  providerId: string,
  slot: string,
  notes: string | undefined,
  context: MutationSessionContext,
): Promise<{ id: string; status: string }> {
  const data = await phrFetch('/api/v1/appointments', {
    method: 'POST',
    body: JSON.stringify({ patientId, providerId, slot, notes }),
    context: withIdempotency(context),
    expectedSchema: AppointmentBookingResultSchema,
  });
  return data;
}

export async function cancelAppointment(
  appointmentId: string,
  patientId: string,
  context: MutationSessionContext,
): Promise<{ success: boolean }> {
  const data = await phrFetch(`/api/v1/appointments/${encodeURIComponent(appointmentId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ patientId }),
    context: withIdempotency(context),
    expectedSchema: AppointmentCancelResultSchema,
  });
  return data;
}

export async function rescheduleAppointment(
  appointmentId: string,
  patientId: string,
  newSlot: string,
  context: MutationSessionContext,
): Promise<{ id: string; status: string }> {
  const data = await phrFetch(`/api/v1/appointments/${encodeURIComponent(appointmentId)}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ patientId, newSlot }),
    context: withIdempotency(context),
    expectedSchema: AppointmentBookingResultSchema,
  });
  return data;
}

// --- Records ---

export async function fetchRecords(
  patientId: string,
  context: SessionContext,
  filters?: {
    category?: string;
    resourceType?: string;
    dateFrom?: string;
    dateTo?: string;
    limit?: number;
    offset?: number;
  },
): Promise<PatientRecordSummary[]> {
  const url = new URL(`${API_BASE_URL}/api/v1/records`);
  url.searchParams.set('patientId', patientId);
  url.searchParams.set('limit', String(filters?.limit ?? 50));
  url.searchParams.set('offset', String(filters?.offset ?? 0));
  if (filters?.category) url.searchParams.set('category', filters.category);
  if (filters?.resourceType) url.searchParams.set('resourceType', filters.resourceType);
  if (filters?.dateFrom) url.searchParams.set('dateFrom', filters.dateFrom);
  if (filters?.dateTo) url.searchParams.set('dateTo', filters.dateTo);

  const body = await phrFetch(`${url.pathname}${url.search}`, {
    context,
    expectedSchema: PatientRecordListSchema,
  });
  return body.items;
}

export async function fetchRecordDetail(
  patientId: string,
  recordId: string,
  context: SessionContext,
): Promise<{ record: PatientRecordSummary; fhirJson: string; accessAudit: { accessedAt: string; accessedBy: string } }> {
  const response = await phrFetch(`/api/v1/records/${encodeURIComponent(recordId)}?patientId=${encodeURIComponent(patientId)}`, { context, expectedSchema: PatientRecordAccessSchema });

  // Fetch the actual FHIR resource
  const fhirData = await fhirGet(response.resourceType, recordId, context);
  
  return {
    record: {
      id: recordId,
      title: `${response.resourceType} - ${recordId}`,
      category: 'clinical',
      updatedAt: response.accessedAt,
      resourceType: response.resourceType,
      fhirJson: JSON.stringify(fhirData, null, 2),
    },
    fhirJson: JSON.stringify(fhirData, null, 2),
    accessAudit: {
      accessedAt: response.accessedAt,
      accessedBy: response.accessedBy,
    },
  };
}

export async function fetchOcrDocument(
  docId: string,
  context: SessionContext,
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/api/v1/records/documents/${encodeURIComponent(docId)}/ocr`, { context, expectedSchema: OcrReviewDocumentSchema });
  return data;
}

export async function confirmOcrDocument(
  docId: string,
  context: MutationSessionContext,
  correctedText?: string,
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/api/v1/records/documents/${encodeURIComponent(docId)}/ocr/confirm`, {
    method: 'POST',
    body: correctedText ? JSON.stringify({ correctedText }) : undefined,
    context: withIdempotency(context),
    expectedSchema: OcrReviewDocumentSchema,
  });
  return data;
}

export async function rejectOcrDocument(
  docId: string,
  context: MutationSessionContext,
): Promise<{ documentId: string; rejected: boolean }> {
  const data = await phrFetch(`/api/v1/records/documents/${encodeURIComponent(docId)}/ocr/reject`, {
    method: 'POST',
    context: withIdempotency(context),
    expectedSchema: OcrRejectResultSchema,
  });
  return data;
}

// --- Notifications ---

export async function fetchNotifications(principalId: string, context: SessionContext): Promise<NotificationSummary[]> {
  const body = await phrFetch(`/api/v1/notifications?principalId=${encodeURIComponent(principalId)}`, { context, expectedSchema: z.object({ items: z.array(NotificationSummarySchema) }) });
  return body.items;
}

export async function markNotificationRead(
  notificationId: string,
  context: MutationSessionContext,
): Promise<{ notificationId: string; read: boolean }> {
  if (!notificationId.trim()) {
    throw new PhrApiError('notificationId is required to mark a notification as read', 400, 'Notification');
  }
  return phrFetch(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: 'POST',
    context: withIdempotency(context),
    expectedSchema: z.object({
      notificationId: z.string(),
      read: z.boolean(),
    }),
  });
}

// Provider, Caregiver, FCHV routes removed - marked as hidden in route contract

