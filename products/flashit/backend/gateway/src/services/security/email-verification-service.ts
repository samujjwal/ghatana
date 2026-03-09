/**
 * Email Verification Service
 * 
 * Handles generation, sending, and validation of email verification tokens.
 * 
 * @doc.type service
 * @doc.purpose Email verification token lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */

import crypto from 'crypto';
import { prisma } from '../../lib/prisma';
import { sendEmail } from '../../lib/email';

const TOKEN_EXPIRY_HOURS = 24;
const APP_URL = process.env.APP_URL || 'http://localhost:3000';

/**
 * Generate a verification token and send the verification email.
 */
export async function sendVerificationEmail(userId: string, email: string): Promise<void> {
  // Generate a random token
  const rawToken = crypto.randomBytes(32).toString('hex');
  const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');

  const expiresAt = new Date();
  expiresAt.setHours(expiresAt.getHours() + TOKEN_EXPIRY_HOURS);

  // Invalidate any existing tokens for this user
  await prisma.emailVerificationToken.updateMany({
    where: {
      userId,
      usedAt: null,
    },
    data: {
      usedAt: new Date(), // Mark as used so they can't be reused
    },
  });

  // Create new token
  await prisma.emailVerificationToken.create({
    data: {
      userId,
      tokenHash,
      expiresAt,
    },
  });

  const verificationUrl = `${APP_URL}/verify-email?token=${rawToken}`;

  await sendEmail({
    to: email,
    subject: 'Verify your FlashIt email address',
    body: `Welcome to FlashIt!\n\nPlease verify your email by clicking the link below:\n\n${verificationUrl}\n\nThis link expires in ${TOKEN_EXPIRY_HOURS} hours.\n\nIf you did not create an account, you can safely ignore this email.`,
    html: `
      <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 40px 20px;">
        <h1 style="color: #1a1a1a; font-size: 24px; margin-bottom: 16px;">Welcome to FlashIt!</h1>
        <p style="color: #4a4a4a; font-size: 16px; line-height: 1.5;">
          Please verify your email address by clicking the button below:
        </p>
        <div style="text-align: center; margin: 32px 0;">
          <a href="${verificationUrl}" 
             style="background-color: #007AFF; color: white; padding: 14px 32px; border-radius: 8px; text-decoration: none; font-size: 16px; font-weight: 600; display: inline-block;">
            Verify Email Address
          </a>
        </div>
        <p style="color: #999; font-size: 14px; line-height: 1.5;">
          This link expires in ${TOKEN_EXPIRY_HOURS} hours. If you did not create an account, you can safely ignore this email.
        </p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;" />
        <p style="color: #999; font-size: 12px;">
          If the button doesn't work, copy and paste this link: ${verificationUrl}
        </p>
      </div>
    `,
  });
}

/**
 * Verify a token and mark the user's email as verified.
 * Returns `{ verified: true }` on success, or `{ verified: false, reason }` on failure.
 */
export async function verifyEmailToken(
  rawToken: string,
): Promise<{ verified: boolean; reason?: string; userId?: string }> {
  const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');

  const record = await prisma.emailVerificationToken.findUnique({
    where: { tokenHash },
    include: { user: { select: { id: true, emailVerified: true } } },
  });

  if (!record) {
    return { verified: false, reason: 'Invalid verification token' };
  }

  if (record.usedAt) {
    return { verified: false, reason: 'Token has already been used' };
  }

  if (record.expiresAt < new Date()) {
    return { verified: false, reason: 'Verification token has expired' };
  }

  if (record.user.emailVerified) {
    // Already verified — mark token as used and return success
    await prisma.emailVerificationToken.update({
      where: { id: record.id },
      data: { usedAt: new Date() },
    });
    return { verified: true, userId: record.userId };
  }

  // Mark user as verified and token as used in a transaction
  await prisma.$transaction([
    prisma.user.update({
      where: { id: record.userId },
      data: { emailVerified: true },
    }),
    prisma.emailVerificationToken.update({
      where: { id: record.id },
      data: { usedAt: new Date() },
    }),
    prisma.auditEvent.create({
      data: {
        eventType: 'EMAIL_VERIFIED',
        userId: record.userId,
        actor: record.userId,
        action: 'VERIFY_EMAIL',
        resourceType: 'USER',
        resourceId: record.userId,
      },
    }),
  ]);

  return { verified: true, userId: record.userId };
}

/**
 * Resend verification email for a user (rate-limited to 1 per 5 minutes).
 */
export async function resendVerificationEmail(
  userId: string,
  email: string,
): Promise<{ sent: boolean; reason?: string }> {
  // Check if already verified
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { emailVerified: true },
  });

  if (user?.emailVerified) {
    return { sent: false, reason: 'Email is already verified' };
  }

  // Rate limit: check most recent token
  const recentToken = await prisma.emailVerificationToken.findFirst({
    where: { userId, usedAt: null },
    orderBy: { createdAt: 'desc' },
  });

  if (recentToken) {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
    if (recentToken.createdAt > fiveMinutesAgo) {
      return { sent: false, reason: 'Verification email was sent recently. Please wait a few minutes before requesting again.' };
    }
  }

  await sendVerificationEmail(userId, email);
  return { sent: true };
}
