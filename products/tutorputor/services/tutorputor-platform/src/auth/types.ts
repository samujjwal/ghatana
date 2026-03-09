/**
 * Fastify Request Type Extensions
 * 
 * Extends FastifyRequest with custom properties for authentication
 */

import { FastifyRequest } from 'fastify';
import { AuthContext } from './index.js';

declare module 'fastify' {
  export interface FastifyRequest {
    authContext?: AuthContext;
  }
}
