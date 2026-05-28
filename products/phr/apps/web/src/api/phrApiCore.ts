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
import { API_BASE_URL, buildPhrHeaders, phrFetch, PhrApiError, type SessionContext, withIdempotency } from './requestApi';

async function fhirGet(resourceType: string, id: string | undefined, context: SessionContext): Promise<unknown> {
  const path = id !== undefined ? `/fhir/${resourceType}/${id}` : `/fhir/${resourceType}`;
  return phrFetch(path, {
    accept: 'application/fhir+json',
    context,
  });
}
// --- Public ---

export async function fetchDashboardData(context: SessionContext): Promise<DashboardData> {
  const dashboard = BackendDashboardSchema.parse(await phrFetch('/dashboard', { context }));
  return dashboardSchema.parse({
    patient: {
      id: dashboard.principalId,
      name: dashboard.profileSummary.name,
      age: 0,
      bloodType: t('common.unknown'),
      location: dashboard.tenantId,
      emergencyContact: t('common.unknown'),
    },
    records: [],
    consents: [],
    appointments: [],
    labs: [],
    medications: Array.from({ length: dashboard.medications.activeCount }, (_, index) => ({
      id: `active-medication-${index + 1}`,
      medication: t('dashboard.medications.active'),
      dosage: '',
      schedule: '',
      adherence: dashboard.medications.adherenceAlert ? 0 : 100,
    })),
  });
}

export async function exportPatientBundle(context: SessionContext): Promise<string> {
  return phrFetch('/fhir/Patient/current/$export', {
    method: 'POST',
    context,
    contentType: 'application/json',
    expectedSchema: ExportPatientBundleSchema,
  });
}

export async function fetchReleaseReadiness(options: {
  environment: 'local' | 'dev' | 'staging' | 'prod';
  role: string;
  tenantId?: string;
  principalId?: string;
}): Promise<PhrReleaseReadiness> {
  const tenantId = options.tenantId ?? 'tenant-health-1';
  const principalId = options.principalId ?? `${options.role}-release-cockpit`;
  const url = new URL(`${API_BASE_URL}/release-readiness`);
  url.searchParams.set('environment', options.environment);
  const response = await fetch(url.toString(), {
    headers: buildPhrHeaders({
      tenantId,
      principalId,
      role: options.role,
      persona: options.role,
      tier: 'clinical',
    }),
  });
  if (!response.ok) {
    throw new PhrApiError(`PHR release readiness failed with status ${response.status}`, response.status);
  }
  const data = await response.json();
  return PhrReleaseReadinessSchema.parse(data);
}

export async function fetchAuditEvents(options: {
  patientId?: string;
  filter?: 'all' | 'access' | 'consent' | 'emergency';
  page?: number;
  pageSize?: number;
  tenantId?: string;
  principalId?: string;
  role?: string;
}): Promise<AuditEventsPage> {
  const context: SessionContext = {
    tenantId: options.tenantId,
    principalId: options.principalId,
    role: options.role,
  };
  const url = new URL(`${API_BASE_URL}/audit/events`);
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
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
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
  return phrFetch('/consents/grants', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
    expectedSchema: consentGrantResponseSchema,
  });
}

export async function revokeConsentGrant(
  grantId: string,
  patientId: string,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<ConsentRevokeResult> {
  if (!grantId || !patientId) {
    throw new PhrApiError('grantId and patientId are required to revoke consent', 400, 'Consent');
  }
  const url = new URL(`${API_BASE_URL}/consents/grants/${encodeURIComponent(grantId)}/revoke`);
  url.searchParams.set('patientId', patientId);
  const data = await phrFetch(`${url.pathname}${url.search}`, {
    method: 'POST',
    context: withIdempotency(context),
  });
  return ConsentRevokeResultSchema.parse(data);
}

export async function createAppointmentRequest(
  request: AppointmentRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<AppointmentCreateResult> {
  const validated = AppointmentRequestSchema.parse(request);
  const data = await phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
  });
  return AppointmentCreateResultSchema.parse(data);
}

