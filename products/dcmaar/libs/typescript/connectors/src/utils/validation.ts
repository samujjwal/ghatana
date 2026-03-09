import { z } from 'zod';
import { ConnectionOptions, ConnectionOptionsSchema } from '../types';

/**
 * Validate connection options against the schema
 * @param options Connection options to validate
 * @returns Validation result
 */
export function validateConnectionOptions(
  options: ConnectionOptions
): { valid: boolean; error?: string } {
  try {
    ConnectionOptionsSchema.parse(options);
    return { valid: true };
  } catch (error) {
    if (error instanceof z.ZodError) {
      return { 
        valid: false, 
        error: error.errors.map(e => `${e.path.join('.')}: ${e.message}`).join('; ') 
      };
    }
    return { 
      valid: false, 
      error: error instanceof Error ? error.message : 'Unknown validation error' 
    };
  }
}

/**
 * Validate a URL
 * @param url URL to validate
 * @param protocols Allowed protocols (default: http, https, ws, wss)
 * @returns True if URL is valid
 */
export function isValidUrl(
  url: string, 
  protocols: string[] = ['http:', 'https:', 'ws:', 'wss:']
): boolean {
  try {
    const parsed = new URL(url);
    return protocols.includes(parsed.protocol);
  } catch {
    return false;
  }
}

/**
 * Validate an email address
 * @param email Email to validate
 * @returns True if email is valid
 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/**
 * Validate a UUID
 * @param uuid UUID to validate
 * @returns True if UUID is valid
 */
export function isValidUuid(uuid: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(uuid);
}

/**
 * Validate a JWT token
 * @param token JWT token to validate
 * @returns True if token is valid
 */
export function isValidJwt(token: string): boolean {
  return /^[A-Za-z0-9-_=]+\.[A-Za-z0-9-_=]+\.?[A-Za-z0-9-_.+/=]*$/.test(token);
}

/**
 * Validate a date string
 * @param dateString Date string to validate
 * @param format Expected format (default: ISO 8601)
 * @returns True if date is valid
 */
export function isValidDate(
  dateString: string, 
  format: 'iso' | 'timestamp' | 'rfc2822' = 'iso'
): boolean {
  const date = new Date(dateString);
  
  if (isNaN(date.getTime())) {
    return false;
  }
  
  switch (format) {
    case 'iso':
      return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?$/.test(dateString);
    case 'timestamp':
      return /^\d+$/.test(dateString);
    case 'rfc2822':
      // This is a simplified check
      return /^[A-Za-z]{3}, \d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT$/.test(dateString);
    default:
      return false;
  }
}

/**
 * Validate a JSON string
 * @param jsonString JSON string to validate
 * @returns True if JSON is valid
 */
export function isValidJson(jsonString: string): boolean {
  try {
    JSON.parse(jsonString);
    return true;
  } catch {
    return false;
  }
}

/**
 * Validate a regular expression
 * @param pattern Regular expression pattern to validate
 * @returns True if pattern is valid
 */
export function isValidRegex(pattern: string): boolean {
  try {
    new RegExp(pattern);
    return true;
  } catch {
    return false;
  }
}

/**
 * Validate a domain name
 * @param domain Domain name to validate
 * @returns True if domain is valid
 */
export function isValidDomain(domain: string): boolean {
  return /^(?:(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)\.)+[a-z]{2,}|localhost|\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?::\d+)?(?:\/.*)?$/i.test(domain);
}

/**
 * Validate an IP address
 * @param ip IP address to validate
 * @param version IP version (4, 6, or 'all')
 * @returns True if IP is valid
 */
export function isValidIp(
  ip: string, 
  version: 4 | 6 | 'all' = 'all'
): boolean {
  if (version === 4 || version === 'all') {
    if (/^(?:\d{1,3}\.){3}\d{1,3}$/.test(ip)) {
      const parts = ip.split('.').map(Number);
      return parts.every(part => part >= 0 && part <= 255);
    }
  }
  
  if (version === 6 || version === 'all') {
    // Simplified IPv6 validation
    if (/^[0-9a-fA-F:]+$/.test(ip)) {
      const parts = ip.split('::');
      if (parts.length > 2) return false;
      
      const allParts = ip.split(':');
      if (allParts.length > 8) return false;
      
      return allParts.every(part => {
        if (part === '') return true;
        return /^[0-9a-fA-F]{1,4}$/.test(part);
      });
    }
  }
  
  return false;
}

/**
 * Sanitize and validate input based on type
 * @param value Value to sanitize
 * @param type Expected type
 * @returns Sanitized value or null if invalid
 */
export function sanitizeAndValidate<T>(
  value: unknown, 
  type: 'string' | 'number' | 'boolean' | 'object' | 'array' | 'date'
): T | null {
  try {
    switch (type) {
      case 'string':
        return (typeof value === 'string' ? value : String(value)) as unknown as T;
      
      case 'number':
        const num = Number(value);
        return (isFinite(num) ? num : null) as unknown as T;
      
      case 'boolean':
        if (typeof value === 'boolean') return value as unknown as T;
        if (typeof value === 'string') {
          const lower = value.toLowerCase();
          if (lower === 'true' || lower === '1') return true as unknown as T;
          if (lower === 'false' || lower === '0') return false as unknown as T;
        }
        return (!!value) as unknown as T;
      
      case 'date':
        const date = new Date(value);
        return (isNaN(date.getTime()) ? null : date) as unknown as T;
      
      case 'object':
        if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
          return value as T;
        }
        try {
          const parsed = typeof value === 'string' ? JSON.parse(value) : value;
          return (parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null) as T;
        } catch {
          return null;
        }
      
      case 'array':
        if (Array.isArray(value)) {
          return value as T;
        }
        try {
          const parsed = typeof value === 'string' ? JSON.parse(value) : value;
          return (Array.isArray(parsed) ? parsed : null) as T;
        } catch {
          return null;
        }
      
      default:
        return null;
    }
  } catch {
    return null;
  }
}
