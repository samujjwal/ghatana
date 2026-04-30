/**
 * Security Scans API Routes
 *
 * Provides real validated security scanning endpoints for YAPPC.
 * Replaces placeholder security scan pathways with production-grade implementations.
 *
 * @doc.type route
 * @doc.production-grade true
 * @doc.purpose Security scanning and vulnerability assessment
 * @doc.layer api
 * @doc.pattern REST API
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { requirePermission } from '../middleware/rbac.middleware';
import { SecurityScanService } from '../services/security/SecurityScanService';
import { VulnerabilityScanService } from '../services/security/VulnerabilityScanService';
import { ComplianceScanService } from '../services/security/ComplianceScanService';

// Initialize real security scan services
const securityScanService = new SecurityScanService();
const vulnerabilityScanService = new VulnerabilityScanService();
const complianceScanService = new ComplianceScanService();

// ============================================================================
// Types
// ============================================================================

interface SecurityScanRequest {
  target: string; // URL, file path, or component identifier
  scanType: 'vulnerability' | 'dependency' | 'code' | 'compliance' | 'full';
  options?: {
    severity?: 'low' | 'medium' | 'high' | 'critical';
    depth?: 'quick' | 'standard' | 'deep';
    includeDependencies?: boolean;
    excludePatterns?: string[];
  };
}

interface SecurityScanResult {
  scanId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  target: string;
  scanType: string;
  startedAt: string;
  completedAt?: string;
  findings: SecurityFinding[];
  summary: {
    total: number;
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
  recommendations: string[];
}

interface SecurityFinding {
  id: string;
  type: 'vulnerability' | 'dependency' | 'code' | 'compliance';
  severity: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  description: string;
  location?: string;
  cveId?: string;
  owaspCategory?: string;
  recommendation: string;
  references?: string[];
}

// ============================================================================
// Routes
// ============================================================================

export async function securityScanRoutes(fastify: FastifyInstance) {
  /**
   * POST /api/security/scans
   * Initiate a new security scan
   */
  fastify.post(
    '/security/scans',
    {
      preHandler: [requirePermission('security', 'create')],
      schema: {
        body: {
          type: 'object',
          required: ['target', 'scanType'],
          properties: {
            target: { type: 'string' },
            scanType: { 
              type: 'string', 
              enum: ['vulnerability', 'dependency', 'code', 'compliance', 'full'] 
            },
            options: {
              type: 'object',
              properties: {
                severity: { type: 'string', enum: ['low', 'medium', 'high', 'critical'] },
                depth: { type: 'string', enum: ['quick', 'standard', 'deep'] },
                includeDependencies: { type: 'boolean' },
                excludePatterns: { type: 'array', items: { type: 'string' } }
              }
            }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: SecurityScanRequest }>, reply: FastifyReply) => {
      try {
        const { target, scanType, options } = request.body;
        
        // Validate target format
        if (!target || target.trim().length === 0) {
          return reply.status(400).send({
            error: 'Invalid target',
            message: 'Target must be a valid URL, file path, or component identifier'
          });
        }

        // Select appropriate scan service
        let scanResult: SecurityScanResult;
        switch (scanType) {
          case 'vulnerability':
            scanResult = await vulnerabilityScanService.scan(target, options);
            break;
          case 'dependency':
            scanResult = await securityScanService.scanDependencies(target, options);
            break;
          case 'code':
            scanResult = await securityScanService.scanCode(target, options);
            break;
          case 'compliance':
            scanResult = await complianceScanService.scan(target, options);
            break;
          case 'full':
            scanResult = await securityScanService.fullScan(target, options);
            break;
          default:
            return reply.status(400).send({
              error: 'Invalid scan type',
              message: 'Supported scan types: vulnerability, dependency, code, compliance, full'
            });
        }

        return reply.status(201).send({
          success: true,
          data: scanResult,
          metadata: {
            timestamp: new Date().toISOString(),
            requestId: generateRequestId()
          }
        });
      } catch (error: unknown) {
        fastify.log.error('Security scan initiation failed:', error);
        return reply.status(500).send({
          error: 'Scan initiation failed',
          message: error instanceof Error ? error.message : 'Unknown error occurred'
        });
      }
    }
  );

  /**
   * GET /api/security/scans/:scanId
   * Get scan results by ID
   */
  fastify.get(
    '/security/scans/:scanId',
    {
      preHandler: [requirePermission('security', 'read')]
    },
    async (request: FastifyRequest<{ Params: { scanId: string } }>, reply: FastifyReply) => {
      try {
        const { scanId } = request.params;
        
        if (!scanId || scanId.trim().length === 0) {
          return reply.status(400).send({
            error: 'Invalid scan ID',
            message: 'Scan ID is required'
          });
        }

        // Try to get results from all scan services
        const vulnerabilityResult = await vulnerabilityScanService.getScanResult(scanId);
        if (vulnerabilityResult) {
          return reply.send({
            success: true,
            data: vulnerabilityResult,
            metadata: {
              timestamp: new Date().toISOString(),
              requestId: generateRequestId()
            }
          });
        }

        const securityResult = await securityScanService.getScanResult(scanId);
        if (securityResult) {
          return reply.send({
            success: true,
            data: securityResult,
            metadata: {
              timestamp: new Date().toISOString(),
              requestId: generateRequestId()
            }
          });
        }

        const complianceResult = await complianceScanService.getScanResult(scanId);
        if (complianceResult) {
          return reply.send({
            success: true,
            data: complianceResult,
            metadata: {
              timestamp: new Date().toISOString(),
              requestId: generateRequestId()
            }
          });
        }

        return reply.status(404).send({
          error: 'Scan not found',
          message: `No scan found with ID: ${scanId}`
        });
      } catch (error: unknown) {
        fastify.log.error('Failed to retrieve scan results:', error);
        return reply.status(500).send({
          error: 'Failed to retrieve scan results',
          message: error instanceof Error ? error.message : 'Unknown error occurred'
        });
      }
    }
  );

  /**
   * GET /api/security/scans
   * List all security scans with optional filtering
   */
  fastify.get(
    '/security/scans',
    {
      preHandler: [requirePermission('security', 'read')],
      schema: {
        querystring: {
          type: 'object',
          properties: {
            status: { type: 'string', enum: ['pending', 'running', 'completed', 'failed'] },
            scanType: { type: 'string', enum: ['vulnerability', 'dependency', 'code', 'compliance', 'full'] },
            limit: { type: 'number', minimum: 1, maximum: 100 },
            offset: { type: 'number', minimum: 0 }
          }
        }
      }
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const query = request.query as any;
        const { status, scanType, limit = 50, offset = 0 } = query;

        // Aggregate results from all scan services
        const [vulnerabilityScans, securityScans, complianceScans] = await Promise.all([
          vulnerabilityScanService.listScans({ status, scanType, limit, offset }),
          securityScanService.listScans({ status, scanType, limit, offset }),
          complianceScanService.listScans({ status, scanType, limit, offset })
        ]);

        const allScans = [...vulnerabilityScans, ...securityScans, ...complianceScans]
          .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())
          .slice(0, limit);

        return reply.send({
          success: true,
          data: allScans,
          pagination: {
            limit,
            offset,
            total: allScans.length
          },
          metadata: {
            timestamp: new Date().toISOString(),
            requestId: generateRequestId()
          }
        });
      } catch (error: unknown) {
        fastify.log.error('Failed to list security scans:', error);
        return reply.status(500).send({
          error: 'Failed to list security scans',
          message: error instanceof Error ? error.message : 'Unknown error occurred'
        });
      }
    }
  );

  /**
   * DELETE /api/security/scans/:scanId
   * Delete a security scan and its results
   */
  fastify.delete(
    '/security/scans/:scanId',
    {
      preHandler: [requirePermission('security', 'delete')]
    },
    async (request: FastifyRequest<{ Params: { scanId: string } }>, reply: FastifyReply) => {
      try {
        const { scanId } = request.params;
        
        if (!scanId || scanId.trim().length === 0) {
          return reply.status(400).send({
            error: 'Invalid scan ID',
            message: 'Scan ID is required'
          });
        }

        // Try to delete from all scan services
        let deleted = false;
        deleted = await vulnerabilityScanService.deleteScan(scanId) || deleted;
        deleted = await securityScanService.deleteScan(scanId) || deleted;
        deleted = await complianceScanService.deleteScan(scanId) || deleted;

        if (!deleted) {
          return reply.status(404).send({
            error: 'Scan not found',
            message: `No scan found with ID: ${scanId}`
          });
        }

        return reply.send({
          success: true,
          message: 'Scan deleted successfully',
          metadata: {
            timestamp: new Date().toISOString(),
            requestId: generateRequestId()
          }
        });
      } catch (error: unknown) {
        fastify.log.error('Failed to delete security scan:', error);
        return reply.status(500).send({
          error: 'Failed to delete security scan',
          message: error instanceof Error ? error.message : 'Unknown error occurred'
        });
      }
    }
  );

  /**
   * GET /api/security/vulnerabilities
   * Get aggregated vulnerability findings across all scans
   */
  fastify.get(
    '/security/vulnerabilities',
    {
      preHandler: [requirePermission('security', 'read')],
      schema: {
        querystring: {
          type: 'object',
          properties: {
            severity: { type: 'string', enum: ['low', 'medium', 'high', 'critical'] },
            status: { type: 'string', enum: ['open', 'resolved', 'ignored'] },
            limit: { type: 'number', minimum: 1, maximum: 100 },
            offset: { type: 'number', minimum: 0 }
          }
        }
      }
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const query = request.query as any;
        const { severity, status, limit = 50, offset = 0 } = query;

        const vulnerabilities = await vulnerabilityScanService.getVulnerabilities({
          severity,
          status,
          limit,
          offset
        });

        return reply.send({
          success: true,
          data: vulnerabilities,
          metadata: {
            timestamp: new Date().toISOString(),
            requestId: generateRequestId()
          }
        });
      } catch (error: unknown) {
        fastify.log.error('Failed to retrieve vulnerabilities:', error);
        return reply.status(500).send({
          error: 'Failed to retrieve vulnerabilities',
          message: error instanceof Error ? error.message : 'Unknown error occurred'
        });
      }
    }
  );
}

// ============================================================================
// Helper Functions
// ============================================================================

function generateRequestId(): string {
  try {
    return crypto.randomUUID();
  } catch {
    return `req-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}
