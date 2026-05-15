/**
 * @fileoverview Two-Factor Authentication (2FA) Service for Flashit
 * Implements TOTP-based 2FA using speakeasy
 *
 * @doc.type service
 * @doc.purpose Provide 2FA verification for enhanced account security
 * @doc.layer backend
 * @doc.pattern Security Service
 */

import { generateSecret, generateURI, verifySync } from "otplib";
import QRCode from "qrcode";
import { prisma } from "./prisma";
import { comparePassword } from "./auth";
import { Logger } from "./logger";

const logger = Logger.create({ component: "TOTPService" });

export interface TOTPSetupResult {
  secret: string;
  qrCodeUrl: string;
  backupCodes: string[];
}

export interface TOTPVerifyResult {
  valid: boolean;
  remainingAttempts?: number;
}

/**
 * Generate a new TOTP secret for a user
 * @doc.purpose Initialize 2FA for a user account
 */
export async function generateTOTPSecret(
  userId: string,
): Promise<TOTPSetupResult> {
  // Generate secret
  const secret = generateSecret();

  // Generate backup codes
  const backupCodes = generateBackupCodes();

  // Store secret in database (encrypted)
  await prisma.twoFactorAuth.upsert({
    where: { userId },
    create: {
      userId,
      secretKey: secret,
      enabled: false,
      backupCodes,
    },
    update: {
      secretKey: secret,
      enabled: false,
      backupCodes,
      verifiedAt: null,
    },
  });

  // Generate QR code URL
  const otpauthUrl = generateURI({
    issuer: "Flashit",
    label: userId,
    secret,
  });
  const qrCodeUrl = await QRCode.toDataURL(otpauthUrl);

  logger.info("TOTP secret generated", { userId });

  return {
    secret,
    qrCodeUrl,
    backupCodes,
  };
}

/**
 * Verify TOTP token during setup
 * @doc.purpose Confirm user has correctly configured 2FA
 */
export async function verifyTOTPSetup(
  userId: string,
  token: string,
): Promise<boolean> {
  const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
    where: { userId },
  });

  if (!twoFactorAuth?.secretKey) {
    throw new Error("TOTP not initialized for user");
  }

  if (twoFactorAuth.enabled) {
    throw new Error("TOTP already enabled");
  }

  const verified = verifySync({
    token,
    secret: twoFactorAuth.secretKey,
  }).valid;

  if (verified) {
    // Enable 2FA
    await prisma.twoFactorAuth.update({
      where: { userId },
      data: {
        enabled: true,
        verifiedAt: new Date(),
      },
    });

    await prisma.user.update({
      where: { id: userId },
      data: { twoFactorEnabled: true },
    });

    logger.info("TOTP enabled for user", { userId });
  }

  return verified;
}

/**
 * Verify TOTP token during login
 * @doc.purpose Validate 2FA code during authentication
 */
