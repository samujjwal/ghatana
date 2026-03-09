/**
 * Rate limiting middleware with endpoint-specific limits and metrics tracking.
 *
 * <p><b>Purpose</b><br>
 * Protects API endpoints from abuse by limiting request rates per IP address.
 * Provides granular rate limits for different endpoint categories (auth, API, reports)
 * with sliding window counters and metrics tracking for monitoring.
 *
 * <p><b>Rate Limit Policies</b><br>
 * - Global API: 100 requests per 15 minutes (all /api/* endpoints)
 * - Authentication: 5 requests per 15 minutes (login, register, password reset)
 * - Report Generation: 10 requests per hour (CSV/PDF exports)
 * - Skip successful auth: Only count failed login attempts
 *
 * <p><b>Configuration</b><br>
 * Configurable via environment variables:
 * - RATE_LIMIT_WINDOW_MS: Time window in milliseconds (default 15min)
 * - RATE_LIMIT_MAX_REQUESTS: Max requests per window (default 100)
 * - AUTH_RATE_LIMIT_MAX: Auth endpoint limit (default 5)
 *
 * <p><b>Metrics & Logging</b><br>
 * Increments rateLimitExceeded metric tagged by endpoint, logs rate limit
 * violations with IP and endpoint context for security audit trail.
 *
 * <p><b>Response Format</b><br>
 * Returns 429 Too Many Requests with standard rate limit headers:
 * - X-RateLimit-Limit: Request quota
 * - X-RateLimit-Remaining: Remaining requests
 * - X-RateLimit-Reset: Unix timestamp of quota reset
 *
 * @doc.type middleware
 * @doc.purpose Rate limiting for API abuse prevention and security
 * @doc.layer backend
 * @doc.pattern Middleware
 */
import rateLimit from 'express-rate-limit';
import { logger } from '../utils/logger';
import { rateLimitExceeded } from '../utils/metrics';

/**
 * Global API rate limiter (applies to all /api/* endpoints)
 */
export const globalLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000'), // 15 minutes
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '100'),
  message: 'Too many requests from this IP, please try again later',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'global' });
    logger.warn('Rate limit exceeded', {
      ip: req.ip,
      path: req.path,
      limit: 'global',
    });
    res.status(429).json({
      error: 'Too many requests',
      message: 'Please try again later',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

/**
 * Strict rate limiter for authentication endpoints
 * Prevents brute force attacks on login/register
 */
export const authLimiter = rateLimit({
  windowMs: 900000, // 15 minutes
  max: 5, // 5 attempts per 15 minutes
  skipSuccessfulRequests: true, // Don't count successful logins
  message: 'Too many authentication attempts, please try again later',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'auth' });
    logger.warn('Auth rate limit exceeded', {
      ip: req.ip,
      path: req.path,
      email: req.body?.email,
    });
    res.status(429).json({
      error: 'Too many authentication attempts',
      message: 'Please wait 15 minutes before trying again',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

/**
 * Password reset rate limiter
 * Prevents password reset spam and enumeration attacks
 */
export const passwordResetLimiter = rateLimit({
  windowMs: 3600000, // 1 hour
  max: 3, // 3 reset emails per hour
  skipSuccessfulRequests: false, // Count all attempts
  message: 'Too many password reset requests',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'password_reset' });
    logger.warn('Password reset rate limit exceeded', {
      ip: req.ip,
      email: req.body?.email,
    });
    res.status(429).json({
      error: 'Too many password reset requests',
      message: 'Please wait 1 hour before requesting another password reset',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

/**
 * Registration rate limiter
 * Prevents spam account creation
 */
export const registrationLimiter = rateLimit({
  windowMs: 3600000, // 1 hour
  max: 10, // 10 signups per hour per IP
  skipSuccessfulRequests: false,
  message: 'Too many registration attempts',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'registration' });
    logger.warn('Registration rate limit exceeded', {
      ip: req.ip,
      email: req.body?.email,
    });
    res.status(429).json({
      error: 'Too many registration attempts',
      message: 'Please wait 1 hour before creating another account',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

/**
 * Email verification rate limiter
 * Prevents spam of verification emails
 */
export const emailVerificationLimiter = rateLimit({
  windowMs: 600000, // 10 minutes
  max: 3, // 3 verification emails per 10 minutes
  message: 'Too many verification email requests',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'email_verification' });
    logger.warn('Email verification rate limit exceeded', {
      ip: req.ip,
      email: req.body?.email,
    });
    res.status(429).json({
      error: 'Too many verification requests',
      message: 'Please wait 10 minutes before requesting another verification email',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

/**
 * Data export rate limiter
 * Prevents excessive data export requests
 */
export const dataExportLimiter = rateLimit({
  windowMs: 3600000, // 1 hour
  max: 5, // 5 exports per hour
  message: 'Too many data export requests',
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    rateLimitExceeded.inc({ endpoint: 'data_export' });
    logger.warn('Data export rate limit exceeded', {
      ip: req.ip,
      path: req.path,
    });
    res.status(429).json({
      error: 'Too many export requests',
      message: 'Please wait 1 hour before exporting more data',
      retryAfter: res.getHeader('Retry-After'),
    });
  },
});

export default {
  globalLimiter,
  authLimiter,
  passwordResetLimiter,
  registrationLimiter,
  emailVerificationLimiter,
  dataExportLimiter,
};
