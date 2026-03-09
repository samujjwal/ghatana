/**
 * Audit Log Hash Chain
 *
 * Provides tamper-evident integrity for audit logs by chaining
 * SHA-256 hashes. Each new entry's hash includes the previous
 * entry's hash, forming an immutable chain.
 *
 * @doc.type class
 * @doc.purpose Tamper-evident hash chain for audit logs
 * @doc.layer security
 * @doc.pattern Integrity
 */

import { createHash } from 'crypto';
import type { PrismaClient } from '@ghatana/tutorputor-db';

// =============================================================================
// Types
// =============================================================================

export interface AuditHashEntry {
    entryId: string;
    tenantId: string;
    sequenceNumber: number;
    entryHash: string;
    previousHash: string;
    payload: string;
    createdAt: Date;
}

export interface HashVerificationResult {
    valid: boolean;
    checkedCount: number;
    firstBrokenIndex?: number;
    firstBrokenEntryId?: string;
    error?: string;
}

// =============================================================================
// Hash Chain
// =============================================================================

const GENESIS_HASH = '0'.repeat(64);

export class AuditHashChain {
    constructor(private readonly prisma: PrismaClient) {}

    /**
     * Compute SHA-256 hash for an audit entry, chaining to the previous hash.
     */
    computeHash(payload: string, previousHash: string): string {
        return createHash('sha256')
            .update(previousHash)
            .update(payload)
            .digest('hex');
    }

    /**
     * Append a new audit log entry to the hash chain.
     * Retrieves the latest chain entry for the tenant and extends it.
     */
    async appendEntry(
        tenantId: string,
        entryId: string,
        payload: Record<string, unknown>,
    ): Promise<AuditHashEntry> {
        const serialized = JSON.stringify(payload, Object.keys(payload).sort());

        // Get latest chain entry for this tenant
        const latest = await this.prisma.auditHashChain.findFirst({
            where: { tenantId },
            orderBy: { sequenceNumber: 'desc' },
        });

        const previousHash = latest?.entryHash ?? GENESIS_HASH;
        const sequenceNumber = (latest?.sequenceNumber ?? 0) + 1;
        const entryHash = this.computeHash(serialized, previousHash);

        const entry = await this.prisma.auditHashChain.create({
            data: {
                entryId,
                tenantId,
                sequenceNumber,
                entryHash,
                previousHash,
                payload: serialized,
            },
        });

        return {
            entryId: entry.entryId,
            tenantId: entry.tenantId,
            sequenceNumber: entry.sequenceNumber,
            entryHash: entry.entryHash,
            previousHash: entry.previousHash,
            payload: entry.payload,
            createdAt: entry.createdAt,
        };
    }

    /**
     * Verify the integrity of the hash chain for a tenant.
     * Walks the chain from the beginning and checks each link.
     */
    async verifyChain(
        tenantId: string,
        options: { limit?: number } = {},
    ): Promise<HashVerificationResult> {
        const { limit = 10000 } = options;

        const entries = await this.prisma.auditHashChain.findMany({
            where: { tenantId },
            orderBy: { sequenceNumber: 'asc' },
            take: limit,
        });

        if (entries.length === 0) {
            return { valid: true, checkedCount: 0 };
        }

        let expectedPreviousHash = GENESIS_HASH;

        for (let i = 0; i < entries.length; i++) {
            const entry = entries[i]!;

            // Check previous hash link
            if (entry.previousHash !== expectedPreviousHash) {
                return {
                    valid: false,
                    checkedCount: i + 1,
                    firstBrokenIndex: i,
                    firstBrokenEntryId: entry.entryId,
                    error: `Chain broken at index ${i}: previous hash mismatch`,
                };
            }

            // Recompute hash and verify
            const recomputed = this.computeHash(entry.payload, entry.previousHash);
            if (recomputed !== entry.entryHash) {
                return {
                    valid: false,
                    checkedCount: i + 1,
                    firstBrokenIndex: i,
                    firstBrokenEntryId: entry.entryId,
                    error: `Chain broken at index ${i}: entry hash tampered`,
                };
            }

            expectedPreviousHash = entry.entryHash;
        }

        return { valid: true, checkedCount: entries.length };
    }

    /**
     * Get the latest hash for a tenant (for external verification).
     */
    async getLatestHash(tenantId: string): Promise<string | null> {
        const latest = await this.prisma.auditHashChain.findFirst({
            where: { tenantId },
            orderBy: { sequenceNumber: 'desc' },
        });
        return latest?.entryHash ?? null;
    }
}
