/**
 * Tests for PHI Storage Compliance Gate (KER-T05)
 */

jest.mock('../phiEncryptedStorage', () => ({
  phiSet: jest.fn(() => Promise.resolve()),
  phiGet: jest.fn(() => Promise.resolve(null)),
  phiRemove: jest.fn(() => Promise.resolve()),
  phiClearAll: jest.fn(() => Promise.resolve()),
  setPhiStorageAdapter: jest.fn(),
}));

import { phiSet, phiGet, phiRemove, phiClearAll, setPhiStorageAdapter } from '../phiEncryptedStorage';
import {
  phiStorageComplianceGate,
  phiSetCompliant,
  phiGetCompliant,
  phiRemoveCompliant,
  phiClearAllCompliant,
  getFieldSensitivity,
  fieldRequiresEncryption,
  fieldRequiresAudit,
} from '../phiStorageComplianceGate';

const mockSet = phiSet as jest.Mock;
const mockGet = phiGet as jest.Mock;
const mockRemove = phiRemove as jest.Mock;
const mockClearAll = phiClearAll as jest.Mock;
const mockSetAdapter = setPhiStorageAdapter as jest.Mock;

describe('PHI Storage Compliance Gate', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Reset adapter before each test
    mockSetAdapter.mockImplementation(() => {});
  });

  describe('phiStorageComplianceGate', () => {
    it('should allow compliant storage operations', async () => {
      const callback = jest.fn();
      await phiStorageComplianceGate('patient', 'name', 'set', callback);
      expect(callback).toHaveBeenCalled();
    });

    it('should reject operations for unclassified fields', async () => {
      const callback = jest.fn();
      await expect(
        phiStorageComplianceGate('patient', 'unclassifiedField' as any, 'set', callback)
      ).rejects.toThrow('UNCLASSIFIED_FIELD');
      expect(callback).not.toHaveBeenCalled();
    });

    it('should reject operations for unknown categories', async () => {
      const callback = jest.fn();
      await expect(
        phiStorageComplianceGate('unknown' as any, 'name', 'set', callback)
      ).rejects.toThrow('UNKNOWN_CATEGORY');
      expect(callback).not.toHaveBeenCalled();
    });
  });

  describe('phiSetCompliant', () => {
    it('should store PHI data after validation', async () => {
      await phiSetCompliant('patient', 'name', 'test-key', 'test-value');
      expect(mockSet).toHaveBeenCalledWith('test-key', 'test-value');
    });

    it('should reject unclassified fields', async () => {
      await expect(
        phiSetCompliant('patient', 'unclassifiedField' as any, 'test-key', 'test-value')
      ).rejects.toThrow('UNCLASSIFIED_FIELD');
    });
  });

  describe('phiGetCompliant', () => {
    it('should retrieve PHI data after validation', async () => {
      mockGet.mockResolvedValue('test-value');
      const result = await phiGetCompliant('patient', 'name', 'test-key');
      expect(result).toBe('test-value');
      expect(mockGet).toHaveBeenCalledWith('test-key');
    });

    it('should return null for missing PHI data', async () => {
      mockGet.mockResolvedValue(null);
      const result = await phiGetCompliant('patient', 'name', 'test-key');
      expect(result).toBeNull();
    });
  });

  describe('phiRemoveCompliant', () => {
    it('should remove PHI data after validation', async () => {
      await phiRemoveCompliant('patient', 'name', 'test-key');
      expect(mockRemove).toHaveBeenCalledWith('test-key');
    });
  });

  describe('phiClearAllCompliant', () => {
    it('should clear all PHI data with compliance logging', async () => {
      await phiClearAllCompliant();
      expect(mockClearAll).toHaveBeenCalled();
    });
  });

  describe('getFieldSensitivity', () => {
    it('should return high sensitivity for direct identifiers', () => {
      expect(getFieldSensitivity('patient', 'patientId')).toBe('high');
      expect(getFieldSensitivity('patient', 'name')).toBe('high');
      expect(getFieldSensitivity('medical', 'diagnosis')).toBe('high');
    });

    it('should return medium sensitivity for indirect identifiers', () => {
      expect(getFieldSensitivity('patient', 'phoneNumber')).toBe('medium');
      expect(getFieldSensitivity('patient', 'email')).toBe('medium');
      expect(getFieldSensitivity('medical', 'immunizations')).toBe('medium');
    });

    it('should return low sensitivity for non-identifying metadata', () => {
      expect(getFieldSensitivity('documents', 'documentTitle')).toBe('low');
      expect(getFieldSensitivity('appointments', 'appointmentType')).toBe('low');
    });

    it('should throw for unclassified fields', () => {
      expect(() => getFieldSensitivity('patient', 'unclassifiedField' as any)).toThrow('UNCLASSIFIED_FIELD');
    });
  });

  describe('fieldRequiresEncryption', () => {
    it('should return true for high sensitivity fields', () => {
      expect(fieldRequiresEncryption('patient', 'patientId')).toBe(true);
      expect(fieldRequiresEncryption('medical', 'diagnosis')).toBe(true);
    });

    it('should return true for medium sensitivity fields', () => {
      expect(fieldRequiresEncryption('patient', 'phoneNumber')).toBe(true);
      expect(fieldRequiresEncryption('medical', 'immunizations')).toBe(true);
    });

    it('should return false for low sensitivity fields', () => {
      expect(fieldRequiresEncryption('documents', 'documentTitle')).toBe(false);
      expect(fieldRequiresEncryption('appointments', 'appointmentType')).toBe(false);
    });
  });

  describe('fieldRequiresAudit', () => {
    it('should return true for high sensitivity fields', () => {
      expect(fieldRequiresAudit('patient', 'patientId')).toBe(true);
      expect(fieldRequiresAudit('medical', 'diagnosis')).toBe(true);
    });

    it('should return false for medium sensitivity fields', () => {
      expect(fieldRequiresAudit('patient', 'phoneNumber')).toBe(false);
      expect(fieldRequiresAudit('medical', 'immunizations')).toBe(false);
    });

    it('should return false for low sensitivity fields', () => {
      expect(fieldRequiresAudit('documents', 'documentTitle')).toBe(false);
      expect(fieldRequiresAudit('appointments', 'appointmentType')).toBe(false);
    });
  });
});
