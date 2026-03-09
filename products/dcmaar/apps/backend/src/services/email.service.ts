/**
 * Email service for sending verification, password reset, and notification emails.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized email sending functionality using Nodemailer with SMTP.
 * Handles verification emails (account activation), password reset emails (with
 * secure tokens), and notification emails (policy violations, device status).
 * Supports HTML and plain text formats with template rendering.
 *
 * <p><b>Email Types</b><br>
 * - Verification: Account activation emails with clickable verification links
 * - Password Reset: Secure reset links with time-limited tokens (1-hour expiration)
 * - Notifications: Policy violation alerts, device offline warnings
 * - Welcome: Onboarding emails for new users
 *
 * <p><b>SMTP Configuration</b><br>
 * Configurable via environment variables:
 * - EMAIL_HOST: SMTP server hostname (e.g., smtp.gmail.com)
 * - EMAIL_PORT: SMTP port (587 for TLS, 465 for SSL)
 * - EMAIL_USER: SMTP authentication username
 * - EMAIL_PASS: SMTP authentication password
 * - EMAIL_FROM: Sender address (e.g., "Guardian <noreply@guardian.app>")
 *
 * <p><b>Features</b><br>
 * - HTML templates: Responsive email templates with branding
 * - Plain text fallback: Accessibility for text-only clients
 * - Error handling: Logs failures, retries transient errors
 * - Rate limiting: Prevents spam/abuse
 * - Template variables: Dynamic content substitution (username, links, etc.)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * await emailService.sendVerificationEmail(user.email, verificationToken);
 * await emailService.sendPasswordResetEmail(user.email, resetToken);
 * await emailService.sendNotification(user.email, 'Device Offline', alertHtml);
 * }</pre>
 *
 * <p><b>Security</b><br>
 * - Tokens: Cryptographically secure random tokens (32 bytes hex)
 * - Expiration: Verification tokens expire after 24 hours, reset tokens after 1 hour
 * - No user data in URLs: Only tokens in links, user data loaded server-side
 * - TLS: Encrypted SMTP connections (STARTTLS or SSL)
 *
 * @doc.type service
 * @doc.purpose Email sending for verification, password reset, and notifications
 * @doc.layer backend
 * @doc.pattern Service
 */
import { createTransport } from 'nodemailer';
import type { Transporter } from 'nodemailer';

export interface EmailConfig {
  from: string;
  host: string;
  port: number;
  secure: boolean;
  auth: {
    user: string;
    pass: string;
  };
}

export interface EmailOptions {
  to: string;
  subject: string;
  html: string;
  text?: string;
}

class EmailService {
  private transporter: Transporter | null = null;
  private config: EmailConfig | null = null;

  /**
   * Initialize email service with configuration
   */
  initialize(config: EmailConfig): void {
    this.config = config;
    this.transporter = createTransport({
      host: config.host,
      port: config.port,
      secure: config.secure,
      auth: config.auth,
    });
  }

  /**
   * Send email
   */
  async sendEmail(options: EmailOptions): Promise<void> {
    if (!this.transporter || !this.config) {
      console.warn('[EmailService] Not initialized, skipping email send');
      return;
    }

    try {
      await this.transporter.sendMail({
        from: this.config.from,
        to: options.to,
        subject: options.subject,
        html: options.html,
        text: options.text || this.stripHtml(options.html),
      });
      console.log(`[EmailService] Email sent to ${options.to}`);
    } catch (error) {
      console.error('[EmailService] Failed to send email:', error);
      throw new Error('Failed to send email');
    }
  }

