/**
 * SSE Utilities
 *
 * Shared helpers for Server-Sent Events responses.
 *
 * @doc.type module
 * @doc.purpose Reusable SSE event serialization for Fastify routes
 * @doc.layer product
 * @doc.pattern Transport Utility
 */

import type { FastifyReply } from "fastify";

export function writeSseEvent(
  raw: FastifyReply["raw"],
  event: string,
  data: unknown,
): void {
  raw.write(`event: ${event}\n`);
  raw.write(`data: ${JSON.stringify(data)}\n\n`);
}
