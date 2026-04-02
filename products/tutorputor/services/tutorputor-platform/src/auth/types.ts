/**
 * Fastify Request Type Extensions
 *
 * Extends FastifyRequest with custom properties for authentication
 */

import type { AuthContext } from "./index.js";

type AuthenticatedRequestUser = AuthContext["user"] & {
  userId?: string;
};

declare module "fastify" {
  export interface FastifyRequest {
    authContext?: AuthContext;
    user?: AuthenticatedRequestUser;
  }
}
