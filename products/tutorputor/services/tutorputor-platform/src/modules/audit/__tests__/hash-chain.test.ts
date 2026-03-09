/**
 * Audit Hash Chain Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test tamper-evident hash chain for audit logs
 * @doc.layer security
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuditHashChain } from '../hash-chain';
import type { PrismaClient } from '@ghatana/tutorputor-db';

const GENESIS_HASH = '0'.repeat(64);

function makeMockPrisma() {
    return {
        auditHashChain: {
            findFirst: vi.fn(),
            findMany: vi.fn(),
            create: vi.fn(),
        },
    } as unknown as PrismaClient;
}

describe('AuditHashChain', () => {
    let chain: AuditHashChain;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        chain = new AuditHashChain(prisma);
    });

    // =========================================================================
    // computeHash
    // =========================================================================
    describe('computeHash', () => {
        it('returns a 64-char hex string', () => {
            const hash = chain.computeHash('payload', GENESIS_HASH);
            expect(hash).toMatch(/^[0-9a-f]{64}$/);
        });

        it('is deterministic', () => {
            const a = chain.computeHash('data', 'prev');
            const b = chain.computeHash('data', 'prev');
            expect(a).toBe(b);
        });

        it('changes with different payload', () => {
            const a = chain.computeHash('data1', 'prev');
            const b = chain.computeHash('data2', 'prev');
            expect(a).not.toBe(b);
        });

        it('changes with different previous hash', () => {
            const a = chain.computeHash('data', 'prev1');
            const b = chain.computeHash('data', 'prev2');
            expect(a).not.toBe(b);
        });
    });

    // =========================================================================
    // appendEntry
    // =========================================================================
    describe('appendEntry', () => {
        it('creates first entry with genesis hash as previous', async () => {
            (prisma.auditHashChain.findFirst as any).mockResolvedValue(null);
            (prisma.auditHashChain.create as any).mockImplementation(({ data }: any) => ({
                ...data,
                createdAt: new Date(),
            }));

            const entry = await chain.appendEntry('t1', 'entry-1', { action: 'create', resource: 'module' });

            expect(entry.sequenceNumber).toBe(1);
            expect(entry.previousHash).toBe(GENESIS_HASH);
            expect(entry.entryHash).toMatch(/^[0-9a-f]{64}$/);
        });

        it('chains to previous entry hash', async () => {
            const prevHash = 'a'.repeat(64);
            (prisma.auditHashChain.findFirst as any).mockResolvedValue({
                entryHash: prevHash,
                sequenceNumber: 5,
            });
            (prisma.auditHashChain.create as any).mockImplementation(({ data }: any) => ({
                ...data,
                createdAt: new Date(),
            }));

            const entry = await chain.appendEntry('t1', 'entry-6', { action: 'delete' });

            expect(entry.sequenceNumber).toBe(6);
            expect(entry.previousHash).toBe(prevHash);
        });

        it('serializes payload with sorted keys for determinism', async () => {
            (prisma.auditHashChain.findFirst as any).mockResolvedValue(null);
            (prisma.auditHashChain.create as any).mockImplementation(({ data }: any) => ({
                ...data,
                createdAt: new Date(),
            }));

            const entry1 = await chain.appendEntry('t1', 'e1', { b: 2, a: 1 });

            // Reset mock
            (prisma.auditHashChain.findFirst as any).mockResolvedValue(null);

            const entry2 = await chain.appendEntry('t1', 'e2', { a: 1, b: 2 });

            // Same payload in different order should produce same hash
            expect(entry1.entryHash).toBe(entry2.entryHash);
        });
    });

    // =========================================================================
    // verifyChain
    // =========================================================================
    describe('verifyChain', () => {
        it('returns valid for empty chain', async () => {
            (prisma.auditHashChain.findMany as any).mockResolvedValue([]);

            const result = await chain.verifyChain('t1');
            expect(result.valid).toBe(true);
            expect(result.checkedCount).toBe(0);
        });

        it('verifies a valid chain', async () => {
            const payload1 = JSON.stringify({ action: 'create' });
            const hash1 = chain.computeHash(payload1, GENESIS_HASH);

            const payload2 = JSON.stringify({ action: 'update' });
            const hash2 = chain.computeHash(payload2, hash1);

            (prisma.auditHashChain.findMany as any).mockResolvedValue([
                { entryId: 'e1', sequenceNumber: 1, previousHash: GENESIS_HASH, entryHash: hash1, payload: payload1 },
                { entryId: 'e2', sequenceNumber: 2, previousHash: hash1, entryHash: hash2, payload: payload2 },
            ]);

            const result = await chain.verifyChain('t1');
            expect(result.valid).toBe(true);
            expect(result.checkedCount).toBe(2);
        });

        it('detects tampered entry hash', async () => {
            const payload1 = JSON.stringify({ action: 'create' });
            const hash1 = chain.computeHash(payload1, GENESIS_HASH);

            (prisma.auditHashChain.findMany as any).mockResolvedValue([
                { entryId: 'e1', sequenceNumber: 1, previousHash: GENESIS_HASH, entryHash: hash1, payload: payload1 },
                { entryId: 'e2', sequenceNumber: 2, previousHash: hash1, entryHash: 'tampered', payload: '{"action":"update"}' },
            ]);

            const result = await chain.verifyChain('t1');
            expect(result.valid).toBe(false);
            expect(result.firstBrokenIndex).toBe(1);
            expect(result.firstBrokenEntryId).toBe('e2');
            expect(result.error).toContain('tampered');
        });

        it('detects broken previous hash link', async () => {
            const payload1 = JSON.stringify({ action: 'create' });
            const hash1 = chain.computeHash(payload1, GENESIS_HASH);

            const payload2 = JSON.stringify({ action: 'update' });
            const hash2 = chain.computeHash(payload2, 'wrong_previous');

            (prisma.auditHashChain.findMany as any).mockResolvedValue([
                { entryId: 'e1', sequenceNumber: 1, previousHash: GENESIS_HASH, entryHash: hash1, payload: payload1 },
                { entryId: 'e2', sequenceNumber: 2, previousHash: 'wrong_previous', entryHash: hash2, payload: payload2 },
            ]);

            const result = await chain.verifyChain('t1');
            expect(result.valid).toBe(false);
            expect(result.firstBrokenIndex).toBe(1);
            expect(result.error).toContain('previous hash mismatch');
        });
    });

    // =========================================================================
    // getLatestHash
    // =========================================================================
    describe('getLatestHash', () => {
        it('returns null for empty chain', async () => {
            (prisma.auditHashChain.findFirst as any).mockResolvedValue(null);

            const hash = await chain.getLatestHash('t1');
            expect(hash).toBeNull();
        });

        it('returns latest hash', async () => {
            (prisma.auditHashChain.findFirst as any).mockResolvedValue({
                entryHash: 'abc123',
            });

            const hash = await chain.getLatestHash('t1');
            expect(hash).toBe('abc123');
        });
    });
});