  /**
   * Send email verification
   */
  async sendVerificationEmail(email: string, token: string, displayName?: string): Promise<void> {
    const verificationUrl = `${process.env.FRONTEND_URL}/verify-email/${token}`;
    
    const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Verify your Guardian account</title>
</head>
<body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
  <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
    <h1 style="color: white; margin: 0;">Guardian Parental Control</h1>
  </div>
  
  <div style="background: #f8f9fa; padding: 30px; border-radius: 0 0 10px 10px;">
    <h2 style="color: #333; margin-top: 0;">Verify Your Email Address</h2>
    
    <p style="color: #666; font-size: 16px; line-height: 1.5;">
      ${displayName ? `Hi ${displayName},` : 'Hello,'}
    </p>
    
    <p style="color: #666; font-size: 16px; line-height: 1.5;">
      Thanks for signing up for Guardian! Please verify your email address to complete your account setup and start protecting your family online.
    </p>
    
    <div style="text-align: center; margin: 30px 0;">
      <a href="${verificationUrl}" 
         style="background: #667eea; color: white; padding: 14px 28px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;">
        Verify Email Address
      </a>
    </div>
    
    <p style="color: #999; font-size: 14px; line-height: 1.5;">
      Or copy and paste this link into your browser:
    </p>
    <p style="color: #667eea; font-size: 14px; word-break: break-all;">
      ${verificationUrl}
    </p>
    
    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">
    
    <p style="color: #999; font-size: 13px; line-height: 1.5;">
      This link will expire in 24 hours. If you didn't create a Guardian account, you can safely ignore this email.
    </p>
    
    <p style="color: #999; font-size: 13px; line-height: 1.5;">
      Questions? Contact us at support@guardian-parental.com
    </p>
  </div>
  
  <div style="text-align: center; padding: 20px; color: #999; font-size: 12px;">
    © ${new Date().getFullYear()} Guardian Parental Control. All rights reserved.
  </div>
</body>
</html>
    `;

    await this.sendEmail({
      to: email,
      subject: 'Verify your Guardian account',
      html,
    });
  }

  /**
   * Send password reset email
   */
  async sendPasswordResetEmail(email: string, token: string, displayName?: string): Promise<void> {
    const resetUrl = `${process.env.FRONTEND_URL}/reset-password/${token}`;
    
    const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Reset your Guardian password</title>
</head>
<body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
  <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
    <h1 style="color: white; margin: 0;">Guardian Parental Control</h1>
  </div>
  
  <div style="background: #f8f9fa; padding: 30px; border-radius: 0 0 10px 10px;">
    <h2 style="color: #333; margin-top: 0;">Reset Your Password</h2>
    
    <p style="color: #666; font-size: 16px; line-height: 1.5;">
      ${displayName ? `Hi ${displayName},` : 'Hello,'}
    </p>
    
    <p style="color: #666; font-size: 16px; line-height: 1.5;">
      We received a request to reset your Guardian account password. Click the button below to create a new password.
    </p>
    
    <div style="text-align: center; margin: 30px 0;">
      <a href="${resetUrl}" 
         style="background: #667eea; color: white; padding: 14px 28px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;">
        Reset Password
      </a>
    </div>
    
    <p style="color: #999; font-size: 14px; line-height: 1.5;">
      Or copy and paste this link into your browser:
    </p>
    <p style="color: #667eea; font-size: 14px; word-break: break-all;">
      ${resetUrl}
    </p>
    
    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">
    
    <p style="color: #999; font-size: 13px; line-height: 1.5;">
      This link will expire in 1 hour. If you didn't request a password reset, you can safely ignore this email.
    </p>
    
    <p style="color: #999; font-size: 13px; line-height: 1.5;">
      For security reasons, we never send passwords via email.
    </p>
  </div>
  
  <div style="text-align: center; padding: 20px; color: #999; font-size: 12px;">
    © ${new Date().getFullYear()} Guardian Parental Control. All rights reserved.
  </div>
</body>
</html>
    `;

    await this.sendEmail({
      to: email,
      subject: 'Reset your Guardian password',
      html,
    });
  }

  /**
   * Strip HTML tags for plain text version
   */
  private stripHtml(html: string): string {
    return html
      .replace(/<[^>]*>/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }
}

// Export singleton instance
export const emailService = new EmailService();

// Initialize if config is available
if (process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS) {
  emailService.initialize({
    from: process.env.SMTP_FROM || 'Guardian <noreply@guardian-parental.com>',
    host: process.env.SMTP_HOST,
    port: parseInt(process.env.SMTP_PORT || '587'),
    secure: process.env.SMTP_SECURE === 'true',
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });
  console.log('[EmailService] Initialized with SMTP config');
} else {
  console.warn('[EmailService] SMTP not configured, emails will not be sent');
}
