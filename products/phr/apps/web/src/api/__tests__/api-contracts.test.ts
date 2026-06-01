/**
 * PHR API contract tests
 *
 * Verifies that all exported API functions exist and have correct signatures.
 * These are contract tests that ensure the API surface is stable.
 *
 * @doc.type test
 * @doc.purpose Verify PHR API function contracts
 * @doc.layer web
 */

import { describe, it, expect } from 'vitest';
import * as phrApi from '../phrApi';

describe('PHR API Contract Tests', () => {
  describe('requestApi exports', () => {
    it('exports API_BASE_URL constant', () => {
      expect(typeof phrApi.API_BASE_URL).toBe('string');
    });

    it('exports buildPhrHeaders function', () => {
      expect(typeof phrApi.buildPhrHeaders).toBe('function');
    });

    it('exports phrFetch function', () => {
      expect(typeof phrApi.phrFetch).toBe('function');
    });

    it('exports PhrApiError class', () => {
      expect(phrApi.PhrApiError).toBeDefined();
      expect(typeof phrApi.PhrApiError).toBe('function');
    });
  });

  describe('authApi exports', () => {
    it('exports loginWithCredentials function', () => {
      expect(typeof phrApi.loginWithCredentials).toBe('function');
    });

    it('exports logoutSession function', () => {
      expect(typeof phrApi.logoutSession).toBe('function');
    });
  });

  describe('patientApi exports', () => {
    it('exports exportPatientBundle function', () => {
      expect(typeof phrApi.exportPatientBundle).toBe('function');
    });

    it('exports fetchHieStatus function', () => {
      expect(typeof phrApi.fetchHieStatus).toBe('function');
    });

    it('exports fetchDashboardData function', () => {
      expect(typeof phrApi.fetchDashboardData).toBe('function');
    });

    it('exports submitHieOperation function', () => {
      expect(typeof phrApi.submitHieOperation).toBe('function');
    });

    it('exports fetchPatientProfile function', () => {
      expect(typeof phrApi.fetchPatientProfile).toBe('function');
    });

    it('exports fetchTimeline function', () => {
      expect(typeof phrApi.fetchTimeline).toBe('function');
    });

    it('exports updatePatientProfile function', () => {
      expect(typeof phrApi.updatePatientProfile).toBe('function');
    });
  });

  describe('recordsApi exports', () => {
    it('exports fetchRecords function', () => {
      expect(typeof phrApi.fetchRecords).toBe('function');
    });

    it('exports fetchRecordDetail function', () => {
      expect(typeof phrApi.fetchRecordDetail).toBe('function');
    });
  });

  describe('clinicalApi exports', () => {
    it('exports fetchConditionDetail function', () => {
      expect(typeof phrApi.fetchConditionDetail).toBe('function');
    });

    it('exports fetchConditions function', () => {
      expect(typeof phrApi.fetchConditions).toBe('function');
    });

    it('exports fetchImmunizations function', () => {
      expect(typeof phrApi.fetchImmunizations).toBe('function');
    });

    it('exports fetchMedicationDetail function', () => {
      expect(typeof phrApi.fetchMedicationDetail).toBe('function');
    });

    it('exports fetchMedications function', () => {
      expect(typeof phrApi.fetchMedications).toBe('function');
    });

    it('exports fetchObservationDetail function', () => {
      expect(typeof phrApi.fetchObservationDetail).toBe('function');
    });

    it('exports fetchObservations function', () => {
      expect(typeof phrApi.fetchObservations).toBe('function');
    });
  });

  describe('documentsApi exports', () => {
    it('exports confirmOcrDocument function', () => {
      expect(typeof phrApi.confirmOcrDocument).toBe('function');
    });

    it('exports downloadDocument function', () => {
      expect(typeof phrApi.downloadDocument).toBe('function');
    });

    it('exports fetchDocumentDetail function', () => {
      expect(typeof phrApi.fetchDocumentDetail).toBe('function');
    });

    it('exports fetchDocuments function', () => {
      expect(typeof phrApi.fetchDocuments).toBe('function');
    });

    it('exports fetchOcrDocument function', () => {
      expect(typeof phrApi.fetchOcrDocument).toBe('function');
    });

    it('exports rejectOcrDocument function', () => {
      expect(typeof phrApi.rejectOcrDocument).toBe('function');
    });

    it('exports uploadDocument function', () => {
      expect(typeof phrApi.uploadDocument).toBe('function');
    });
  });

  describe('consentApi exports', () => {
    it('exports createConsentGrant function', () => {
      expect(typeof phrApi.createConsentGrant).toBe('function');
    });

    it('exports revokeConsentGrant function', () => {
      expect(typeof phrApi.revokeConsentGrant).toBe('function');
    });
  });

  describe('adminApi exports', () => {
    it('exports createAppointmentRequest function', () => {
      expect(typeof phrApi.createAppointmentRequest).toBe('function');
    });

    it('exports bookAppointment function', () => {
      expect(typeof phrApi.bookAppointment).toBe('function');
    });

    it('exports cancelAppointment function', () => {
      expect(typeof phrApi.cancelAppointment).toBe('function');
    });

    it('exports fetchAppointments function', () => {
      expect(typeof phrApi.fetchAppointments).toBe('function');
    });

    it('exports backend workflow list functions', () => {
      expect(typeof phrApi.fetchBillingHistory).toBe('function');
      expect(typeof phrApi.fetchImagingOrders).toBe('function');
      expect(typeof phrApi.fetchImagingStudies).toBe('function');
      expect(typeof phrApi.fetchReferrals).toBe('function');
      expect(typeof phrApi.fetchTelemedicineSessions).toBe('function');
    });

    it('exports rescheduleAppointment function', () => {
      expect(typeof phrApi.rescheduleAppointment).toBe('function');
    });
  });

  describe('emergencyApi exports', () => {
    it('exports requestEmergencyAccess function', () => {
      expect(typeof phrApi.requestEmergencyAccess).toBe('function');
    });

    it('exports reviewEmergencyAccess function', () => {
      expect(typeof phrApi.reviewEmergencyAccess).toBe('function');
    });
  });

  describe('auditApi exports', () => {
    it('exports fetchAuditEvents function', () => {
      expect(typeof phrApi.fetchAuditEvents).toBe('function');
    });
  });

  describe('releaseApi exports', () => {
    it('exports fetchReleaseReadiness function', () => {
      expect(typeof phrApi.fetchReleaseReadiness).toBe('function');
    });
  });

  describe('notificationsApi exports', () => {
    it('exports fetchNotifications function', () => {
      expect(typeof phrApi.fetchNotifications).toBe('function');
    });
  });

  // roleApi exports removed - routes are hidden in route contract (API-004, API-006)

  describe('API surface stability', () => {
    it('exports all expected API functions', () => {
      const expectedExports = [
        'API_BASE_URL',
        'buildPhrHeaders',
        'phrFetch',
        'PhrApiError',
        'loginWithCredentials',
        'logoutSession',
        'exportPatientBundle',
        'fetchDashboardData',
        'fetchHieStatus',
        'fetchPatientProfile',
        'fetchTimeline',
        'submitHieOperation',
        'updatePatientProfile',
        'fetchRecords',
        'fetchRecordDetail',
        'fetchConditionDetail',
        'fetchConditions',
        'fetchImmunizations',
        'fetchMedicationDetail',
        'fetchMedications',
        'fetchObservationDetail',
        'fetchObservations',
        'confirmOcrDocument',
        'downloadDocument',
        'fetchDocumentDetail',
        'fetchDocuments',
        'fetchOcrDocument',
        'rejectOcrDocument',
        'uploadDocument',
        'createConsentGrant',
        'revokeConsentGrant',
        'createAppointmentRequest',
        'bookAppointment',
        'cancelAppointment',
        'fetchAppointments',
        'fetchBillingHistory',
        'fetchImagingOrders',
        'fetchImagingStudies',
        'fetchReferrals',
        'fetchTelemedicineSessions',
        'rescheduleAppointment',
        'requestEmergencyAccess',
        'reviewEmergencyAccess',
        'fetchAuditEvents',
        'fetchReleaseReadiness',
        'fetchNotifications',
      ];

      expectedExports.forEach((exportName) => {
        expect(phrApi[exportName as keyof typeof phrApi]).toBeDefined();
      });
    });
  });
});
