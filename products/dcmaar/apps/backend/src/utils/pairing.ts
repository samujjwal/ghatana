/**
 * Device pairing code generation and validation utilities.
 *
 * <p><b>Purpose</b><br>
 * Provides secure 6-digit pairing code generation for device registration flow.
 * Parents enter pairing codes on mobile/desktop devices to link them with their
 * Guardian account, enabling device monitoring and policy enforcement.
 *
 * <p><b>Pairing Flow</b><br>
 * 1. Backend generates random 6-digit code with 15-minute expiration
 * 2. Parent displays code on device to be paired
 * 3. Parent enters code in Guardian mobile app
 * 4. Backend validates code and associates device with parent account
 * 5. Device begins reporting usage data and receiving policies
 *
 * <p><b>Security</b><br>
 * - 6-digit numeric codes (1 million combinations)
 * - 15-minute expiration window (prevents reuse attacks)
 * - One-time use (code invalidated after successful pairing)
 * - Random generation using Math.random() (sufficient for pairing codes)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const code = generatePairingCode(); // "482916"
 * const expiresAt = getPairingExpiration(); // 15 minutes from now
 * await savePairingCode(deviceId, code, expiresAt);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Device pairing code generation and expiration
 * @doc.layer backend
 * @doc.pattern Utility
 */

/**
 * Generate a random 6-digit pairing code
 */
export function generatePairingCode(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

/**
 * Generate pairing code expiration time (15 minutes from now)
 */
export function getPairingExpiration(): Date {
  const now = new Date();
  now.setMinutes(now.getMinutes() + 15);
  return now;
}

/**
 * Check if pairing code has expired
 */
export function isPairingExpired(expiresAt: Date | string): boolean {
  const expiry = typeof expiresAt === 'string' ? new Date(expiresAt) : expiresAt;
  return expiry < new Date();
}

/**
 * Validate pairing code format
 */
export function isValidPairingCodeFormat(code: string): boolean {
  return /^\d{6}$/.test(code);
}
