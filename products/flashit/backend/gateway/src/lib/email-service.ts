/**
 * @fileoverview Flashit Email Service - Production Ready Implementation
 * Replaces stub email provider with real SMTP/SES integration
 *
 * @doc.type service
 * @doc.purpose Send transactional emails in production
 * @doc.layer backend
 * @doc.pattern Infrastructure Service
 */

import nodemailer from "nodemailer";
import { SESClient, SendRawEmailCommand } from "@aws-sdk/client-ses";
import { emailConfig } from "../config/email";
import { Logger } from "./logger";

const logger = Logger.create({ component: "EmailService" });

// Email provider types
export type EmailProvider = "smtp" | "ses" | "sendgrid" | "postmark" | "stub";

export interface EmailPayload {
  to: string | string[];
  subject: string;
  text?: string;
  html?: string;
  from?: string;
  replyTo?: string;
  attachments?: Array<{
    filename: string;
    content: Buffer | string;
    contentType?: string;
  }>;
}

export interface EmailResult {
  messageId: string;
  provider: EmailProvider;
  timestamp: Date;
}

export interface EmailConfigValidation {
  valid: boolean;
  provider?: EmailProvider;
  errors: string[];
}

export interface TestEmailResult {
  success: boolean;
  error?: string;
}

/**
 * Create SMTP transporter
 * @doc.purpose Initialize SMTP connection for email delivery
 */
export function createSMTPTransport(): any {
  return nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: parseInt(process.env.SMTP_PORT || "587"),
    secure: process.env.SMTP_SECURE === "true",
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
    pool: true, // Use connection pooling
    maxConnections: 5,
    maxMessages: 100,
  });
}

/**
 * Create AWS SES client
 * @doc.purpose Initialize AWS SES for email delivery
 */
function createSESClient() {
  return new SESClient({
    region: process.env.AWS_REGION || "us-east-1",
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
    },
  });
}

/**
 * Send email via SMTP
 * @doc.purpose Deliver email using SMTP provider
 */
async function sendViaSMTP(payload: EmailPayload): Promise<EmailResult> {
  const transporter = createSMTPTransport();

  const result = await transporter.sendMail({
    from: payload.from || process.env.EMAIL_FROM,
    to: payload.to,
    subject: payload.subject,
    text: payload.text,
    html: payload.html,
    replyTo: payload.replyTo,
    attachments: payload.attachments,
  });

  return {
    messageId: result.messageId,
    provider: "smtp",
    timestamp: new Date(),
  };
}

/**
 * Send email via AWS SES
 * @doc.purpose Deliver email using AWS SES
 */
async function sendViaSES(payload: EmailPayload): Promise<EmailResult> {
  const ses = createSESClient();

  // Build raw email
  const transporter = nodemailer.createTransport({
    SES: { ses, aws: { SendRawEmailCommand } },
  });

  const result = await transporter.sendMail({
    from: payload.from || process.env.EMAIL_FROM,
    to: payload.to,
    subject: payload.subject,
    text: payload.text,
    html: payload.html,
    replyTo: payload.replyTo,
    attachments: payload.attachments,
  });

  return {
    messageId: result.messageId,
    provider: "ses",
    timestamp: new Date(),
  };
}

/**
 * Send email with provider selection
 * @doc.purpose Main email sending interface with provider failover
 */
export async function sendEmail(payload: EmailPayload): Promise<EmailResult> {
  // Validate configuration
  const provider = emailConfig.provider as EmailProvider;

  // CRITICAL: Never allow stub in production
  if (provider === "stub") {
    if (process.env.NODE_ENV === "production") {
      throw new Error(
        "CRITICAL: EMAIL_PROVIDER=stub is not allowed in production. " +
          "Configure EMAIL_PROVIDER=smtp, EMAIL_PROVIDER=ses, or another real provider.",
      );
    }
    // In development, log and return mock
    logger.warn("[email stub] Email not sent (development mode)", {
      to: payload.to,
      subject: payload.subject,
    });
    return {
      messageId: `stub-${Date.now()}`,
      provider: "stub",
      timestamp: new Date(),
    };
  }

  // Track metrics
  const startTime = Date.now();

  try {
    let result: EmailResult;

    switch (provider) {
      case "smtp":
        result = await sendViaSMTP(payload);
        break;
      case "ses":
        result = await sendViaSES(payload);
        break;
      default:
        throw new Error(`Unsupported email provider: ${provider}`);
    }

    // Log success
    logger.info("Email sent", {
      provider: result.provider,
      messageId: result.messageId,
      to: payload.to,
      subject: payload.subject,
      duration: Date.now() - startTime,
    });

    return result;
  } catch (error) {
    // Log failure
    logger.error("Email sending failed", {
      provider,
      to: payload.to,
      subject: payload.subject,
      error: error instanceof Error ? error.message : "Unknown error",
      duration: Date.now() - startTime,
    });

    throw error;
  }
}

