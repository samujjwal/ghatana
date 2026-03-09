/*
  Fastify type augmentations for project-wide use.

  This file declares the minimal plugin-provided members used by the
  Guardian backend so TypeScript understands methods like
  `reply.setCookie`, `reply.clearCookie`, `request.cookies`, and some
  rate-limit/httpErrors helpers. It is intentionally conservative and
  focuses only on the members used by the codebase.
*/

import 'fastify';

declare module 'fastify' {
  interface FastifyReply {
    /** Set an HTTP cookie (provided by @fastify/cookie) */
    setCookie(name: string, value: string, options?: Record<string, any>): FastifyReply;
    /** Clear an HTTP cookie (provided by @fastify/cookie) */
    clearCookie(name: string, options?: Record<string, any>): FastifyReply;
  }

  interface FastifyRequest {
    /** Parsed cookies (provided by @fastify/cookie) */
    cookies?: Record<string, string>;
  }

  interface FastifyInstance {
    /** Some plugins add convenience helpers; keep permissive shapes */
    serializeCookie?: (...args: any[]) => any;
    parseCookie?: (...args: any[]) => any;
    createRateLimit?: (...args: any[]) => any;
    rateLimit?: any;
    httpErrors?: Record<string, any>;
  }
}
