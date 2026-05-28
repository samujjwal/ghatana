export {
  API_BASE_URL,
  buildPhrHeaders,
  phrFetch,
  PhrApiError,
} from './requestApi';

export {
  loginWithCredentials,
  logoutSession,
} from './authApi';

export {
  exportPatientBundle,
  fetchDashboardData,
  fetchPatientProfile,
  fetchTimeline,
  updatePatientProfile,
} from './patientApi';

export {
  fetchRecords,
  fetchRecordDetail,
} from './recordsApi';

export {
  fetchConditionDetail,
  fetchConditions,
  fetchImmunizations,
  fetchMedicationDetail,
  fetchMedications,
  fetchObservationDetail,
  fetchObservations,
} from './clinicalApi';

export {
  confirmOcrDocument,
  downloadDocument,
  fetchDocumentDetail,
  fetchDocuments,
  fetchOcrDocument,
  rejectOcrDocument,
  uploadDocument,
} from './documentsApi';

export {
  createConsentGrant,
  revokeConsentGrant,
} from './consentApi';

export {
  createAppointmentRequest,
  bookAppointment,
  cancelAppointment,
  fetchAppointments,
  fetchProviders,
  rescheduleAppointment,
} from './adminApi';

export {
  requestEmergencyAccess,
  reviewEmergencyAccess,
} from './emergencyApi';

export {
  fetchAuditEvents,
} from './auditApi';

export {
  fetchReleaseReadiness,
} from './releaseApi';

export {
  fetchNotifications,
} from './notificationsApi';

export {
  fetchCaregiverDependents,
  fetchFchvDashboard,
  fetchProviderPatients,
} from './roleApi';
