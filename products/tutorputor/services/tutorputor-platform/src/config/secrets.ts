/**
 * Secure Secrets Management
 * 
 * Provides secure secret handling with:
 * - Environment variable validation
 * - Secret rotation support
 * - Auditing and logging
 * - Integration with external secret managers
 */

import crypto from 'crypto';
import { getConfig } from './config.js';

export interface SecretManager {
  getSecret(key: string): Promise<string>;
  setSecret(key: string, value: string): Promise<void>;
  deleteSecret(key: string): Promise<void>;
  rotateSecret(key: string): Promise<string>;
}

/**
 * Environment-based Secret Manager
 * For development and basic deployments
 */
export class EnvironmentSecretManager implements SecretManager {
  private auditLog: Array<{
    timestamp: Date;
    action: string;
    key: string;
    success: boolean;
  }> = [];

  async getSecret(key: string): Promise<string> {
    const value = process.env[key];
    
    if (!value) {
      this.auditLog.push({
        timestamp: new Date(),
        action: 'GET',
        key,
        success: false,
      });
      throw new Error(`Secret ${key} not found in environment`);
    }

    // Validate that the secret is not a default/placeholder value
    const forbiddenValues = ['change-me-in-production', 'secret', 'test', 'password', 'minioadmin', 'sk-'];
    if (forbiddenValues.some(forbidden => value.toLowerCase().includes(forbidden))) {
      throw new Error(`Secret ${key} appears to be a default/placeholder value`);
    }

    this.auditLog.push({
      timestamp: new Date(),
      action: 'GET',
      key,
      success: true,
    });

    return value;
  }

  async setSecret(key: string, value: string): Promise<void> {
    // In production, this should integrate with a proper secret manager
    if (getConfig().NODE_ENV === 'production') {
      throw new Error('Cannot set secrets in production environment');
    }

    process.env[key] = value;
    
    this.auditLog.push({
      timestamp: new Date(),
      action: 'SET',
      key,
      success: true,
    });
  }

  async deleteSecret(key: string): Promise<void> {
    if (getConfig().NODE_ENV === 'production') {
      throw new Error('Cannot delete secrets in production environment');
    }

    delete process.env[key];
    
    this.auditLog.push({
      timestamp: new Date(),
      action: 'DELETE',
      key,
      success: true,
    });
  }

  async rotateSecret(key: string): Promise<string> {
    const newSecret = this.generateSecureSecret();
    await this.setSecret(key, newSecret);
    
    this.auditLog.push({
      timestamp: new Date(),
      action: 'ROTATE',
      key,
      success: true,
    });

    return newSecret;
  }

  private generateSecureSecret(length: number = 64): string {
    return crypto.randomBytes(length).toString('hex');
  }

  getAuditLog(): typeof this.auditLog {
    return [...this.auditLog];
  }
}

/**
 * HashiCorp Vault Secret Manager
 * For production deployments
 */
export class VaultSecretManager implements SecretManager {
  private vaultUrl: string;
  private vaultToken: string;
  private auditLog: Array<{
    timestamp: Date;
    action: string;
    key: string;
    success: boolean;
  }> = [];

  constructor(vaultUrl: string, vaultToken: string) {
    this.vaultUrl = vaultUrl;
    this.vaultToken = vaultToken;
  }

  async getSecret(key: string): Promise<string> {
    try {
      // Implementation would use Vault HTTP API
      // This is a simplified version
      const response = await fetch(`${this.vaultUrl}/v1/secret/data/${key}`, {
        headers: {
          'X-Vault-Token': this.vaultToken,
        },
      });

      if (!response.ok) {
        throw new Error(`Vault request failed: ${response.statusText}`);
      }

      const data = await response.json() as { data: { data: { value: string } } };
      const secret = data.data.data.value;

      this.auditLog.push({
        timestamp: new Date(),
        action: 'GET',
        key,
        success: true,
      });

      return secret;
    } catch (error) {
      this.auditLog.push({
        timestamp: new Date(),
        action: 'GET',
        key,
        success: false,
      });
      throw error;
    }
  }

