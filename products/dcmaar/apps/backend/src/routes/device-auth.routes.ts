/**
 * Device Authentication Routes
 *
 * Endpoints for agent-device authentication and token management.
 *
 * <p><b>Endpoints</b><br>
 * - POST /api/auth/device-token - Generate device authentication token
 * - POST /api/auth/device-token/refresh - Refresh expired device token
 *
 * <p><b>Purpose</b><br>
 * Manages JWT tokens for child device agents (agent-desktop, agent-react-native, etc.)
 * Enables secure WebSocket connections and API access from unpaired devices.
 *
 * @doc.type route
 * @doc.purpose Device authentication endpoints
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */

import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../db';
import { logger } from '../utils/logger';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';

// Validation schemas
const generateDeviceTokenSchema = z.object({
  childId: z.string().uuid('Invalid child ID'),
  deviceName: z.string().min(1).max(255),
  deviceType: z.enum(['desktop', 'mobile', 'tablet', 'chromebook']),
  platform: z.enum(['windows', 'macos', 'linux', 'ios', 'android']),
  scopes: z.array(z.string()).optional().default(['events:write', 'status:read']),
});

const refreshDeviceTokenSchema = z.object({
  token: z.string().min(1, 'Refresh token is required'),
});

interface DeviceTokenClaims {
  deviceId: string;
  deviceName: string;
  childId: string;
  userId: string;
  scopes: string[];
  iat: number;
  exp: number;
}

/**
 * Generate new device authentication token
 * POST /api/auth/device-token
 */
async function generateDeviceToken(request: FastifyRequest, reply: FastifyReply) {
  try {
    const userId = (request as AuthRequest).userId;
    if (!userId) {
      return reply.code(401).send({ error: 'Not authenticated' });
    }

    // Validate request body
    const parseResult = generateDeviceTokenSchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.code(400).send({
      error: parseResult.error.issues[0]?.message || 'Invalid request',
      details: parseResult.error.issues,
      });
    }

    const { childId, deviceName, deviceType, platform, scopes } = parseResult.data;

    // Verify child belongs to user
    const childResult = await pool.query(
      'SELECT id FROM children WHERE id = $1 AND user_id = $2',
      [childId, userId]
    );

    if (childResult.rows.length === 0) {
      return reply.code(403).send({
        error: 'Child not found or access denied',
      });
    }

    // Create device record
    const deviceId = uuidv4();
    try {
      const deviceQuery = `
        INSERT INTO devices (
          id, user_id, child_id, device_name, device_type, is_active, last_seen_at
        ) VALUES ($1, $2, $3, $4, $5, true, NOW())
        RETURNING id, child_id, device_name
      `;

      const deviceInsertResult = await pool.query(deviceQuery, [
        deviceId,
        userId,
        childId,
        deviceName,
        deviceType,
      ]);

      if (deviceInsertResult.rows.length === 0) {
        return reply.code(500).send({
          error: 'Failed to create device',
        });
      }

      const device = deviceInsertResult.rows[0];

      // Generate JWT token (24 hours = 86400 seconds)
      const expiresIn = 86400;
      const deviceToken = jwt.sign(
        {
          deviceId: device.id,
          deviceName: device.device_name,
          childId: device.child_id,
          userId,
          scopes,
        },
        process.env.DEVICE_TOKEN_SECRET || 'device-secret',
        { expiresIn }
      );

      // Log audit event
      logger.info('Device token generated', {
        userId,
        childId,
        deviceId: device.id,
        deviceName,
      });

      return reply.code(200).send({
        deviceToken,
        deviceId: device.id,
        expiresIn,
      });
    } catch (dbError) {
      logger.error('Device creation error', {
        error: dbError instanceof Error ? dbError.message : 'Unknown error',
        userId,
        childId,
        deviceType,
      });
      return reply.code(500).send({
        error: 'Failed to create device',
      });
    }
  } catch (error) {
    logger.error('Error generating device token', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return reply.code(500).send({
      error: 'Failed to generate token',
    });
  }
}

