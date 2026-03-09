import { z } from "zod";

const envSchema = z.object({
  NODE_ENV: z.enum(["development", "production", "test"]).default("development"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().default(2900),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace"]).default("info"),
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRES_IN: z.string().default("7d"),
  CONTEXT_SERVICE_URL: z.string().url().default("http://localhost:8080"),
  JAVA_AGENT_SERVICE_URL: z.string().url().optional(),
  USE_JAVA_AGENT_CLASSIFICATION: z.string().optional(),
  S3_BUCKET: z.string().optional(),
  S3_REGION: z.string().default("us-east-1"),
  AWS_ACCESS_KEY_ID: z.string().optional(),
  AWS_SECRET_ACCESS_KEY: z.string().optional(),
  EMAIL_PROVIDER: z.enum(["smtp", "ses", "stub"]).optional(),
  STRIPE_SECRET_KEY: z.string().optional(),
  STRIPE_WEBHOOK_SECRET: z.string().optional(),
  STRIPE_PRICE_PRO_MONTHLY: z.string().optional(),
  STRIPE_PRICE_PRO_ANNUAL: z.string().optional(),
  STRIPE_PRICE_TEAMS_MONTHLY: z.string().optional(),
  STRIPE_PRICE_TEAMS_ANNUAL: z.string().optional(),
});

export type WebApiEnv = z.infer<typeof envSchema>;

export const loadEnv = (): WebApiEnv => {
  const result = envSchema.safeParse(process.env);
  if (!result.success) {
    console.error("❌ Invalid environment variables:");
    console.error(JSON.stringify(result.error.format(), null, 2));
    process.exit(1);
  }

  // Production-grade invariants (fail fast)
  if (result.data.NODE_ENV === 'production') {
    if (!result.data.JAVA_AGENT_SERVICE_URL) {
      console.error('❌ Missing JAVA_AGENT_SERVICE_URL in production');
      process.exit(1);
    }
    if (!result.data.EMAIL_PROVIDER || result.data.EMAIL_PROVIDER === 'stub') {
      console.error('❌ EMAIL_PROVIDER must be set to smtp|ses in production');
      process.exit(1);
    }
    if (!result.data.STRIPE_SECRET_KEY) {
      console.error('❌ Missing STRIPE_SECRET_KEY in production');
      process.exit(1);
    }
    if (!result.data.STRIPE_WEBHOOK_SECRET) {
      console.error('❌ Missing STRIPE_WEBHOOK_SECRET in production');
      process.exit(1);
    }
    // Validate Stripe price IDs are configured
    if (!result.data.STRIPE_PRICE_PRO_MONTHLY) {
      console.error('❌ Missing STRIPE_PRICE_PRO_MONTHLY in production');
      process.exit(1);
    }
    if (!result.data.STRIPE_PRICE_PRO_ANNUAL) {
      console.error('❌ Missing STRIPE_PRICE_PRO_ANNUAL in production');
      process.exit(1);
    }
    if (!result.data.STRIPE_PRICE_TEAMS_MONTHLY) {
      console.error('❌ Missing STRIPE_PRICE_TEAMS_MONTHLY in production');
      process.exit(1);
    }
    if (!result.data.STRIPE_PRICE_TEAMS_ANNUAL) {
      console.error('❌ Missing STRIPE_PRICE_TEAMS_ANNUAL in production');
      process.exit(1);
    }
  }

  return result.data;
};
