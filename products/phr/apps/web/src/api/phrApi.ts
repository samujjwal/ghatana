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
  fetchHieStatus,
  fetchPatientProfile,
  fetchTimeline,
  submitHieOperation,
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
  fetchConsentGrants,
  revokeConsentGrant,
} from './consentApi';

export {
  createAppointmentRequest,
  bookAppointment,
  cancelAppointment,
  fetchAppointments,
  fetchBillingHistory,
  fetchImagingOrders,
  fetchImagingStudies,
  fetchReferrals,
  fetchTelemedicineSessions,
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

// Role-specific functions removed - routes are hidden in route contract
// See API-004 and API-006 in implementation tracker