export async function requestEmergencyAccess(
  request: EmergencyAccessRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<EmergencyAccessEvent> {
  const validated = EmergencyAccessRequestSchema.parse(request);
  const data = await phrFetch('/emergency/access', {
    method: 'POST',
    body: JSON.stringify(validated),
    context: withIdempotency(context),
  });
  return EmergencyAccessEventSchema.parse(data);
}

export async function reviewEmergencyAccess(
  review: EmergencyReviewRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<EmergencyAccessEvent> {
  const data = await phrFetch(`/emergency/reviews/${encodeURIComponent(review.eventId)}`, {
    method: 'POST',
    body: JSON.stringify({ reviewNote: review.reviewNote, reviewerId: review.reviewerId }),
    context: withIdempotency(context),
  });
  return EmergencyAccessEventSchema.parse(data);
}

export async function loginWithCredentials(request: PhrLoginRequest): Promise<PhrSession> {
  if (!request.nationalId.trim() || !request.password) {
    throw new PhrApiError('National ID and password are required', 400);
  }
  const data = await phrFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ nationalId: request.nationalId, password: request.password }),
    expectedSchema: PhrSessionSchema,
  });
  return data;
}

export async function logoutSession(context: {
  tenantId: string;
  principalId: string;
}): Promise<void> {
  await phrFetch('/auth/logout', {
    method: 'POST',
    context,
  });
}

// --- Patient ---

export async function fetchPatientProfile(context: SessionContext): Promise<PatientProfileExtended> {
  const data = await phrFetch('/profile', { context });
  return PatientProfileExtendedSchema.parse(data);
}

