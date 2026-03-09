/**
 * @doc.type module
 * @doc.purpose Chat service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { Redis } from 'ioredis';
import type {
    ChatService,
    TenantId,
    UserId,
    PaginationArgs,
    PaginatedResult,
} from '@ghatana/tutorputor-contracts';

import type {
    ChatRoom,
    ChatMessage,
} from '@ghatana/tutorputor-contracts/v1/social';

/**
 * Configuration for ChatServiceImpl
 */
export interface ChatServiceConfig {
    prisma: PrismaClient;
    redis?: Redis;
    defaultRetentionDays?: number;
    maxMessageLength?: number;
}

/**
 * Implementation of the Chat service.
 */
export class ChatServiceImpl implements ChatService {
    private readonly prisma: PrismaClient;
    private readonly redis?: Redis;
    private readonly defaultRetentionDays: number;
    private readonly maxMessageLength: number;

    constructor(config: ChatServiceConfig) {
        this.prisma = config.prisma;
        this.redis = config.redis;
        this.defaultRetentionDays = config.defaultRetentionDays ?? 90;
        this.maxMessageLength = config.maxMessageLength ?? 10000;
    }

    async getOrCreateRoom(args: {
        tenantId: TenantId;
        type: ChatRoom['type'];
        participants: UserId[];
        studyGroupId?: string;
        tutoringSessionId?: string;
    }): Promise<ChatRoom> {
        // For study group rooms, check if one exists
        if (args.studyGroupId) {
            const existing = await this.prisma.chatRoom.findFirst({
                where: {
                    tenantId: args.tenantId,
                    studyGroupId: args.studyGroupId,
                },
            });

            if (existing) {
                return this.mapRoomFromDb(existing);
            }
        }

        // For tutoring session rooms
        if (args.tutoringSessionId) {
            const existing = await this.prisma.chatRoom.findFirst({
                where: {
                    tenantId: args.tenantId,
                    tutoringSessionId: args.tutoringSessionId,
                },
            });

            if (existing) {
                return this.mapRoomFromDb(existing);
            }
        }

        // For direct messages, check existing
        if (args.type === 'direct' && args.participants.length === 2) {
            const sortedParticipants = [...args.participants].sort();
            const existing = await this.prisma.chatRoom.findFirst({
                where: {
                    tenantId: args.tenantId,
                    type: 'DIRECT',
                    participants: JSON.stringify(sortedParticipants),
                },
            });

            if (existing) {
                return this.mapRoomFromDb(existing);
            }
        }

        const sortedParticipants = [...args.participants].sort();

        const room = await this.prisma.chatRoom.create({
            data: {
                tenantId: args.tenantId,
                type: this.mapRoomTypeToDb(args.type),
                studyGroupId: args.studyGroupId,
                tutoringSessionId: args.tutoringSessionId,
                participants: JSON.stringify(sortedParticipants),
                retentionDays: this.defaultRetentionDays,
            },
        });

        return this.mapRoomFromDb(room);
    }

    async getRoom(args: {
        tenantId: TenantId;
        roomId: string;
        userId: UserId;
    }): Promise<ChatRoom> {
        const room = await this.prisma.chatRoom.findFirst({
            where: {
                id: args.roomId,
                tenantId: args.tenantId,
            },
        });

        if (!room) {
            throw new Error('Chat room not found');
        }

        // Check if user is a participant
        const participants: string[] = JSON.parse(room.participants);
        if (!participants.includes(args.userId)) {
            throw new Error('Not a participant in this room');
        }

        return this.mapRoomFromDb(room);
    }