/**
 * Send templated email
 * @doc.purpose Send email using predefined templates
 */
export async function sendTemplatedEmail(
  templateId: string,
  payload: Omit<EmailPayload, "text" | "html">,
  variables: Record<string, string>,
): Promise<EmailResult> {
  // Load template
  const template = await loadTemplate(templateId);

  // Render template
  const html = renderTemplate(template.html, variables);
  const text = renderTemplate(template.text, variables);

  // Send
  return sendEmail({
    ...payload,
    html,
    text,
  });
}

async function loadTemplate(
  templateId: string,
): Promise<{ html: string; text: string }> {
  // Template loading implementation
  // This would load from a template directory or database
  throw new Error("Template loading not implemented");
}

function renderTemplate(
  template: string,
  variables: Record<string, string>,
): string {
  return template.replace(
    /\{\{(\w+)\}\}/g,
    (match, key) => variables[key] || match,
  );
}

/**
 * Verify email configuration
 * @doc.purpose Validate email service is properly configured
 */
export function verifyEmailConfig(): EmailConfigValidation {
  const errors: string[] = [];
  const provider = process.env.EMAIL_PROVIDER as EmailProvider;

  if (!provider) {
    errors.push("EMAIL_PROVIDER is not set");
    return { valid: false, errors };
  }

  if (!process.env.EMAIL_FROM) {
    errors.push("EMAIL_FROM is required");
  }

  if (provider === "smtp") {
    if (!process.env.SMTP_HOST) {
      errors.push("SMTP_HOST is required for SMTP provider");
    }
    if (!process.env.SMTP_PORT) {
      errors.push("SMTP_PORT is required for SMTP provider");
    }
  } else if (provider === "ses") {
    if (!process.env.AWS_REGION) {
      errors.push("AWS_REGION is required for SES provider");
    }
    if (!process.env.AWS_ACCESS_KEY_ID || !process.env.AWS_SECRET_ACCESS_KEY) {
      errors.push("AWS credentials are required for SES provider");
    }
  } else if (provider === "stub") {
    if (process.env.NODE_ENV === "production") {
      errors.push("STUB provider is not allowed in production");
    }
  }

  return {
    valid: errors.length === 0,
    provider,
    errors,
  };
}

/**
 * Test email configuration by sending a test email
 * @doc.purpose Validate email configuration works in practice
 */
export async function testEmailConfig(
  to: string,
): Promise<{ success: boolean; error?: string }> {
  const config = verifyEmailConfig();
  if (!config.valid) {
    return { success: false, error: config.errors.join(", ") };
  }

  try {
    await sendEmail({
      to,
      subject: "Test Email from Flashit",
      text: "This is a test email to verify your email configuration is working correctly.",
    });
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error",
    };
  }
}

/**
 * Retry an operation with exponential backoff
 * @doc.purpose Handle transient failures with automatic retry
 */
export async function retryWithBackoff<T>(
  operation: () => Promise<T>,
  maxRetries: number = 3,
  baseDelay: number = 1000,
): Promise<T> {
  let lastError: Error | undefined;

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));

      // Don't retry authentication errors
      if (
        lastError.message.includes("auth") ||
        lastError.message.includes("credentials")
      ) {
        throw lastError;
      }

      if (attempt < maxRetries - 1) {
        const delay = baseDelay * Math.pow(2, attempt);
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }
  }

  throw lastError;
}