export async function verifyTOTP(
  userId: string,
  token: string,
): Promise<TOTPVerifyResult> {
  const [user, twoFactorAuth] = await Promise.all([
    prisma.user.findUnique({
      where: { id: userId },
      select: {
        failedLoginAttempts: true,
        lockedUntil: true,
      },
    }),
    prisma.twoFactorAuth.findUnique({ where: { userId } }),
  ]);

  if (!twoFactorAuth?.enabled || !twoFactorAuth.secretKey) {
    return { valid: false };
  }

  // Check if account is locked
  if (user?.lockedUntil && user.lockedUntil > new Date()) {
    throw new Error("Account temporarily locked due to failed 2FA attempts");
  }

  // Verify token
  const verified = verifySync({
    token,
    secret: twoFactorAuth.secretKey,
  }).valid;

  // Check backup codes
  if (!verified) {
    const backupVerified = await verifyBackupCode(userId, token);
    if (backupVerified) {
      return { valid: true };
    }
  }

  if (!verified) {
    // Increment failed attempts
    const failedAttempts = (user?.failedLoginAttempts || 0) + 1;
    const lockUntil =
      failedAttempts >= 5
        ? new Date(Date.now() + 15 * 60 * 1000) // Lock for 15 minutes
        : undefined;

    await prisma.user.update({
      where: { id: userId },
      data: {
        failedLoginAttempts: failedAttempts,
        lockedUntil: lockUntil,
      },
    });

    logger.warn("Failed TOTP attempt", {
      userId,
      failedAttempts,
      locked: !!lockUntil,
    });

    return {
      valid: false,
      remainingAttempts: 5 - failedAttempts,
    };
  }

  // Reset failed attempts on success
  if (user?.failedLoginAttempts && user.failedLoginAttempts > 0) {
    await prisma.user.update({
      where: { id: userId },
      data: {
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
    });
  }

  return { valid: true };
}

/**
 * Disable TOTP for a user
 * @doc.purpose Allow users to disable 2FA (requires password verification)
 */
export async function disableTOTP(
  userId: string,
  password: string,
): Promise<boolean> {
  // Verify password first
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { passwordHash: true, twoFactorEnabled: true },
  });

  if (!user?.twoFactorEnabled) {
    return false;
  }

  // Implementation note: Verify password here
  const validPassword = await comparePassword(password, user.passwordHash);
  if (!validPassword) {
    throw new Error("Invalid password");
  }

  // Disable 2FA
  await prisma.twoFactorAuth.update({
    where: { userId },
    data: {
      enabled: false,
      secretKey: null,
      backupCodes: [],
      verifiedAt: null,
    },
  });

  await prisma.user.update({
    where: { id: userId },
    data: { twoFactorEnabled: false },
  });

  logger.info("TOTP disabled for user", { userId });

  return true;
}

/**
 * Verify a backup code
 * @doc.purpose Allow users to use backup codes if they lose their authenticator
 */
async function verifyBackupCode(
  userId: string,
  code: string,
): Promise<boolean> {
  const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
    where: { userId },
    select: { backupCodes: true },
  });

  if (!twoFactorAuth?.backupCodes.length) {
    return false;
  }

  const backupCodes = [...twoFactorAuth.backupCodes];
  const index = backupCodes.indexOf(code);

  if (index === -1) {
    return false;
  }

  // Remove used backup code
  backupCodes.splice(index, 1);

  await prisma.twoFactorAuth.update({
    where: { userId },
    data: {
      backupCodes,
    },
  });

  logger.info("Backup code used for 2FA", {
    userId,
    remainingCodes: backupCodes.length,
  });

  return true;
}

/**
 * Generate backup codes
 * @doc.purpose Create one-time use backup codes for account recovery
 */
function generateBackupCodes(): string[] {
  const codes: string[] = [];
  for (let i = 0; i < 10; i++) {
    // Generate 8-character alphanumeric codes
    const code = Array.from({ length: 8 }, () =>
      Math.random().toString(36).charAt(2),
    )
      .join("")
      .toUpperCase();
    codes.push(code);
  }
  return codes;
}

/**
 * Check if user has 2FA enabled
 * @doc.purpose Check 2FA status for login flow
 */
export async function isTOTPEnabled(userId: string): Promise<boolean> {
  const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
    where: { userId },
    select: { enabled: true },
  });

  return twoFactorAuth?.enabled || false;
}

/**
 * Regenerate backup codes
 * @doc.purpose Allow users to get new backup codes (invalidates old ones)
 */
export async function regenerateBackupCodes(userId: string): Promise<string[]> {
  const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
    where: { userId },
    select: { enabled: true },
  });

  if (!twoFactorAuth?.enabled) {
    throw new Error("TOTP not enabled");
  }

  const newCodes = generateBackupCodes();

  await prisma.twoFactorAuth.update({
    where: { userId },
    data: {
      backupCodes: newCodes,
    },
  });

  logger.info("Backup codes regenerated", { userId });

  return newCodes;
}
