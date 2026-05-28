import * as LocalAuthentication from 'expo-local-authentication';
import { t } from '../i18n/phrMobileI18n';
import {
  phiDisableBiometricPolicy,
  phiEnableBiometricPolicy,
  phiIsBiometricPolicyEnabled,
} from './phiEncryptedStorage';

/**
 * Checks if biometric authentication policy is enabled for PHI access.
 * When enabled, users must authenticate with biometrics or device passcode
 * before any PHI can be decrypted.
 */
export async function isBiometricPolicyEnabled(): Promise<boolean> {
  return phiIsBiometricPolicyEnabled();
}

/**
 * Enables the biometric authentication policy for PHI access.
 */
export async function enableBiometricPolicy(): Promise<void> {
  await phiEnableBiometricPolicy();
}

/**
 * Disables the biometric authentication policy for PHI access.
 */
export async function disableBiometricPolicy(): Promise<void> {
  await phiDisableBiometricPolicy();
}

export async function authenticateBiometric(): Promise<boolean> {
  const available = await LocalAuthentication.hasHardwareAsync();
  if (!available) {
    return false;
  }

  const enrolled = await LocalAuthentication.isEnrolledAsync();
  if (!enrolled) {
    return false;
  }

  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: t('biometric.promptMessage'),
    disableDeviceFallback: false,
  });
  return result.success;
}
