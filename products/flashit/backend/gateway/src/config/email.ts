/**
 * @fileoverview Email Configuration
 * Configuration for email service providers
 */

export const emailConfig = {
  provider: process.env.EMAIL_PROVIDER || 'stub',
  from: process.env.EMAIL_FROM || 'noreply@flashit.app',
  smtp: {
    host: process.env.SMTP_HOST,
    port: parseInt(process.env.SMTP_PORT || '587'),
    secure: process.env.SMTP_SECURE === 'true',
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  },
  ses: {
    region: process.env.AWS_REGION,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID,
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
    },
  },
};
