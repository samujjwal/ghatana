import * as LocalAuthentication from 'expo-local-authentication';

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
    promptMessage: 'Authenticate to unlock your health record',
    disableDeviceFallback: false,
  });
  return result.success;
}