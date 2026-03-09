/**
 * Chat Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for rooms, messages, moderation
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ChatServiceImpl } from '../chat';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        chatRoom: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            update: vi.fn(),
        },
        chatMessage: {
            create: vi.fn(),
            findMany: vi.fn(),
            findUnique: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
            updateMany: vi.fn(),
        },
    } as unknown as PrismaClient;
}

function makeMockRedis() {
    return {
        publish: vi.fn().mockResolvedValue(1),
    };
}

function makeRoomRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'room-1',
        tenantId: 't1',
        type: 'DIRECT',
        studyGroupId: null,
        tutoringSessionId: null,
        participants: JSON.stringify(['u1', 'u2']),
        messageCount: 0,
        lastMessageAt: null,
        retentionDays: 90,
        status: 'ACTIVE',
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

function makeMessageRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'msg-1',
        roomId: 'room-1',
        senderId: 'u1',
        senderName: 'Alice',
        type: 'TEXT',
        content: 'Hello!',
        metadata: null,
        replyToId: null,
        attachments: null,
        reactions: JSON.stringify({}),
        status: 'sent',
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

describe('ChatServiceImpl', () => {
    let service: ChatServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;
    let redis: ReturnType<typeof makeMockRedis>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        redis = makeMockRedis();
        service = new ChatServiceImpl({ prisma, redis: redis as any });
    });

    // =========================================================================
    // Room Management
    // =========================================================================
    describe('getOrCreateRoom', () => {
        it('creates a new direct room', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(null);
            (prisma.chatRoom.create as any).mockResolvedValue(makeRoomRow());

            const room = await service.getOrCreateRoom({
                tenantId: 't1' as any,
                type: 'direct',
                participants: ['u1' as any, 'u2' as any],
            });

            expect(room.id).toBe('room-1');
            expect(room.type).toBe('direct');
            expect(prisma.chatRoom.create).toHaveBeenCalled();
        });

        it('returns existing direct room for same participants', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(makeRoomRow());

            const room = await service.getOrCreateRoom({
                tenantId: 't1' as any,
                type: 'direct',
                participants: ['u1' as any, 'u2' as any],
            });

            expect(room.id).toBe('room-1');
            expect(prisma.chatRoom.create).not.toHaveBeenCalled();
        });

        it('returns existing study group room', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(
                makeRoomRow({ type: 'GROUP', studyGroupId: 'sg-1' }),
            );

            const room = await service.getOrCreateRoom({
                tenantId: 't1' as any,
                type: 'group',
                participants: ['u1' as any, 'u2' as any, 'u3' as any],
                studyGroupId: 'sg-1',
            });

            expect(prisma.chatRoom.create).not.toHaveBeenCalled();
        });
    });

    describe('getRoom', () => {
        it('returns room for participant', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(makeRoomRow());

            const room = await service.getRoom({
                tenantId: 't1' as any,
                roomId: 'room-1',
                userId: 'u1' as any,
            });

            expect(room.id).toBe('room-1');
        });

        it('throws for non-participant', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(makeRoomRow());

            await expect(
                service.getRoom({
                    tenantId: 't1' as any,
                    roomId: 'room-1',
                    userId: 'u3' as any, // Not in participants
                }),
            ).rejects.toThrow('Not a participant');
        });

        it('throws for non-existent room', async () => {
            (prisma.chatRoom.findFirst as any).mockResolvedValue(null);

            await expect(
                service.getRoom({
                    tenantId: 't1' as any,
                    roomId: 'nonexistent',
                    userId: 'u1' as any,
                }),
            ).rejects.toThrow('Chat room not found');
        });
    });

    describe('listRooms', () => {
        it('returns rooms where user is a participant', async () => {
            (prisma.chatRoom.findMany as any).mockResolvedValue([
                makeRoomRow({ participants: JSON.stringify(['u1', 'u2']) }),
                makeRoomRow({ id: 'room-2', participants: JSON.stringify(['u3', 'u4']) }),
            ]);

            const result = await service.listRooms({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                pagination: { offset: 0, limit: 20 },
            });

            expect(result.items).toHaveLength(1);
            expect(result.items[0].id).toBe('room-1');
        });
    });

    // =========================================================================
    // Messages
    // =========================================================================
    describe('sendMessage', () => {
        it('creates message and publishes to Redis', async () => {
            (prisma.chatRoom.findUnique as any).mockResolvedValue(makeRoomRow());
            (prisma.chatMessage.create as any).mockResolvedValue(makeMessageRow());
            (prisma.chatRoom.update as any).mockResolvedValue({});

            const msg = await service.sendMessage({
                tenantId: 't1' as any,
                roomId: 'room-1',
                senderId: 'u1' as any,
                type: 'text',
                content: 'Hello!',
            });

            expect(msg.content).toBe('Hello!');
            expect(prisma.chatRoom.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ messageCount: { increment: 1 } }),
                }),
            );
            // Redis publish for room and for other participant
            expect(redis.publish).toHaveBeenCalledTimes(2);
        });

        it('throws for non-participant sender', async () => {
            (prisma.chatRoom.findUnique as any).mockResolvedValue(makeRoomRow());

            await expect(
                service.sendMessage({
                    tenantId: 't1' as any,
                    roomId: 'room-1',
                    senderId: 'u3' as any, // Not a participant
                    type: 'text',
                    content: 'Hacked!',
                }),
            ).rejects.toThrow('Not a participant');
        });

        it('throws for message exceeding max length', async () => {
            (prisma.chatRoom.findUnique as any).mockResolvedValue(makeRoomRow());
            const longContent = 'a'.repeat(10001);

            await expect(
                service.sendMessage({
                    tenantId: 't1' as any,
                    roomId: 'room-1',
                    senderId: 'u1' as any,
                    type: 'text',
                    content: longContent,
                }),
            ).rejects.toThrow('Message too long');
        });

        it('throws for non-existent room', async () => {
            (prisma.chatRoom.findUnique as any).mockResolvedValue(null);

            await expect(
                service.sendMessage({
                    tenantId: 't1' as any,
                    roomId: 'nonexistent',
                    senderId: 'u1' as any,
                    type: 'text',
                    content: 'Hello',
                }),
            ).rejects.toThrow('Chat room not found');
        });
    });

    // =========================================================================
    // No Redis fallback
    // =========================================================================
    describe('without Redis', () => {
        it('sends message without Redis publish', async () => {
            const noRedisService = new ChatServiceImpl({ prisma });

            (prisma.chatRoom.findUnique as any).mockResolvedValue(makeRoomRow());
            (prisma.chatMessage.create as any).mockResolvedValue(makeMessageRow());
            (prisma.chatRoom.update as any).mockResolvedValue({});

            const msg = await noRedisService.sendMessage({
                tenantId: 't1' as any,
                roomId: 'room-1',
                senderId: 'u1' as any,
                type: 'text',
                content: 'Hello!',
            });

            expect(msg.content).toBe('Hello!');
            expect(redis.publish).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    // Config defaults
    // =========================================================================
    describe('configuration', () => {
        it('defaults retention to 90 days', () => {
            const svc = new ChatServiceImpl({ prisma });
            // Access via room creation
            expect(svc['defaultRetentionDays']).toBe(90);
        });

        it('defaults max message length to 10000', () => {
            const svc = new ChatServiceImpl({ prisma });
            expect(svc['maxMessageLength']).toBe(10000);
        });

        it('respects custom config', () => {
            const svc = new ChatServiceImpl({
                prisma,
                defaultRetentionDays: 30,
                maxMessageLength: 500,
            });
            expect(svc['defaultRetentionDays']).toBe(30);
            expect(svc['maxMessageLength']).toBe(500);
        });
    });
});
