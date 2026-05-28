import { notificationBodyContainsPhi, redactPhiFromText } from '../pushNotifications';

describe('pushNotifications PHI redaction', () => {
  describe('notificationBodyContainsPhi', () => {
    it('detects MRN patterns', () => {
      expect(notificationBodyContainsPhi('Your MRN is 123456789')).toBe(true);
    });

    it('detects national ID patterns', () => {
      expect(notificationBodyContainsPhi('National ID: 987654321012')).toBe(true);
    });

    it('detects DOB patterns', () => {
      expect(notificationBodyContainsPhi('DOB: 01/15/1990')).toBe(true);
      expect(notificationBodyContainsPhi('Date of birth: 1990-01-15')).toBe(true);
    });

    it('detects blood type mentions', () => {
      expect(notificationBodyContainsPhi('Blood type: A+')).toBe(true);
    });

    it('detects diagnosis mentions', () => {
      expect(notificationBodyContainsPhi('Diagnosis: Type 2 Diabetes')).toBe(true);
    });

    it('detects medication mentions', () => {
      expect(notificationBodyContainsPhi('Medication: Metformin')).toBe(true);
    });

    it('detects ICD codes', () => {
      expect(notificationBodyContainsPhi('ICD-10: E11')).toBe(true);
    });

    it('detects FHIR mentions', () => {
      expect(notificationBodyContainsPhi('FHIR resource updated')).toBe(true);
    });

    it('returns false for safe messages', () => {
      expect(notificationBodyContainsPhi('You have a new appointment')).toBe(false);
      expect(notificationBodyContainsPhi('Lab results are ready')).toBe(false);
      expect(notificationBodyContainsPhi('Prescription refill approved')).toBe(false);
    });
  });

  describe('redactPhiFromText', () => {
    it('redacts messages containing PHI', () => {
      expect(redactPhiFromText('Diagnosis: Hypertension')).toBe('[Redacted - open app to view details]');
      expect(redactPhiFromText('MRN: 123456789')).toBe('[Redacted - open app to view details]');
    });

    it('preserves messages without PHI', () => {
      expect(redactPhiFromText('You have a new appointment')).toBe('You have a new appointment');
      expect(redactPhiFromText('Lab results are ready')).toBe('Lab results are ready');
    });

    it('handles empty strings', () => {
      expect(redactPhiFromText('')).toBe('');
    });
  });
});
