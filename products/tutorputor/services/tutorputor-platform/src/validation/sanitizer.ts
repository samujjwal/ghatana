/**
 * Input Sanitization Utilities
 *
 * Provides sanitization functions for user input to prevent XSS, SQL injection,
 * and other security vulnerabilities.
 *
 * @doc.type module
 * @doc.purpose Input sanitization for security
 * @doc.layer platform
 * @doc.pattern Utility
 */

/**
 * Sanitize HTML content to prevent XSS attacks
 */
export function sanitizeHtml(input: string): string {
  if (!input) return '';
  
  // Remove potentially dangerous HTML tags and attributes
  return input
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
    .replace(/<object\b[^<]*(?:(?!<\/object>)<[^<]*)*<\/object>/gi, '')
    .replace(/<embed\b[^<]*(?:(?!<\/embed>)<[^<]*)*<\/embed>/gi, '')
    .replace(/on\w+="[^"]*"/gi, '') // Remove event handlers
    .replace(/on\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/vbscript:/gi, '')
    .replace(/data:/gi, '');
}

/**
 * Sanitize user input for database queries
 */
export function sanitizeSqlInput(input: string): string {
  if (!input) return '';
  
  // Remove SQL injection patterns
  return input
    .replace(/['";\\]/g, '') // Remove quotes and backslashes
    .replace(/--/g, '') // Remove SQL comments
    .replace(/\/\*/g, '') // Remove block comment start
    .replace(/\*\//g, '') // Remove block comment end
    .trim();
}

/**
 * Sanitize email address
 */
export function sanitizeEmail(input: string): string {
  if (!input) return '';
  
  return input
    .toLowerCase()
    .trim()
    .replace(/[^\w\.\-@]/g, ''); // Keep only valid email characters
}

/**
 * Sanitize username
 */
export function sanitizeUsername(input: string): string {
  if (!input) return '';
  
  return input
    .toLowerCase()
    .trim()
    .replace(/[^\w\-]/g, ''); // Keep only alphanumeric, hyphen, underscore
}

/**
 * Sanitize URL
 */
export function sanitizeUrl(input: string): string {
  if (!input) return '';
  
  return input
    .trim()
    .replace(/javascript:/gi, '')
    .replace(/vbscript:/gi, '')
    .replace(/data:/gi, '');
}

/**
 * Trim and limit string length
 */
export function limitLength(input: string, maxLength: number): string {
  if (!input) return '';
  
  return input.trim().substring(0, maxLength);
}

/**
 * Remove null bytes and other control characters
 */
export function removeControlCharacters(input: string): string {
  if (!input) return '';
  
  return input.replace(/[\x00-\x1F\x7F]/g, '');
}