  async setSecret(key: string, value: string): Promise<void> {
    try {
      const response = await fetch(`${this.vaultUrl}/v1/secret/data/${key}`, {
        method: 'POST',
        headers: {
          'X-Vault-Token': this.vaultToken,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          data: { value },
        }),
      });

      if (!response.ok) {
        throw new Error(`Vault request failed: ${response.statusText}`);
      }

      this.auditLog.push({
        timestamp: new Date(),
        action: 'SET',
        key,
        success: true,
      });
    } catch (error) {
      this.auditLog.push({
        timestamp: new Date(),
        action: 'SET',
        key,
        success: false,
      });
      throw error;
    }
  }

  async deleteSecret(key: string): Promise<void> {
    try {
      const response = await fetch(`${this.vaultUrl}/v1/secret/data/${key}`, {
        method: 'DELETE',
        headers: {
          'X-Vault-Token': this.vaultToken,
        },
      });

      if (!response.ok) {
        throw new Error(`Vault request failed: ${response.statusText}`);
      }

      this.auditLog.push({
        timestamp: new Date(),
        action: 'DELETE',
        key,
        success: true,
      });
    } catch (error) {
      this.auditLog.push({
        timestamp: new Date(),
        action: 'DELETE',
        key,
        success: false,
      });
      throw error;
    }
  }

  async rotateSecret(key: string): Promise<string> {
    const newSecret = this.generateSecureSecret();
    await this.setSecret(key, newSecret);
    
    this.auditLog.push({
      timestamp: new Date(),
      action: 'ROTATE',
      key,
      success: true,
    });

    return newSecret;
  }

  private generateSecureSecret(length: number = 64): string {
    return crypto.randomBytes(length).toString('hex');
  }

  getAuditLog(): typeof this.auditLog {
    return [...this.auditLog];
  }
}

/**
 * Secret Manager Factory
 */
export function createSecretManager(): SecretManager {
  const config = getConfig();
  
  // In production, use Vault or other secure secret manager
  if (config.NODE_ENV === 'production') {
    const vaultUrl = process.env.VAULT_URL;
    const vaultToken = process.env.VAULT_TOKEN;
    
    if (vaultUrl && vaultToken) {
      return new VaultSecretManager(vaultUrl, vaultToken);
    }
    
    throw new Error('Production environment requires Vault configuration');
  }
  
  // In development, use environment variables
  return new EnvironmentSecretManager();
}

/**
 * Secret validation utilities
 */
export class SecretValidator {
  static validateJWTSecret(secret: string): boolean {
    return secret.length >= 32 && !this.isCommonSecret(secret);
  }

  static validateDatabaseUrl(url: string): boolean {
    try {
      const parsed = new URL(url);
      return parsed.protocol.includes('postgresql') || parsed.protocol.includes('mysql');
    } catch {
      return false;
    }
  }

  static validateS3Credentials(accessKey: string, secretKey: string): boolean {
    return accessKey.length >= 16 && secretKey.length >= 16;
  }

  private static isCommonSecret(secret: string): boolean {
    const commonSecrets = [
      'change-me-in-production',
      'secret',
      'password',
      'test',
      'default',
      'admin',
      '123456',
      'qwerty',
    ];
    
    return commonSecrets.includes(secret.toLowerCase());
  }

  static generateSecureSecret(length: number = 64): string {
    return crypto.randomBytes(length).toString('hex');
  }
}

/**
 * Pre-commit hook for secret detection
 */
export function detectSecretsInCode(code: string): Array<{ line: number; match: string; type: string }> {
  const secrets: Array<{ line: number; match: string; type: string }> = [];
  const lines = code.split('\n');

  // Patterns for common secret exposures
  const patterns = [
    { regex: /password\s*=\s*['"`][^'"`]+['"`]/i, type: 'password' },
    { regex: /secret\s*=\s*['"`][^'"`]+['"`]/i, type: 'secret' },
    { regex: /api[_-]?key\s*=\s*['"`][^'"`]+['"`]/i, type: 'api_key' },
    { regex: /token\s*=\s*['"`][^'"`]+['"`]/i, type: 'token' },
    { regex: /postgresql:\/\/[^:]+:[^@]+@/, type: 'database_url' },
    { regex: /mongodb:\/\/[^:]+:[^@]+@/, type: 'database_url' },
  ];

  lines.forEach((line, index) => {
    patterns.forEach(pattern => {
      const match = line.match(pattern.regex);
      if (match) {
        secrets.push({
          line: index + 1,
          match: match[0],
          type: pattern.type,
        });
      }
    });
  });

  return secrets;
}

// Export singleton instance
export const secretManager = createSecretManager();
