import {
  validateConnectionOptions,
  isValidUrl,
  isValidEmail,
  isValidUuid,
  isValidJwt,
  isValidDate,
  isValidJson,
  isValidRegex,
  isValidDomain,
  isValidIp,
  sanitizeAndValidate,
} from '../../../src/utils/validation';

describe('validation utils', () => {
  describe('validateConnectionOptions', () => {
    it('should validate valid connection options', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
      });
      expect(result.valid).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('should apply default values', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
      });
      expect(result.valid).toBe(true);
    });

    it('should reject missing id', () => {
      const result = validateConnectionOptions({
        type: 'http',
      } as any);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('id');
    });

    it('should reject empty id', () => {
      const result = validateConnectionOptions({
        id: '',
        type: 'http',
      });
      expect(result.valid).toBe(false);
      expect(result.error).toContain('ID is required');
    });

    it('should reject missing type', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
      } as any);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('type');
    });

    it('should validate maxRetries as non-negative integer', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
        maxRetries: -1,
      });
      expect(result.valid).toBe(false);
    });

    it('should validate timeout as positive integer', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
        timeout: 0,
      });
      expect(result.valid).toBe(false);
    });

    it('should accept optional headers', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
        headers: { 'Content-Type': 'application/json' },
      });
      expect(result.valid).toBe(true);
    });

    it('should accept optional auth', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
        auth: { username: 'user', password: 'pass' },
      });
      expect(result.valid).toBe(true);
    });

    it('should accept debug flag', () => {
      const result = validateConnectionOptions({
        id: 'test-id',
        type: 'http',
        debug: true,
      });
      expect(result.valid).toBe(true);
    });

    it('should handle non-ZodError exceptions', () => {
      const result = validateConnectionOptions(null as any);
      expect(result.valid).toBe(false);
      expect(result.error).toBeDefined();
    });
  });

  describe('isValidUrl', () => {
    it('should validate http URLs', () => {
      expect(isValidUrl('http://example.com')).toBe(true);
    });

    it('should validate https URLs', () => {
      expect(isValidUrl('https://example.com')).toBe(true);
    });

    it('should validate ws URLs', () => {
      expect(isValidUrl('ws://example.com')).toBe(true);
    });

    it('should validate wss URLs', () => {
      expect(isValidUrl('wss://example.com')).toBe(true);
    });

    it('should reject invalid URLs', () => {
      expect(isValidUrl('not-a-url')).toBe(false);
    });

    it('should reject ftp URLs by default', () => {
      expect(isValidUrl('ftp://example.com')).toBe(false);
    });

    it('should accept custom protocols', () => {
      expect(isValidUrl('ftp://example.com', ['ftp:'])).toBe(true);
    });

    it('should reject URLs with wrong protocol when custom protocols specified', () => {
      expect(isValidUrl('http://example.com', ['ftp:'])).toBe(false);
    });

    it('should handle malformed URLs', () => {
      expect(isValidUrl('://invalid')).toBe(false);
    });
  });

  describe('isValidEmail', () => {
    it('should validate correct emails', () => {
      expect(isValidEmail('test@example.com')).toBe(true);
      expect(isValidEmail('user.name@example.co.uk')).toBe(true);
      expect(isValidEmail('user+tag@example.com')).toBe(true);
    });

    it('should reject emails without @', () => {
      expect(isValidEmail('test.example.com')).toBe(false);
    });

    it('should reject emails without domain', () => {
      expect(isValidEmail('test@')).toBe(false);
    });

    it('should reject emails without username', () => {
      expect(isValidEmail('@example.com')).toBe(false);
    });

    it('should reject emails with spaces', () => {
      expect(isValidEmail('test @example.com')).toBe(false);
      expect(isValidEmail('test@ example.com')).toBe(false);
    });

    it('should reject emails without TLD', () => {
      expect(isValidEmail('test@example')).toBe(false);
    });
  });

  describe('isValidUuid', () => {
    it('should validate correct UUIDs', () => {
      expect(isValidUuid('123e4567-e89b-12d3-a456-426614174000')).toBe(true);
      expect(isValidUuid('550e8400-e29b-41d4-a716-446655440000')).toBe(true);
    });

    it('should validate UUID v1-v5', () => {
      expect(isValidUuid('123e4567-e89b-12d3-a456-426614174000')).toBe(true);
      expect(isValidUuid('123e4567-e89b-22d3-a456-426614174000')).toBe(true);
      expect(isValidUuid('123e4567-e89b-32d3-a456-426614174000')).toBe(true);
      expect(isValidUuid('123e4567-e89b-42d3-a456-426614174000')).toBe(true);
      expect(isValidUuid('123e4567-e89b-52d3-a456-426614174000')).toBe(true);
    });

    it('should reject invalid UUIDs', () => {
      expect(isValidUuid('not-a-uuid')).toBe(false);
      expect(isValidUuid('123e4567-e89b-12d3-a456')).toBe(false);
      expect(isValidUuid('123e4567e89b12d3a456426614174000')).toBe(false);
    });

    it('should reject UUID with wrong version', () => {
      expect(isValidUuid('123e4567-e89b-62d3-a456-426614174000')).toBe(false);
    });

    it('should reject UUID with wrong variant', () => {
      expect(isValidUuid('123e4567-e89b-12d3-f456-426614174000')).toBe(false);
    });

    it('should accept both upper and lowercase', () => {
      expect(isValidUuid('123E4567-E89B-12D3-A456-426614174000')).toBe(true);
    });
  });

  describe('isValidJwt', () => {
    it('should validate correct JWT tokens', () => {
      expect(isValidJwt('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U')).toBe(true);
    });

    it('should validate JWT with two parts (unsigned)', () => {
      expect(isValidJwt('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0')).toBe(true);
    });

    it('should validate JWT with three parts', () => {
      expect(isValidJwt('header.payload.signature')).toBe(true);
    });

    it('should reject invalid JWT formats', () => {
      expect(isValidJwt('not.a.jwt.token.extra')).toBe(false);
      expect(isValidJwt('only-one-part')).toBe(false);
    });

    it('should reject JWT with invalid characters', () => {
      expect(isValidJwt('header!.payload@.signature#')).toBe(false);
    });

    it('should accept JWT with padding', () => {
      expect(isValidJwt('header==.payload=.signature==')).toBe(true);
    });
  });

  describe('isValidDate', () => {
    it('should validate ISO 8601 dates', () => {
      expect(isValidDate('2023-10-22T10:30:00Z')).toBe(true);
      expect(isValidDate('2023-10-22T10:30:00.123Z')).toBe(true);
      expect(isValidDate('2023-10-22T10:30:00+05:30')).toBe(true);
    });

    it('should validate timestamp format', () => {
      expect(isValidDate('1634897400000', 'timestamp')).toBe(true);
    });

    it('should validate RFC 2822 format', () => {
      expect(isValidDate('Sun, 22 Oct 2023 10:30:00 GMT', 'rfc2822')).toBe(true);
    });

    it('should reject invalid dates', () => {
      expect(isValidDate('not-a-date')).toBe(false);
      expect(isValidDate('2023-13-45')).toBe(false);
    });

    it('should reject wrong format dates', () => {
      expect(isValidDate('2023-10-22', 'timestamp')).toBe(false);
      expect(isValidDate('1634897400000', 'iso')).toBe(false);
    });

    it('should reject malformed ISO dates', () => {
      expect(isValidDate('2023-10-22 10:30:00', 'iso')).toBe(false);
    });

    it('should reject invalid timestamp', () => {
      expect(isValidDate('not-a-number', 'timestamp')).toBe(false);
    });
  });

  describe('isValidJson', () => {
    it('should validate valid JSON', () => {
      expect(isValidJson('{}')).toBe(true);
      expect(isValidJson('[]')).toBe(true);
      expect(isValidJson('{"key": "value"}')).toBe(true);
      expect(isValidJson('[1, 2, 3]')).toBe(true);
    });

    it('should validate primitive JSON values', () => {
      expect(isValidJson('null')).toBe(true);
      expect(isValidJson('true')).toBe(true);
      expect(isValidJson('false')).toBe(true);
      expect(isValidJson('42')).toBe(true);
      expect(isValidJson('"string"')).toBe(true);
    });

    it('should reject invalid JSON', () => {
      expect(isValidJson('{invalid}')).toBe(false);
      expect(isValidJson('{key: value}')).toBe(false);
      expect(isValidJson("{'key': 'value'}")).toBe(false);
    });

    it('should reject unclosed braces', () => {
      expect(isValidJson('{')).toBe(false);
      expect(isValidJson('[')).toBe(false);
    });
  });

  describe('isValidRegex', () => {
    it('should validate valid regex patterns', () => {
      expect(isValidRegex('^test$')).toBe(true);
      expect(isValidRegex('[a-z]+')).toBe(true);
      expect(isValidRegex('\\d{3}')).toBe(true);
    });

    it('should validate complex patterns', () => {
      expect(isValidRegex('(?:https?://)?[a-z]+')).toBe(true);
    });

    it('should reject invalid regex patterns', () => {
      expect(isValidRegex('[')).toBe(false);
      expect(isValidRegex('(?')).toBe(false);
      expect(isValidRegex('*')).toBe(false);
    });

    it('should validate empty pattern', () => {
      expect(isValidRegex('')).toBe(true);
    });
  });

  describe('isValidDomain', () => {
    it('should validate correct domains', () => {
      expect(isValidDomain('example.com')).toBe(true);
      expect(isValidDomain('sub.example.com')).toBe(true);
      expect(isValidDomain('deep.sub.example.com')).toBe(true);
    });

    it('should validate localhost', () => {
      expect(isValidDomain('localhost')).toBe(true);
    });

    it('should validate IP addresses in domain format', () => {
      expect(isValidDomain('192.168.1.1')).toBe(true);
    });

    it('should validate domains with ports', () => {
      expect(isValidDomain('example.com:8080')).toBe(true);
    });

    it('should validate domains with paths', () => {
      expect(isValidDomain('example.com/path')).toBe(true);
    });

    it('should reject invalid domains', () => {
      expect(isValidDomain('not a domain')).toBe(false);
      expect(isValidDomain('-example.com')).toBe(false);
    });

    it('should accept uppercase and lowercase', () => {
      expect(isValidDomain('Example.COM')).toBe(true);
    });
  });

  describe('isValidIp', () => {
    describe('IPv4', () => {
      it('should validate correct IPv4 addresses', () => {
        expect(isValidIp('192.168.1.1', 4)).toBe(true);
        expect(isValidIp('10.0.0.0', 4)).toBe(true);
        expect(isValidIp('255.255.255.255', 4)).toBe(true);
        expect(isValidIp('0.0.0.0', 4)).toBe(true);
      });

      it('should reject invalid IPv4 addresses', () => {
        expect(isValidIp('256.1.1.1', 4)).toBe(false);
        expect(isValidIp('1.1.1', 4)).toBe(false);
        expect(isValidIp('1.1.1.1.1', 4)).toBe(false);
      });

      it('should reject IPv4 with non-numeric parts', () => {
        expect(isValidIp('192.168.a.1', 4)).toBe(false);
      });
    });

    describe('IPv6', () => {
      it('should validate correct IPv6 addresses', () => {
        expect(isValidIp('2001:0db8:85a3:0000:0000:8a2e:0370:7334', 6)).toBe(true);
        expect(isValidIp('2001:db8::1', 6)).toBe(true);
        expect(isValidIp('::1', 6)).toBe(true);
        expect(isValidIp('::', 6)).toBe(true);
      });

      it('should reject invalid IPv6 addresses', () => {
        expect(isValidIp('2001:0db8:85a3::8a2e::7334', 6)).toBe(false); // Multiple ::
        expect(isValidIp('gggg::', 6)).toBe(false); // Invalid hex
      });

      it('should reject IPv6 with too many groups', () => {
        expect(isValidIp('1:2:3:4:5:6:7:8:9', 6)).toBe(false);
      });

      it('should reject IPv6 with too long groups', () => {
        expect(isValidIp('12345::', 6)).toBe(false);
      });
    });

    describe('All versions', () => {
      it('should validate both IPv4 and IPv6 by default', () => {
        expect(isValidIp('192.168.1.1')).toBe(true);
        expect(isValidIp('2001:db8::1')).toBe(true);
      });

      it('should validate IPv4 with version "all"', () => {
        expect(isValidIp('192.168.1.1', 'all')).toBe(true);
      });

      it('should validate IPv6 with version "all"', () => {
        expect(isValidIp('2001:db8::1', 'all')).toBe(true);
      });

      it('should reject completely invalid IPs', () => {
        expect(isValidIp('not-an-ip')).toBe(false);
      });
    });
  });

  describe('sanitizeAndValidate', () => {
    describe('string type', () => {
      it('should return string as-is', () => {
        expect(sanitizeAndValidate('test', 'string')).toBe('test');
      });

      it('should convert number to string', () => {
        expect(sanitizeAndValidate(123, 'string')).toBe('123');
      });

      it('should convert boolean to string', () => {
        expect(sanitizeAndValidate(true, 'string')).toBe('true');
      });

      it('should convert object to string', () => {
        expect(sanitizeAndValidate({}, 'string')).toBe('[object Object]');
      });
    });

    describe('number type', () => {
      it('should return number as-is', () => {
        expect(sanitizeAndValidate(123, 'number')).toBe(123);
      });

      it('should convert numeric string to number', () => {
        expect(sanitizeAndValidate('123', 'number')).toBe(123);
      });

      it('should return null for invalid numbers', () => {
        expect(sanitizeAndValidate('not-a-number', 'number')).toBeNull();
        expect(sanitizeAndValidate(Infinity, 'number')).toBeNull();
        expect(sanitizeAndValidate(NaN, 'number')).toBeNull();
      });

      it('should handle zero', () => {
        expect(sanitizeAndValidate(0, 'number')).toBe(0);
        expect(sanitizeAndValidate('0', 'number')).toBe(0);
      });

      it('should handle negative numbers', () => {
        expect(sanitizeAndValidate(-123, 'number')).toBe(-123);
      });

      it('should handle decimals', () => {
        expect(sanitizeAndValidate(123.45, 'number')).toBe(123.45);
      });
    });

    describe('boolean type', () => {
      it('should return boolean as-is', () => {
        expect(sanitizeAndValidate(true, 'boolean')).toBe(true);
        expect(sanitizeAndValidate(false, 'boolean')).toBe(false);
      });

      it('should convert string "true" to boolean', () => {
        expect(sanitizeAndValidate('true', 'boolean')).toBe(true);
        expect(sanitizeAndValidate('TRUE', 'boolean')).toBe(true);
      });

      it('should convert string "false" to boolean', () => {
        expect(sanitizeAndValidate('false', 'boolean')).toBe(false);
        expect(sanitizeAndValidate('FALSE', 'boolean')).toBe(false);
      });

      it('should convert string "1" to true', () => {
        expect(sanitizeAndValidate('1', 'boolean')).toBe(true);
      });

      it('should convert string "0" to false', () => {
        expect(sanitizeAndValidate('0', 'boolean')).toBe(false);
      });

      it('should convert truthy values to true', () => {
        expect(sanitizeAndValidate(1, 'boolean')).toBe(true);
        expect(sanitizeAndValidate('yes', 'boolean')).toBe(true);
      });

      it('should convert falsy values to false', () => {
        expect(sanitizeAndValidate(0, 'boolean')).toBe(false);
        expect(sanitizeAndValidate('', 'boolean')).toBe(false);
      });
    });

    describe('date type', () => {
      it('should return valid date', () => {
        const date = new Date('2023-10-22');
        const result = sanitizeAndValidate('2023-10-22', 'date');
        expect(result).toBeInstanceOf(Date);
        expect((result as Date).toISOString()).toContain('2023-10-22');
      });

      it('should return null for invalid date', () => {
        expect(sanitizeAndValidate('not-a-date', 'date')).toBeNull();
      });

      it('should handle timestamp', () => {
        const result = sanitizeAndValidate(1634897400000, 'date');
        expect(result).toBeInstanceOf(Date);
      });
    });

    describe('object type', () => {
      it('should return object as-is', () => {
        const obj = { key: 'value' };
        expect(sanitizeAndValidate(obj, 'object')).toBe(obj);
      });

      it('should parse JSON string to object', () => {
        const result = sanitizeAndValidate('{"key": "value"}', 'object');
        expect(result).toEqual({ key: 'value' });
      });

      it('should return null for array', () => {
        expect(sanitizeAndValidate([], 'object')).toBeNull();
      });

      it('should return null for null', () => {
        expect(sanitizeAndValidate(null, 'object')).toBeNull();
      });

      it('should return null for invalid JSON string', () => {
        expect(sanitizeAndValidate('{invalid}', 'object')).toBeNull();
      });

      it('should return null for array JSON string', () => {
        expect(sanitizeAndValidate('[1, 2, 3]', 'object')).toBeNull();
      });

      it('should return null for primitive values', () => {
        expect(sanitizeAndValidate('string', 'object')).toBeNull();
        expect(sanitizeAndValidate(123, 'object')).toBeNull();
      });
    });

    describe('array type', () => {
      it('should return array as-is', () => {
        const arr = [1, 2, 3];
        expect(sanitizeAndValidate(arr, 'array')).toBe(arr);
      });

      it('should parse JSON string to array', () => {
        const result = sanitizeAndValidate('[1, 2, 3]', 'array');
        expect(result).toEqual([1, 2, 3]);
      });

      it('should return null for object', () => {
        expect(sanitizeAndValidate({}, 'array')).toBeNull();
      });

      it('should return null for invalid JSON string', () => {
        expect(sanitizeAndValidate('[invalid]', 'array')).toBeNull();
      });

      it('should return null for object JSON string', () => {
        expect(sanitizeAndValidate('{"key": "value"}', 'array')).toBeNull();
      });

      it('should return null for primitive values', () => {
        expect(sanitizeAndValidate('string', 'array')).toBeNull();
        expect(sanitizeAndValidate(123, 'array')).toBeNull();
      });
    });

    describe('error handling', () => {
      it('should handle exceptions gracefully', () => {
        expect(sanitizeAndValidate(undefined, 'string')).toBe('undefined');
      });

      it('should return null for unknown types', () => {
        expect(sanitizeAndValidate('test', 'unknown' as any)).toBeNull();
      });
    });
  });
});
