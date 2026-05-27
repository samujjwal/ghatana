import * as LocalAuthentication from 'expo-local-authentication';
import { t } from '../i18n/phrMobileI18n';

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