export async function updatePatientProfile(
  update: PatientProfileUpdateRequest,
  context: { tenantId: string; principalId: string; role?: string; correlationId?: string; idempotencyKey?: string },
): Promise<PatientProfileExtended> {
  const data = await phrFetch('/profile', {
    method: 'PUT',
    body: JSON.stringify(update),
    context: withIdempotency(context),
  });
  return PatientProfileExtendedSchema.parse(data);
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
    ? `/timeline/${encodeURIComponent(principalId)}/category/${encodeURIComponent(filters.category)}`
    : `/timeline/${encodeURIComponent(principalId)}`;

  const body = TimelinePageSchema.parse(await phrFetch(path, { context }));

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
  const body = z.object({ items: z.array(ConditionSummarySchema) })
    .parse(await phrFetch(`/conditions/${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchConditionDetail(conditionId: string, principalId: string, context: SessionContext): Promise<ConditionSummary> {
  return ConditionSummarySchema.parse(
    await phrFetch(`/conditions/${encodeURIComponent(conditionId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

// --- Observations ---

export async function fetchObservations(principalId: string, context: SessionContext): Promise<ObservationSummary[]> {
  const body = z.object({ items: z.array(ObservationSummarySchema) })
    .parse(await phrFetch(`/clinical/labs/observations?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchObservationDetail(observationId: string, principalId: string, context: SessionContext): Promise<ObservationSummary> {
  return ObservationSummarySchema.parse(
    await phrFetch(`/clinical/labs/observations/${encodeURIComponent(observationId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

// --- Immunizations ---

export async function fetchImmunizations(principalId: string, context: SessionContext): Promise<ImmunizationSummary[]> {
  const body = z.object({ items: z.array(ImmunizationSummarySchema) })
    .parse(await phrFetch(`/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

// --- Documents ---

export async function fetchDocuments(principalId: string, context: SessionContext): Promise<DocumentSummary[]> {
  const body = z.object({ items: z.array(DocumentSummarySchema) })
    .parse(await phrFetch(`/documents?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchDocumentDetail(documentId: string, principalId: string, context: SessionContext): Promise<DocumentDetail> {
  return DocumentDetailSchema.parse(
    await phrFetch(`/documents/${encodeURIComponent(documentId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

export async function downloadDocument(
  documentId: string,
  patientId: string,
  context: SessionContext,
): Promise<{ downloadUrl: string; expiresAt: string }> {
  return DownloadDocumentResponseSchema.parse(await phrFetch(`/documents/${encodeURIComponent(documentId)}/download?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    context,
  }));
}

export async function uploadDocument(
  patientId: string,
  file: File,
  metadata: { title: string; category?: string; description?: string },
  context: SessionContext & { idempotencyKey?: string },
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

  const data = await phrFetch(`/documents?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    body: formData,
    context: uploadContext,
    contentType: '',
    signal: options.signal,
  });
  return DocumentUploadInitResultSchema.parse(data);
}

function uploadDocumentWithProgress(
  patientId: string,
  formData: FormData,
  context: SessionContext,
  options: { signal?: AbortSignal; onProgress?: (progress: number) => void },
): Promise<{ id: string; status: string; ocrStatus: string }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const path = `/documents?patientId=${encodeURIComponent(patientId)}`;
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
  const body = z.object({ items: z.array(BackendMedicationPrescriptionSchema) })
    .parse(await phrFetch(`/clinical/medications?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items.map(toMedicationSummary);
}

export async function fetchMedicationDetail(
  patientId: string,
  medicationId: string,
  context: SessionContext,
): Promise<MedicationSummary & { interactions: string[]; warnings: string[]; history: Array<{ date: string; action: string }> }> {
  const prescription = BackendMedicationPrescriptionSchema.parse(
    await phrFetch(`/clinical/medications/prescriptions/${encodeURIComponent(medicationId)}?patientId=${encodeURIComponent(patientId)}`, { context }),
  );
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
  const body = z.object({ items: z.array(AppointmentSummarySchema) })
    .parse(await phrFetch(`/appointments?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchProviders(context: SessionContext): Promise<Array<{ id: string; name: string; specialty: string; availableSlots: string[] }>> {
  const body = z.object({ items: z.array(ProviderAvailabilitySchema) })
    .parse(await phrFetch('/providers', { context }));
  return body.items;
}

export async function bookAppointment(
  patientId: string,
  providerId: string,
  slot: string,
  notes: string | undefined,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  const data = await phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify({ patientId, providerId, slot, notes }),
    context: withIdempotency(context),
  });
  return AppointmentBookingResultSchema.parse(data);
}

export async function cancelAppointment(
  appointmentId: string,
  patientId: string,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ success: boolean }> {
  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ patientId }),
    context: withIdempotency(context),
  });
  return AppointmentCancelResultSchema.parse(data);
}

export async function rescheduleAppointment(
  appointmentId: string,
  patientId: string,
  newSlot: string,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ patientId, newSlot }),
    context: withIdempotency(context),
  });
  return AppointmentBookingResultSchema.parse(data);
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
  },
): Promise<PatientRecordSummary[]> {
  const url = new URL(`${API_BASE_URL}/patients/${encodeURIComponent(patientId)}/records`);
  url.searchParams.set('limit', '50');
  url.searchParams.set('offset', '0');
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
  const response = PatientRecordAccessSchema.parse(
    await phrFetch(`/patient-records/${encodeURIComponent(patientId)}/records/${encodeURIComponent(recordId)}`, { context }),
  );

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
  context: { tenantId: string; principalId: string; role: string },
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr`, { context });
  return OcrReviewDocumentSchema.parse(data);
}

export async function confirmOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string; idempotencyKey?: string },
  correctedText?: string,
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/confirm`, {
    method: 'POST',
    body: correctedText ? JSON.stringify({ correctedText }) : undefined,
    context: withIdempotency(context),
  });
  return OcrReviewDocumentSchema.parse(data);
}

export async function rejectOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string; idempotencyKey?: string },
): Promise<{ documentId: string; rejected: boolean }> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/reject`, {
    method: 'POST',
    context: withIdempotency(context),
  });
  return OcrRejectResultSchema.parse(data);
}

// --- Notifications ---

export async function fetchNotifications(principalId: string, context: SessionContext): Promise<NotificationSummary[]> {
  const body = z.object({ items: z.array(NotificationSummarySchema) })
    .parse(await phrFetch(`/notifications?principalId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function markNotificationRead(
  notificationId: string,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ notificationId: string; read: boolean }> {
  if (!notificationId.trim()) {
    throw new PhrApiError('notificationId is required to mark a notification as read', 400, 'Notification');
  }
  return phrFetch(`/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: 'POST',
    context: withIdempotency(context),
    expectedSchema: z.object({
      notificationId: z.string(),
      read: z.boolean(),
    }),
  });
}

// --- Provider ---

export async function fetchProviderPatients(
  context: { tenantId: string; principalId: string; role: string },
): Promise<PatientRosterEntry[]> {
  const body = z.object({ items: z.array(PatientRosterEntrySchema) })
    .parse(await phrFetch('/provider/patients', { context }));
  return body.items;
}

// --- Caregiver ---

export async function fetchCaregiverDependents(context: SessionContext): Promise<DependentEntry[]> {
  const body = z.object({ items: z.array(DependentEntrySchema) })
    .parse(await phrFetch('/caregiver/dependents', { context }));
  return body.items;
}

// --- FCHV ---

export async function fetchFchvDashboard(context: SessionContext): Promise<FchvPatientEntry[]> {
  const body = z.object({ items: z.array(FchvPatientEntrySchema) })
    .parse(await phrFetch('/fchv/dashboard', { context }));
  return body.items;
}

