import { z } from 'zod';

export const BackendDashboardSchema = z.object({
  tenantId: z.string(),
  principalId: z.string(),
  role: z.string(),
  correlationId: z.string(),
  profileSummary: z.object({
    name: z.string(),
    email: z.string().nullable().optional(),
    providerId: z.string().nullable().optional(),
    active: z.boolean(),
  }),
  nextAppointment: z.object({
    appointmentId: z.string(),
    scheduledTime: z.string(),
    provider: z.string(),
    type: z.string(),
  }).nullable(),
  medications: z.object({
    activeCount: z.number(),
    adherenceAlert: z.boolean(),
  }),
  recentObservations: z.object({
    count: z.number(),
    hasCritical: z.boolean(),
  }),
  activeConditions: z.object({
    count: z.number(),
    hasChronic: z.boolean(),
  }),
  documents: z.object({
    totalCount: z.number(),
    pendingOcr: z.number(),
  }),
  accessAlerts: z.object({
    expiringConsents: z.number(),
    emergencyAccessPending: z.boolean(),
  }),
  generatedAt: z.string(),
});

// --- Audit ---

export const AuditEventSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  eventType: z.string(),
  principal: z.string(),
  resourceType: z.string(),
  resourceId: z.string().nullable(),
  timestamp: z.string(),
  success: z.boolean(),
  details: z.record(z.string(), z.string()),
});

export const AuditEventsPageSchema = z.object({
  events: z.array(AuditEventSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
});

export const ConditionSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  display: z.string(),
  code: z.string(),
  status: z.enum(['active', 'resolved', 'chronic']),
  onsetDate: z.string(),
}).passthrough();

export const ObservationSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  value: z.string(),
  unit: z.string(),
  status: z.enum(['normal', 'attention', 'critical', 'abnormal', 'pending']),
  effectiveDate: z.string(),
  recordedAt: z.string(),
  loincCode: z.string().optional(),
}).passthrough();

export const ImmunizationSummarySchema = z.object({
  id: z.string(),
  vaccine: z.string(),
  vaccineName: z.string().optional(),
  date: z.string(),
  occurrenceDate: z.string(),
  status: z.enum(['completed', 'not-done', 'entered-in-error', 'due']),
  dose: z.string().optional(),
  doseNumber: z.number().optional(),
  lotNumber: z.string().optional(),
  cvxCode: z.string().optional(),
  route: z.string().optional(),
  seriesName: z.string().optional(),
  source: z.object({
    system: z.string(),
    administeredBy: z.string().optional(),
  }).passthrough().optional(),
  nextDue: z.object({
    id: z.string(),
    vaccine: z.string(),
    vaccineName: z.string().optional(),
    cvxCode: z.string().optional(),
    seriesName: z.string().optional(),
    doseNumber: z.number().optional(),
    dueDate: z.string(),
    status: z.string(),
  }).passthrough().optional(),
}).passthrough();

export const DocumentSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  contentType: z.string().optional(),
  uploadedAt: z.string(),
  status: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
  ocrStatus: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
}).passthrough();

export const DocumentDetailSchema = DocumentSummarySchema.extend({
  uploadedBy: z.string(),
  description: z.string().optional(),
  versions: z.array(z.object({
    versionId: z.string(),
    versionNumber: z.number(),
    createdAt: z.string(),
    createdBy: z.string(),
    changeNote: z.string().optional(),
  }).passthrough()).optional(),
  auditLog: z.array(z.object({
    id: z.string(),
    action: z.string(),
    timestamp: z.string(),
    performedBy: z.string(),
    details: z.record(z.string(), z.unknown()).optional(),
  }).passthrough()).optional(),
}).passthrough();

export const MedicationSummarySchema = z.object({
  id: z.string(),
  medication: z.string(),
  dosage: z.string(),
  schedule: z.string().optional(),
  adherence: z.number().optional(),
  route: z.string().nullable().optional(),
  routeSource: z.string().optional(),
  frequency: z.string().optional(),
  status: z.enum(['active', 'history', 'stopped']).optional(),
  prescriberId: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  adherenceSource: z.string().optional(),
  adherenceStatus: z.object({
    measured: z.boolean(),
    source: z.string(),
  }).passthrough().optional(),
  prescribedAt: z.string().optional(),
  expiresAt: z.string().optional(),
  refillsRemaining: z.number().optional(),
}).passthrough();

