/**
 * Prisma utilities for domain loader.
 *
 * @doc.type module
 * @doc.purpose Centralized Prisma client access
 * @doc.layer product
 * @doc.pattern Utility
 */

import { createPrismaClient as createDbPrismaClient } from "@ghatana/tutorputor-db";
import type { TutorPrismaClient as DbTutorPrismaClient } from "@ghatana/tutorputor-db";

export type TutorPrismaClient = DbTutorPrismaClient;

/**
 * Create a new Prisma client instance with libsql adapter.
 * Required for Prisma ORM 7. Uses libsql for better Node.js 24 compatibility.
 */
export function createPrismaClient(): TutorPrismaClient {
  return createDbPrismaClient();
}

/**
 * Transaction client type for Prisma interactive transactions.
 */
export type TransactionClient = Omit<
  TutorPrismaClient,
  "$connect" | "$disconnect" | "$on" | "$transaction" | "$use" | "$extends"
>;
