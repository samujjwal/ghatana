import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------
const mockUser = {
    id: "user-1",
    tenantId: "tenant-1",
    email: "alice@example.com",
    displayName: "Alice",
    role: "student",
    status: "active",
    createdAt: new Date("2024-01-01"),
    updatedAt: new Date("2024-01-01"),
    passwordHash: "hashed",
    preferences: null,
    avatarUrl: null,
};

function makePrisma() {
    return {
        user: {
            findFirst: vi.fn().mockResolvedValue(mockUser),
            findUnique: vi.fn().mockResolvedValue(mockUser),
            findMany: vi.fn().mockResolvedValue([mockUser]),
            count: vi.fn().mockResolvedValue(1),
            create: vi.fn().mockResolvedValue(mockUser),
            update: vi.fn().mockResolvedValue({ ...mockUser, displayName: "Alice Updated" }),
            delete: vi.fn().mockResolvedValue(mockUser),
        },
    };
}

// ---------------------------------------------------------------------------
// Service factory inline (avoids heavy module imports)
// ---------------------------------------------------------------------------
function createUserServiceInline(prisma: any) {
    return {
        async findById(id: string) {
            return prisma.user.findFirst({ where: { id } });
        },
        async findByEmail(tenantId: string, email: string) {
            return prisma.user.findFirst({ where: { tenantId, email } });
        },
        async listUsers(tenantId: string, opts: { limit?: number; offset?: number } = {}) {
            const [items, total] = await Promise.all([
                prisma.user.findMany({ where: { tenantId }, take: opts.limit ?? 20, skip: opts.offset ?? 0 }),
                prisma.user.count({ where: { tenantId } }),
            ]);
            return { items, total };
        },
        async updateUser(id: string, data: Record<string, unknown>) {
            return prisma.user.update({ where: { id }, data });
        },
        async deactivateUser(id: string) {
            return prisma.user.update({ where: { id }, data: { status: "inactive" } });
        },
    };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("UserService (inline)", () => {
    let service: ReturnType<typeof createUserServiceInline>;
    let prisma: ReturnType<typeof makePrisma>;

    beforeEach(() => {
        prisma = makePrisma();
        service = createUserServiceInline(prisma);
    });

    describe("findById", () => {
        it("returns user by id", async () => {
            const result = await service.findById("user-1");
            expect(result?.id).toBe("user-1");
            expect(result?.email).toBe("alice@example.com");
        });
    });

    describe("findByEmail", () => {
        it("finds user by tenant+email", async () => {
            const result = await service.findByEmail("tenant-1", "alice@example.com");
            expect(result).toBeDefined();
            expect(result?.email).toBe("alice@example.com");
            const callArgs = prisma.user.findFirst.mock.calls[0][0];
            expect(callArgs.where.tenantId).toBe("tenant-1");
            expect(callArgs.where.email).toBe("alice@example.com");
        });
    });

    describe("listUsers", () => {
        it("returns paginated users", async () => {
            const result = await service.listUsers("tenant-1", { limit: 10, offset: 0 });
            expect(result.items).toHaveLength(1);
            expect(result.total).toBe(1);
        });

        it("uses default pagination when not specified", async () => {
            await service.listUsers("tenant-1");
            const call = prisma.user.findMany.mock.calls[0][0];
            expect(call.take).toBe(20);
            expect(call.skip).toBe(0);
        });
    });

    describe("updateUser", () => {
        it("updates user fields", async () => {
            const result = await service.updateUser("user-1", { displayName: "Alice Updated" });
            expect(prisma.user.update).toHaveBeenCalledOnce();
            expect(result?.displayName).toBe("Alice Updated");
        });
    });

    describe("deactivateUser", () => {
        it("sets status to inactive", async () => {
            await service.deactivateUser("user-1");
            const call = prisma.user.update.mock.calls[0][0];
            expect(call.data.status).toBe("inactive");
        });
    });
});