/**
 * Refresh device authentication token
 * POST /api/auth/device-token/refresh
 */
async function refreshDeviceToken(request: FastifyRequest, reply: FastifyReply) {
  try {
    // Accept both 'token' and 'deviceToken' field names for compatibility
    const body = request.body as any;
    const refreshToken = body.token || body.deviceToken;
    const deviceId = body.deviceId;

    if (!refreshToken) {
      return reply.code(400).send({
        error: 'Device token is required',
      });
    }

    if (!deviceId) {
      return reply.code(400).send({
        error: 'Device ID is required',
      });
    }

    // Verify refresh token
    let decoded: DeviceTokenClaims;
    try {
      decoded = jwt.verify(
        refreshToken,
        process.env.DEVICE_TOKEN_SECRET || 'device-secret'
      ) as DeviceTokenClaims;
    } catch (error) {
      return reply.code(401).send({
        error: 'Invalid or expired token',
      });
    }

    // Verify deviceId matches (security check)
    if (decoded.deviceId !== deviceId) {
      return reply.code(401).send({
        error: 'Device ID mismatch',
      });
    }

    // Verify device still exists and is active
    const deviceResult = await pool.query(
      'SELECT id, is_active FROM devices WHERE id = $1',
      [decoded.deviceId]
    );

    if (deviceResult.rows.length === 0 || !deviceResult.rows[0].is_active) {
      return reply.code(403).send({
        error: 'Device not found or inactive',
      });
    }

    // Generate new token (24 hours = 86400 seconds)
    const expiresIn = 86400;
    const newDeviceToken = jwt.sign(
      {
        deviceId: decoded.deviceId,
        deviceName: decoded.deviceName,
        childId: decoded.childId,
        userId: decoded.userId,
        scopes: decoded.scopes || ['events:write', 'status:read'],
      },
      process.env.DEVICE_TOKEN_SECRET || 'device-secret',
      { expiresIn }
    );

    logger.info('Device token refreshed', {
      userId: decoded.userId,
      deviceId: decoded.deviceId,
    });

    return reply.code(200).send({
      deviceToken: newDeviceToken,
      expiresIn,
    });
  } catch (error) {
    logger.error('Error refreshing device token', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return reply.code(500).send({
      error: 'Failed to refresh token',
    });
  }
}

/**
 * Register routes
 */
const deviceAuthRoutes: FastifyPluginAsync = async (fastify) => {
  // Generate device token
  fastify.post(
    '/device-token',
    {
      onRequest: [authenticate],
      schema: {
        description: 'Generate device authentication token',
        tags: ['authentication'],
        body: {
          type: 'object',
          required: ['childId', 'deviceName', 'deviceType', 'platform'],
          properties: {
            childId: { type: 'string', format: 'uuid' },
            deviceName: { type: 'string' },
            deviceType: { type: 'string', enum: ['desktop', 'mobile', 'tablet', 'chromebook'] },
            platform: { type: 'string', enum: ['windows', 'macos', 'linux', 'ios', 'android'] },
            scopes: { type: 'array', items: { type: 'string' } },
          },
        },
        response: {
          200: {
            type: 'object',
            properties: {
              deviceToken: { type: 'string' },
              deviceId: { type: 'string', format: 'uuid' },
              expiresIn: { type: 'number' },
            },
          },
        },
      },
    },
    generateDeviceToken
  );

  // Refresh device token
  fastify.post(
    '/device-token/refresh',
    {
      schema: {
        description: 'Refresh device authentication token',
        tags: ['authentication'],
        body: {
          type: 'object',
          required: ['token'],
          properties: {
            token: { type: 'string' },
            deviceToken: { type: 'string' },  // Accept both field names
          },
        },
        response: {
          200: {
            type: 'object',
            properties: {
              deviceToken: { type: 'string' },
              expiresIn: { type: 'number' },
            },
          },
        },
      },
    },
    refreshDeviceToken
  );

  logger.info('Device authentication routes registered');
};

export default deviceAuthRoutes;