export const MedicationDetailSchema = MedicationSummarySchema.extend({
  interactions: z.array(z.string()).optional(),
  warnings: z.array(z.string()).optional(),
  history: z.array(z.object({
    date: z.string(),
    action: z.string(),
  })).optional(),
}).passthrough();

export const BackendMedicationPrescriptionSchema = z.object({
  id: z.string(),
  medicationName: z.string().optional(),
  medication: z.string().optional(),
  dosage: z.string(),
  indication: z.string().optional(),
  route: z.string().nullable().optional(),
  routeSource: z.string().optional(),
  frequency: z.string().optional(),
  schedule: z.string().optional(),
  prescriberId: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  adherenceSource: z.string().optional(),
  adherenceStatus: z.object({
    measured: z.boolean(),
    source: z.string(),
  }).passthrough().optional(),
  interactions: z.array(z.string()).optional(),
  warnings: z.array(z.string()).optional(),
  prescribedAt: z.string().optional(),
  expiresAt: z.string().optional(),
  refillsRemaining: z.number().optional(),
  status: z.string().optional(),
}).passthrough();

export const AppointmentSummarySchema = z.object({
  id: z.string(),
  provider: z.string(),
  specialty: z.string(),
  startsAt: z.string(),
  location: z.string(),
  status: z.enum(['requested', 'confirmed', 'completed', 'cancelled']).optional(),
  reminderSent: z.boolean().optional(),
  appointmentId: z.string().optional(),
  patientId: z.string().optional(),
  providerId: z.string().optional(),
  scheduledTime: z.string().optional(),
  durationMinutes: z.number().optional(),
  appointmentType: z.string().optional(),
  slotId: z.string().optional(),
}).passthrough();

export const NotificationSummarySchema = z.object({
  id: z.string(),
  type: z.enum(['consent_expiry', 'appointment_reminder', 'lab_result', 'emergency_access', 'system']),
  title: z.string(),
  body: z.string(),
  createdAt: z.string(),
  readAt: z.string().nullable().optional(),
}).passthrough();

export const DependentEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  relationship: z.string(),
  alertCount: z.number(),
  birthDate: z.string().optional(),
  age: z.number().optional(),
}).passthrough();

export const PatientRosterEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  mrn: z.string(),
  alertCount: z.number(),
  lastVisit: z.string().optional(),
  condition: z.string().optional(),
  age: z.number().optional(),
  status: z.string().optional(),
  nextAppointment: z.string().optional(),
}).passthrough();

export const FchvPatientEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  village: z.string(),
  riskLevel: z.enum(['low', 'medium', 'high']),
  pendingActions: z.number(),
  lastContact: z.string().optional(),
}).passthrough();

export const ProviderAvailabilitySchema = z.object({
  id: z.string(),
  name: z.string(),
  specialty: z.string(),
  availableSlots: z.array(z.string()),
});

export const DownloadDocumentResponseSchema = z.object({
  downloadUrl: z.string(),
  expiresAt: z.string(),
});

export const TimelineEventWireSchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  eventType: z.string(),
  title: z.string(),
  description: z.string(),
  details: z.record(z.string(), z.unknown()),
});

export const TimelinePageSchema = z.object({
  patientId: z.string(),
  items: z.array(TimelineEventWireSchema),
  count: z.number(),
  generatedAt: z.string(),
});

export const PatientRecordSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  category: z.string(),
  updatedAt: z.string(),
  resourceType: z.string(),
  redacted: z.boolean().optional(),
  provenance: z.record(z.string(), z.unknown()).optional(),
}).passthrough();

export const PatientRecordAccessAuditSchema = z.object({
  accessedAt: z.string(),
  accessedBy: z.string(),
  accessRole: z.string().optional(),
  correlationId: z.string().optional(),
  policyReason: z.string().optional(),
  requiresAudit: z.boolean().optional(),
}).passthrough();

export const PatientRecordDetailSchema = z.object({
  record: PatientRecordSummarySchema,
  fhirJson: z.string(),
  accessAudit: PatientRecordAccessAuditSchema,
});

export const PatientRecordListSchema = z.object({
  patientId: z.string(),
  items: z.array(PatientRecordSummarySchema),
  count: z.number(),
  limit: z.number(),
  offset: z.number(),
  generatedAt: z.string(),
});

