import * as LocalAuthentication from 'expo-local-authentication';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { t } from '../i18n/phrMobileI18n';

const BIOMETRIC_POLICY_KEY = 'phr-biometric-policy-enabled';

/**
 * Checks if biometric authentication policy is enabled for PHI access.
 * When enabled, users must authenticate with biometrics or device passcode
 * before any PHI can be decrypted.
 */
export async function isBiometricPolicyEnabled(): Promise<boolean> {
  try {
    const value = await AsyncStorage.getItem(BIOMETRIC_POLICY_KEY);
    return value === 'true';
  } catch {
    return false;
  }
}

/**
 * Enables the biometric authentication policy for PHI access.
 */
export async function enableBiometricPolicy(): Promise<void> {
  await AsyncStorage.setItem(BIOMETRIC_POLICY_KEY, 'true');
}

/**
 * Disables the biometric authentication policy for PHI access.
 */
export async function disableBiometricPolicy(): Promise<void> {
  await AsyncStorage.setItem(BIOMETRIC_POLICY_KEY, 'false');
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