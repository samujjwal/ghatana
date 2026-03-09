import { createHmac, randomBytes } from 'crypto';

/**
 * Secure comparison of two strings in constant time
 * @param a First string
 * @param b Second string
 * @returns True if strings are equal
 */
export function secureCompare(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  
  return result === 0;
}

/**
 * Generate a secure random string
 * @param length Length of the random string
 * @returns Random string
 */
export function generateRandomString(length: number = 32): string {
  return randomBytes(Math.ceil(length / 2))
    .toString('hex')
    .slice(0, length);
}

/**
 * Create an HMAC signature for a message
 * @param secret Secret key
 * @param message Message to sign
 * @param algorithm Hash algorithm (default: sha256)
 * @returns HMAC signature
 */
export function createSignature(
  secret: string, 
  message: string, 
  algorithm: string = 'sha256'
): string {
  return createHmac(algorithm, secret)
    .update(message)
    .digest('hex');
}

/**
 * Verify an HMAC signature
 * @param signature Signature to verify
 * @param secret Secret key
 * @param message Original message
 * @param algorithm Hash algorithm (default: sha256)
 * @returns True if signature is valid
 */
export function verifySignature(
  signature: string,
  secret: string,
  message: string,
  algorithm: string = 'sha256'
): boolean {
  const expectedSignature = createSignature(secret, message, algorithm);
  return secureCompare(signature, expectedSignature);
}

/**
 * Sanitize input to prevent XSS and injection attacks
 * @param input Input string to sanitize
 * @returns Sanitized string
 */
export function sanitizeInput(input: string): string {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
    .replace(/\//g, '&#x2F;');
}

/**
 * Validate URL to prevent SSRF attacks
 * @param url URL to validate
 * @param allowedDomains List of allowed domains (empty allows all)
 * @returns True if URL is safe
 */
export function isSafeUrl(url: string, allowedDomains: string[] = []): boolean {
  try {
    const parsedUrl = new URL(url);
    
    // Check if protocol is allowed
    if (!['http:', 'https:', 'ws:', 'wss:'].includes(parsedUrl.protocol)) {
      return false;
    }
    
    // Check against allowed domains if specified
    if (allowedDomains.length > 0) {
      const hostname = parsedUrl.hostname;
      const isAllowed = allowedDomains.some(domain => {
        // Allow exact matches or subdomains (e.g., .example.com matches api.example.com)
        return hostname === domain || 
               (domain.startsWith('.') && hostname.endsWith(domain)) ||
               hostname === domain.substring(1);
      });
      
      if (!isAllowed) {
        return false;
      }
    }
    
    // Check for private IP addresses
    if (isPrivateIp(parsedUrl.hostname)) {
      return false;
    }
    
    return true;
  } catch {
    return false;
  }
}

/**
 * Check if an IP address is private
 * @param ip IP address or hostname
 * @returns True if IP is private
 */
function isPrivateIp(ip: string): boolean {
  // Handle IPv6 addresses
  if (ip.includes(':')) {
    // Check for IPv6 localhost
    if (ip === '::1' || ip === '[::1]') {
      return true;
    }
    // Check for IPv6 private addresses
    return /^f[cd][0-9a-f]{2}(:|$)/i.test(ip) || // fc00::/7 - Unique Local Addresses (ULA)
           /^fe80:/i.test(ip);                     // fe80::/10 - Link-local addresses
  }
  
  // Handle IPv4 addresses
  const parts = ip.split('.').map(Number);
  
  // Check for IPv4 localhost
  if (parts[0] === 127) {
    return true;
  }
  
  // Check for private IPv4 ranges
  return parts[0] === 10 || // 10.0.0.0/8
         (parts[0] === 172 && (parts[1] >= 16 && parts[1] <= 31)) || // 172.16.0.0/12
         (parts[0] === 192 && parts[1] === 168); // 192.168.0.0/16
}

/**
 * Generate a secure token for CSRF protection
 * @returns Object containing token and hashed token
 */
export function generateCsrfToken(): { token: string; hash: string } {
  const token = generateRandomString(32);
  const hash = createSignature('csrf-secret', token);
  return { token, hash };
}

/**
 * Verify a CSRF token
 * @param token Token to verify
 * @param hash Expected hash
 * @returns True if token is valid
 */
export function verifyCsrfToken(token: string, hash: string): boolean {
  return verifySignature(hash, 'csrf-secret', token);
}

/**
 * Encrypt sensitive data (symmetric encryption)
 * @param data Data to encrypt
 * @param secret Secret key
 * @returns Encrypted data as base64
 */
export function encryptData(data: string, secret: string): string {
  // In a real implementation, use a proper encryption library like crypto-js or Node's crypto
  // This is a simplified example
  const encoder = new TextEncoder();
  const dataBuffer = encoder.encode(data);
  const secretBuffer = encoder.encode(secret);
  
  // Simple XOR encryption (not secure, for demonstration only)
  const encrypted = new Uint8Array(dataBuffer.length);
  for (let i = 0; i < dataBuffer.length; i++) {
    encrypted[i] = dataBuffer[i] ^ secretBuffer[i % secretBuffer.length];
  }
  
  return Buffer.from(encrypted).toString('base64');
}

/**
 * Decrypt data
 * @param encryptedData Encrypted data as base64
 * @param secret Secret key
 * @returns Decrypted data
 */
export function decryptData(encryptedData: string, secret: string): string {
  // In a real implementation, use a proper encryption library
  const decoder = new TextDecoder();
  const encryptedBuffer = Buffer.from(encryptedData, 'base64');
  const secretBuffer = new TextEncoder().encode(secret);
  
  // Simple XOR decryption (not secure, for demonstration only)
  const decrypted = new Uint8Array(encryptedBuffer.length);
  for (let i = 0; i < encryptedBuffer.length; i++) {
    decrypted[i] = encryptedBuffer[i] ^ secretBuffer[i % secretBuffer.length];
  }
  
  return decoder.decode(decrypted);
}

/**
 * Sanitize and validate request headers
 * @param headers Headers to sanitize
 * @returns Sanitized headers
 */
export function sanitizeHeaders(headers: Record<string, string>): Record<string, string> {
  const sanitized: Record<string, string> = {};
  
  for (const [key, value] of Object.entries(headers)) {
    // Remove potentially dangerous headers
    if (/[\r\n]|%0D|%0A|%0a|%0d/i.test(key) || /^\s*$/.test(key) || /^\s*$/.test(value)) {
      continue;
    }
    
    // Sanitize header values
    const sanitizedKey = key.trim().toLowerCase();
    const sanitizedValue = sanitizeInput(String(value));
    
    // Skip blacklisted headers
    const blacklistedHeaders = [
      'host', 'connection', 'content-length', 'transfer-encoding',
      'upgrade', 'proxy-', 'x-forwarded-', 'x-real-ip'
    ];
    
    if (!blacklistedHeaders.some(h => sanitizedKey.startsWith(h))) {
      sanitized[sanitizedKey] = sanitizedValue;
    }
  }
  
  return sanitized;
}