export const PhrReleaseReadinessSchema = z.object({
  product: z.literal('phr'),
  tenantId: z.string(),
  principalId: z.string(),
  role: z.string(),
  environment: z.string(),
  generatedAt: z.string(),
  targetCommitSha: z.string(),
  runtimeTruthBlocked: z.boolean(),
  requiredSections: z.array(z.string()),
  releaseReadiness: z.object({
    status: z.string().optional(),
    overallScore: z.number().optional(),
    blockingIssues: z.array(z.string()).optional(),
    warnings: z.array(z.string()).optional(),
  }).optional(),
  sections: z.record(z.string(), z.object({
    label: z.string(),
    status: z.string(),
    runtimeProven: z.boolean(),
    message: z.string(),
    details: z.unknown().optional(),
  })),
}).passthrough();

export const ConsentRevokeResultSchema = z.object({
  grantId: z.string(),
  status: z.literal('REVOKED'),
});

export const ConsentGrantSchema = z.object({
  id: z.string(),
  recipient: z.string(),
  purpose: z.string(),
  status: z.enum(['active', 'expiring', 'revoked']),
  expiresAt: z.string(),
});

const BackendConsentGrantItemSchema = z.object({
  id: z.string().nullable().optional(),
  recipientId: z.string(),
  scope: z.object({
    resourceTypes: z.array(z.string()).optional(),
  }).passthrough().optional(),
  status: z.string(),
  expiresAt: z.string(),
}).passthrough().transform((grant) => {
  const normalizedStatus = grant.status.toLowerCase();
  return {
    id: grant.id ?? `${grant.recipientId}:${grant.expiresAt}`,
    recipient: grant.recipientId,
    purpose: grant.scope?.resourceTypes?.join(', ') || 'consent',
    status: normalizedStatus === 'revoked'
      ? 'revoked' as const
      : normalizedStatus === 'expiring'
        ? 'expiring' as const
        : 'active' as const,
    expiresAt: grant.expiresAt,
  };
});

export const ConsentGrantListResponseSchema = z.object({
  items: z.array(z.union([ConsentGrantSchema, BackendConsentGrantItemSchema])),
  count: z.number(),
  patientId: z.string(),
});

export const ExportPatientBundleSchema = z.object({
  requestId: z.string(),
  operation: z.literal('EXPORT'),
  contractId: z.string(),
  status: z.string(),
  reasonCode: z.string(),
  correlationId: z.string(),
}).passthrough();

export const AppointmentCreateResultSchema = z.object({
  id: z.string(),
  status: z.enum(['requested', 'confirmed', 'cancelled']),
  specialty: z.string(),
  preferredDate: z.string(),
  createdAt: z.string(),
});

export const EmergencyAccessEventSchema = z.object({
  id: z.string(),
  patientId: z.string(),
  clinicianId: z.string(),
  reason: z.string(),
  status: z.enum(['PENDING', 'REVIEWED', 'EXPIRED']),
  accessedAt: z.string(),
  reviewedAt: z.string().optional(),
  reviewedBy: z.string().optional(),
  reviewNote: z.string().optional(),
});

export const PatientProfileExtendedSchema = z.object({
  id: z.string(),
  name: z.string(),
  age: z.number(),
  bloodType: z.string(),
  location: z.string(),
  emergencyContact: z.string(),
  birthDate: z.string().optional(),
  preferredLanguage: z.string().optional(),
  facilityId: z.string().optional(),
  mrn: z.string().optional(),
  gender: z.string().optional(),
}).passthrough();

export const DocumentUploadResultSchema = z.object({
  id: z.string(),
  status: z.enum(['pending_ocr', 'processed', 'failed']),
  ocrAvailable: z.boolean(),
});

export const OcrReviewDocumentSchema = z.object({
  id: z.string(),
  title: z.string(),
  ocrText: z.string(),
  extractedText: z.string(),
  correctedText: z.string().optional(),
  confidence: z.number(),
  status: z.enum(['pending_review', 'confirmed', 'rejected']),
});

export const OcrRejectResultSchema = z.object({
  documentId: z.string(),
  rejected: z.boolean(),
});

export const AppointmentBookingResultSchema = z.object({
  id: z.string(),
  status: z.string(),
});

