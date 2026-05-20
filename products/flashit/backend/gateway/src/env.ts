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

const writeStartupError = (message: string): void => {
  process.stderr.write(`${message}\n`);
};

const requireProductionValue = (
  value: unknown,
  message: string,
): void => {
  if (!value) {
    writeStartupError(message);
    process.exit(1);
  }
};

export const loadEnv = (): WebApiEnv => {
  const result = envSchema.safeParse(process.env);
  if (!result.success) {
    writeStartupError("Invalid environment variables:");
    writeStartupError(JSON.stringify(result.error.format(), null, 2));
    process.exit(1);
  }

  if (result.data.NODE_ENV === "production") {
    requireProductionValue(
      result.data.JAVA_AGENT_SERVICE_URL,
      "Missing JAVA_AGENT_SERVICE_URL in production",
    );

    if (!result.data.EMAIL_PROVIDER || result.data.EMAIL_PROVIDER === "stub") {
      writeStartupError("EMAIL_PROVIDER must be set to smtp|ses in production");
      process.exit(1);
    }

    requireProductionValue(
      result.data.STRIPE_SECRET_KEY,
      "Missing STRIPE_SECRET_KEY in production",
    );
    requireProductionValue(
      result.data.STRIPE_WEBHOOK_SECRET,
      "Missing STRIPE_WEBHOOK_SECRET in production",
    );
    requireProductionValue(
      result.data.STRIPE_PRICE_PRO_MONTHLY,
      "Missing STRIPE_PRICE_PRO_MONTHLY in production",
    );
    requireProductionValue(
      result.data.STRIPE_PRICE_PRO_ANNUAL,
      "Missing STRIPE_PRICE_PRO_ANNUAL in production",
    );
    requireProductionValue(
      result.data.STRIPE_PRICE_TEAMS_MONTHLY,
      "Missing STRIPE_PRICE_TEAMS_MONTHLY in production",
    );
    requireProductionValue(
      result.data.STRIPE_PRICE_TEAMS_ANNUAL,
      "Missing STRIPE_PRICE_TEAMS_ANNUAL in production",
    );
  }

  return result.data;
};
