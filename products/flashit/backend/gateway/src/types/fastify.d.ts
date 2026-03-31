/**
 * Fastify Type Declarations
 * Adds type safety for Fastify decorators and request properties
 */

import { PrismaClient } from '../generated/prisma';

declare module 'fastify' {
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
  }
  
  interface FastifyReply {
    // Add any custom reply methods here
  }
}