export const AppointmentCancelResultSchema = z.object({
  success: z.boolean().optional(),
  appointmentId: z.string().optional(),
  status: z.string().optional(),
}).transform((value) => ({
  success: value.success ?? (value.status === 'CANCELLED' || value.status === 'cancelled'),
}));

export const DocumentUploadInitResultSchema = z.object({
  id: z.string(),
  status: z.string(),
  ocrStatus: z.string(),
});

// --- Consent ---

export const ConsentGrantRequestSchema = z.object({
  patientId: z.string().min(1),
  recipientId: z.string().min(1),
  purpose: z.string().min(1),
  scope: z.object({
    resourceTypes: z.array(z.string()).min(1),
    allDocuments: z.boolean().optional(),
    specificDocumentIds: z.array(z.string()).optional(),
    actions: z.array(z.string()).optional(),
  }),
  expiresAt: z.string().min(1),
}).strict();

export const BackendConsentGrantRequestSchema = z.object({
  patientId: z.string().min(1),
  recipientId: z.string().min(1),
  scope: z.object({
    resourceTypes: z.array(z.string()).min(1),
    allDocuments: z.boolean().optional(),
    specificDocumentIds: z.array(z.string()).optional(),
    actions: z.array(z.string()).optional(),
  }),
  expiresAt: z.string().min(1),
}).strict();

// --- Appointment ---

export const AppointmentRequestSchema = z.object({
  specialty: z.string().min(1, 'Specialty is required'),
  preferredDate: z.string().min(1, 'Preferred date is required'),
  notes: z.string().optional(),
}).strict();

// --- Emergency ---

export const EmergencyAccessRequestSchema = z.object({
  patientId: z.string().min(1, 'Patient ID is required'),
  reason: z.string().min(5, 'Reason must be at least 5 characters'),
  clinicianId: z.string().min(1, 'Clinician ID is required'),
}).strict();

// --- Auth ---

export const PhrSessionSchema = z.object({
  principalId: z.string(),
  tenantId: z.string(),
  role: z.enum(['patient', 'caregiver', 'clinician', 'admin', 'fchv']),
  name: z.string(),
  expiresAt: z.string(),
  persona: z.string().optional(),
  tier: z.string().optional(),
  facilityId: z.string().optional(),
  correlationId: z.string().optional(),
});

// --- List Response Schemas ---

export const ListResponseSchema = <T extends z.ZodType>(itemSchema: T) =>
  z.object({
    items: z.array(itemSchema),
    count: z.number(),
  });

export const ConditionsListSchema = ListResponseSchema(ConditionSummarySchema);
export const ObservationsListSchema = ListResponseSchema(ObservationSummarySchema);
export const ImmunizationsListSchema = ListResponseSchema(ImmunizationSummarySchema);
export const DocumentsListSchema = ListResponseSchema(DocumentSummarySchema);
export const MedicationsListSchema = ListResponseSchema(BackendMedicationPrescriptionSchema);
export const AppointmentsListSchema = ListResponseSchema(AppointmentSummarySchema);
export const ProvidersListSchema = ListResponseSchema(ProviderAvailabilitySchema);
export const NotificationsListSchema = ListResponseSchema(NotificationSummarySchema);
export const DependentsListSchema = ListResponseSchema(DependentEntrySchema);
export const PatientRosterListSchema = ListResponseSchema(PatientRosterEntrySchema);
export const FchvDashboardListSchema = ListResponseSchema(FchvPatientEntrySchema);

// --- Action Response Schemas ---

export const MarkNotificationReadSchema = z.object({
  notificationId: z.string(),
  read: z.boolean(),
});

export const NotificationActionSchema = z.object({
  notificationId: z.string(),
  action: z.string(),
  result: z.unknown(),
});

export const NotificationPreferencesSchema = z.object({
  emailEnabled: z.boolean(),
  smsEnabled: z.boolean(),
  inAppEnabled: z.boolean(),
});

export const NotificationPreferencesUpdateSchema = z.object({
  principalId: z.string(),
  preferences: NotificationPreferencesSchema,
  updated: z.boolean(),
});

export const EmergencyReviewRequestSchema = z.object({
  eventId: z.string(),
  reviewNote: z.string(),
  reviewerId: z.string(),
});