    async listRooms(args: {
        tenantId: TenantId;
        userId: UserId;
        type?: ChatRoom['type'];
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<ChatRoom>> {
        // SQLite doesn't support JSON array contains, so we need a workaround
        const allRooms = await this.prisma.chatRoom.findMany({
            where: {
                tenantId: args.tenantId,
                ...(args.type && { type: this.mapRoomTypeToDb(args.type) }),
            },
            orderBy: { lastMessageAt: 'desc' },
        });

        // Filter rooms where user is a participant
        const userRooms = allRooms.filter((room: any) => {
            const participants: string[] = JSON.parse(room.participants);
            return participants.includes(args.userId);
        });

        const offset = args.pagination.offset ?? 0;
        const limit = args.pagination.limit ?? 20;
        const paginatedRooms = userRooms.slice(offset, offset + limit);

        return {
            items: paginatedRooms.map((r: any) => this.mapRoomFromDb(r)),
            totalCount: userRooms.length,
            total: userRooms.length,
            hasMore: offset + paginatedRooms.length < userRooms.length,
        };
    }

    async sendMessage(args: {
        tenantId: TenantId;
        roomId: string;
        senderId: UserId;
        type: ChatMessage['type'];
        content: string;
        metadata?: Record<string, unknown>;
        replyToId?: string;
        attachments?: Array<{ name: string; type: string; url: string }>;
    }): Promise<ChatMessage> {
        const room = await this.prisma.chatRoom.findUnique({
            where: { id: args.roomId },
        });

        if (!room) {
            throw new Error('Chat room not found');
        }

        // Check if sender is a participant
        const participants: string[] = JSON.parse(room.participants);
        if (!participants.includes(args.senderId)) {
            throw new Error('Not a participant in this room');
        }

        if (args.content.length > this.maxMessageLength) {
            throw new Error(`Message too long. Maximum ${this.maxMessageLength} characters.`);
        }

        const message = await this.prisma.chatMessage.create({
            data: {
                roomId: args.roomId,
                senderId: args.senderId,
                senderName: '', // Would be populated from user service
                type: this.mapMessageTypeToDb(args.type),
                content: args.content,
                metadata: args.metadata ? JSON.stringify(args.metadata) : null,
                replyToId: args.replyToId,
                attachments: args.attachments ? JSON.stringify(args.attachments) : null,
                reactions: JSON.stringify({}),
                status: 'sent',
            },
        });

        // Update room stats
        await this.prisma.chatRoom.update({
            where: { id: args.roomId },
            data: {
                messageCount: { increment: 1 },
                lastMessageAt: new Date(),
            },
        });

        // Publish message to Redis for real-time delivery
        if (this.redis) {
            await this.redis.publish(
                `chat:room:${args.roomId}`,
                JSON.stringify({
                    type: 'new_message',
                    message: this.mapMessageFromDb(message),
                })
            );

            // Notify other participants
            for (const participantId of participants) {
                if (participantId !== args.senderId) {
                    await this.redis.publish(
                        `chat:user:${participantId}`,
                        JSON.stringify({
                            type: 'new_message',
                            roomId: args.roomId,
                            messageId: message.id,
                        })
                    );
                }
            }
        }

        return this.mapMessageFromDb(message);
    }

    async getMessages(args: {
        tenantId: TenantId;
        roomId: string;
        userId: UserId;
        before?: string;
        limit?: number;
    }): Promise<{ messages: ChatMessage[]; hasMore: boolean }> {
        const room = await this.prisma.chatRoom.findUnique({
            where: { id: args.roomId },
        });

        if (!room) {
            throw new Error('Chat room not found');
        }

        // Check if user is a participant
        const participants: string[] = JSON.parse(room.participants);
        if (!participants.includes(args.userId)) {
            throw new Error('Not a participant in this room');
        }

        const limit = args.limit ?? 50;
        const where: any = {
            roomId: args.roomId,
            status: { not: 'deleted' },
        };

        if (args.before) {
            const beforeMessage = await this.prisma.chatMessage.findUnique({
                where: { id: args.before },
            });
            if (beforeMessage) {
                where.createdAt = { lt: beforeMessage.createdAt };
            }
        }

        const messages = await this.prisma.chatMessage.findMany({
            where,
            take: limit + 1, // Get one extra to check hasMore
            orderBy: { createdAt: 'desc' },
        });

        const hasMore = messages.length > limit;
        const resultMessages = messages.slice(0, limit).reverse();

        // Update read receipt
        if (resultMessages.length > 0) {
            const lastMessage = resultMessages[resultMessages.length - 1];
            await this.prisma.chatReadReceipt.upsert({
                where: {
                    roomId_userId: {
                        roomId: args.roomId,
                        userId: args.userId,
                    },
                },
                create: {
                    roomId: args.roomId,
                    userId: args.userId,
                    lastReadMessageId: lastMessage.id,
                    lastReadAt: new Date(),
                },
                update: {
                    lastReadMessageId: lastMessage.id,
                    lastReadAt: new Date(),
                },
            });
        }

        return {
            messages: resultMessages.map((m: any) => this.mapMessageFromDb(m)),
            hasMore,
        };
    }

    async reactToMessage(args: {
        tenantId: TenantId;
        messageId: string;
        userId: UserId;
        emoji: string;
    }): Promise<ChatMessage> {
        const message = await this.prisma.chatMessage.findUnique({
            where: { id: args.messageId },
        });

        if (!message) {
            throw new Error('Message not found');
        }

        const reactions: Record<string, string[]> = JSON.parse(
            message.reactions || '{}'
        );

        // Add reaction
        if (!reactions[args.emoji]) {
            reactions[args.emoji] = [];
        }
        if (!reactions[args.emoji]!.includes(args.userId)) {
            reactions[args.emoji]!.push(args.userId);
        }

        const updated = await this.prisma.chatMessage.update({
            where: { id: args.messageId },
            data: { reactions: JSON.stringify(reactions) },
        });

        // Publish reaction
        if (this.redis) {
            await this.redis.publish(
                `chat:room:${message.roomId}`,
                JSON.stringify({
                    type: 'reaction',
                    messageId: args.messageId,
                    emoji: args.emoji,
                    userId: args.userId,
                    action: 'add',
                })
            );
        }

        return this.mapMessageFromDb(updated);
    }

    async deleteMessage(args: {
        tenantId: TenantId;
        messageId: string;
        userId: UserId;
    }): Promise<void> {
        const message = await this.prisma.chatMessage.findUnique({
            where: { id: args.messageId },
        });

        if (!message) {
            throw new Error('Message not found');
        }

        if (message.senderId !== args.userId) {
            throw new Error('Only the sender can delete this message');
        }

        await this.prisma.chatMessage.update({
            where: { id: args.messageId },
            data: {
                status: 'deleted',
                deletedAt: new Date(),
            },
        });

        // Publish deletion
        if (this.redis) {
            await this.redis.publish(
                `chat:room:${message.roomId}`,
                JSON.stringify({
                    type: 'message_deleted',
                    messageId: args.messageId,
                })
            );
        }
    }

    async markAsRead(args: {
        tenantId: TenantId;
        roomId: string;
        userId: UserId;
        lastReadMessageId: string;
    }): Promise<void> {
        await this.prisma.chatReadReceipt.upsert({
            where: {
                roomId_userId: {
                    roomId: args.roomId,
                    userId: args.userId,
                },
            },
            create: {
                roomId: args.roomId,
                userId: args.userId,
                lastReadMessageId: args.lastReadMessageId,
                lastReadAt: new Date(),
            },
            update: {
                lastReadMessageId: args.lastReadMessageId,
                lastReadAt: new Date(),
            },
        });

        // Publish read receipt
        if (this.redis) {
            await this.redis.publish(
                `chat:room:${args.roomId}`,
                JSON.stringify({
                    type: 'read_receipt',
                    userId: args.userId,
                    lastReadMessageId: args.lastReadMessageId,
                })
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------

    private mapRoomTypeToDb(type: ChatRoom['type']): string {
        const map: Record<ChatRoom['type'], string> = {
            direct: 'DIRECT',
            study_group: 'STUDY_GROUP',
            classroom: 'CLASSROOM',
            tutoring: 'TUTORING',
            support: 'SUPPORT',
        };
        return map[type];
    }

    private mapRoomTypeFromDb(type: string): ChatRoom['type'] {
        const map: Record<string, ChatRoom['type']> = {
            DIRECT: 'direct',
            STUDY_GROUP: 'study_group',
            CLASSROOM: 'classroom',
            TUTORING: 'tutoring',
            SUPPORT: 'support',
        };
        return map[type] ?? 'direct';
    }

    private mapMessageTypeToDb(type: ChatMessage['type']): string {
        const map: Record<ChatMessage['type'], string> = {
            text: 'TEXT',
            image: 'IMAGE',
            file: 'FILE',
            code: 'CODE',
            math: 'MATH',
            quiz_share: 'QUIZ_SHARE',
            system: 'SYSTEM',
        };
        return map[type];
    }

    private mapMessageTypeFromDb(type: string): ChatMessage['type'] {
        const map: Record<string, ChatMessage['type']> = {
            TEXT: 'text',
            IMAGE: 'image',
            FILE: 'file',
            CODE: 'code',
            MATH: 'math',
            QUIZ_SHARE: 'quiz_share',
            SYSTEM: 'system',
        };
        return map[type] ?? 'text';
    }

    private mapRoomFromDb(room: any): ChatRoom {
        return {
            id: room.id,
            tenantId: room.tenantId,
            type: this.mapRoomTypeFromDb(room.type),
            name: room.name ?? undefined,
            studyGroupId: room.studyGroupId ?? undefined,
            classroomId: undefined,
            tutoringSessionId: room.tutoringSessionId ?? undefined,
            participants: JSON.parse(room.participants || '[]'),
            maxParticipants: room.maxParticipants ?? undefined,
            isEncrypted: room.isEncrypted,
            retentionDays: room.retentionDays,
            messageCount: room.messageCount,
            lastMessageAt: room.lastMessageAt ?? undefined,
            createdAt: room.createdAt,
            updatedAt: room.updatedAt,
        };
    }

    private mapMessageFromDb(message: any): ChatMessage {
        return {
            id: message.id,
            roomId: message.roomId,
            senderId: message.senderId,
            senderName: message.senderName,
            type: this.mapMessageTypeFromDb(message.type),
            content: message.content,
            metadata: message.metadata ? JSON.parse(message.metadata) : undefined,
            attachments: message.attachments
                ? JSON.parse(message.attachments)
                : undefined,
            replyToId: message.replyToId ?? undefined,
            reactions: JSON.parse(message.reactions || '{}'),
            status: message.status as any,
            editedAt: message.editedAt ?? undefined,
            deletedAt: message.deletedAt ?? undefined,
            createdAt: message.createdAt,
        };
    }
}
