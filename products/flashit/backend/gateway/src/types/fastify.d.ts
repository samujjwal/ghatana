/**
 * Fastify Type Declarations
 * Adds type safety for Fastify decorators and request properties
 */

import type { FastifyReply, FastifyRequest } from 'fastify';
import type { JwtPayload } from '../lib/auth.js';
import type { PrismaClient } from '../../../generated/prisma/index.js';

declare module '@fastify/jwt' {
  interface FastifyJWT {
    payload: JwtPayload;
    user: JwtPayload;
  }
}

declare module 'fastify' {
  interface RequestGenericInterface {
    Body: any;
    Querystring: any;
    Params: any;
  }

  interface FastifyInstance {
    prisma: PrismaClient;
    authenticate: (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
  
  interface FastifyRequest {
    user: {
      userId: string;
      email: string;
      role: string;
    };
    body: any;
    query: any;
    params: any;
    rawBody?: string | Buffer;
  }
  
  interface FastifyReply {
    code(statusCode: number): FastifyReply;
    status(statusCode: number): FastifyReply;
  }
}
