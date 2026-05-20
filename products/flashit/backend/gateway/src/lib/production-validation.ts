/**
 * @fileoverview Production startup validation for Flashit
 * Ensures critical configuration is present before starting in production
 *
 * @doc.type module
 * @doc.purpose Production safety checks
 * @doc.layer infrastructure
 */

import { verifyEmailConfig } from '../lib/email.js';

interface ValidationError {
  code: string;
  message: string;
  severity: 'critical' | 'warning';
}

interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationError[];
}

const writeValidationLine = (message = ''): void => {
  process.stdout.write(`${message}\n`);
};

/**
 * Validate production configuration
 * @doc.method validateProductionConfig
 */
export async function validateProductionConfig(): Promise<ValidationResult> {
  const errors: ValidationError[] = [];
  const warnings: ValidationError[] = [];

  if (process.env.NODE_ENV !== 'production') {
    return { valid: true, errors, warnings };
  }

  writeValidationLine('Running production configuration validation...');
  writeValidationLine();

  const emailProvider = process.env.EMAIL_PROVIDER;
  if (!emailProvider || emailProvider === 'stub') {
    errors.push({
      code: 'EMAIL_STUB_IN_PRODUCTION',
      message: 'EMAIL_PROVIDER must be set to "smtp" or "ses" in production. Stub provider is not allowed.',
      severity: 'critical',
    });
  } else if (emailProvider === 'smtp') {
    if (!process.env.SMTP_HOST) {
      errors.push({
        code: 'SMTP_HOST_MISSING',
        message: 'SMTP_HOST is required when EMAIL_PROVIDER=smtp',
        severity: 'critical',
      });
    }
    if (!process.env.SMTP_USER || !process.env.SMTP_PASS) {
      errors.push({
        code: 'SMTP_AUTH_MISSING',
        message: 'SMTP_USER and SMTP_PASS are required for SMTP authentication',
        severity: 'critical',
      });
    }
  } else if (emailProvider === 'ses' && !process.env.AWS_REGION) {
    errors.push({
      code: 'AWS_REGION_MISSING',
      message: 'AWS_REGION is required when EMAIL_PROVIDER=ses',
      severity: 'critical',
    });
  }

  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
    errors.push({
      code: 'JWT_SECRET_WEAK',
      message: 'JWT_SECRET must be set and at least 32 characters long',
      severity: 'critical',
    });
  }

  if (!process.env.DATABASE_URL) {
    errors.push({
      code: 'DATABASE_URL_MISSING',
      message: 'DATABASE_URL is required for database connection',
      severity: 'critical',
    });
  }

  if (!process.env.REDIS_HOST) {
    warnings.push({
      code: 'REDIS_HOST_MISSING',
      message: 'REDIS_HOST not set, using default localhost',
      severity: 'warning',
    });
  }

  if (process.env.ENABLE_BILLING === 'true') {
    if (!process.env.STRIPE_SECRET_KEY) {
      errors.push({
        code: 'STRIPE_KEY_MISSING',
        message: 'STRIPE_SECRET_KEY is required when ENABLE_BILLING=true',
        severity: 'critical',
      });
    }
    if (!process.env.STRIPE_WEBHOOK_SECRET) {
      warnings.push({
        code: 'STRIPE_WEBHOOK_MISSING',
        message: 'STRIPE_WEBHOOK_SECRET is recommended for webhook security',
        severity: 'warning',
      });
    }
  }

  if (!process.env.OPENAI_API_KEY && !process.env.OLLAMA_BASE_URL) {
    warnings.push({
      code: 'AI_PROVIDER_MISSING',
      message: 'No AI provider configured (OPENAI_API_KEY or OLLAMA_BASE_URL)',
      severity: 'warning',
    });
  }

  if (emailProvider && emailProvider !== 'stub') {
    try {
      const emailValid = await verifyEmailConfig();
      if (!emailValid) {
        errors.push({
          code: 'EMAIL_VERIFICATION_FAILED',
          message: `Failed to verify ${emailProvider} configuration`,
          severity: 'critical',
        });
      }
    } catch (error) {
      errors.push({
        code: 'EMAIL_CONNECTION_FAILED',
        message: `Email service connection failed: ${error instanceof Error ? error.message : String(error)}`,
        severity: 'critical',
      });
    }
  }

  if (process.env.ALLOW_UNAUTHENTICATED_ACCESS === 'true') {
    errors.push({
      code: 'UNAUTHENTICATED_ACCESS_ENABLED',
      message: 'ALLOW_UNAUTHENTICATED_ACCESS=true is not allowed in production',
      severity: 'critical',
    });
  }

  if (process.env.DISABLE_RATE_LIMITING === 'true') {
    errors.push({
      code: 'RATE_LIMITING_DISABLED',
      message: 'DISABLE_RATE_LIMITING=true is not allowed in production',
      severity: 'critical',
    });
  }

  writeValidationLine('='.repeat(60));
  writeValidationLine('PRODUCTION CONFIGURATION VALIDATION RESULTS');
  writeValidationLine('='.repeat(60));

  if (errors.length > 0) {
    writeValidationLine();
    writeValidationLine(`CRITICAL ERRORS (${errors.length}):`);
    for (const error of errors) {
      writeValidationLine(`   [${error.code}] ${error.message}`);
    }
  }

  if (warnings.length > 0) {
    writeValidationLine();
    writeValidationLine(`WARNINGS (${warnings.length}):`);
    for (const warning of warnings) {
      writeValidationLine(`   [${warning.code}] ${warning.message}`);
    }
  }

  if (errors.length === 0 && warnings.length === 0) {
    writeValidationLine();
    writeValidationLine('All production configuration checks passed.');
  }

  writeValidationLine();
  writeValidationLine('='.repeat(60));

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Assert production configuration is valid
 * Throws error if validation fails
 * @doc.method assertProductionConfig
 */
export async function assertProductionConfig(): Promise<void> {
  const result = await validateProductionConfig();

  if (!result.valid) {
    const errorMessages = result.errors.map((error) => `[${error.code}] ${error.message}`).join('\n');
    throw new Error(
      `PRODUCTION CONFIGURATION INVALID - Startup aborted:\n${errorMessages}\n\n` +
      `Set the required environment variables before starting the server.`,
    );
  }
}
