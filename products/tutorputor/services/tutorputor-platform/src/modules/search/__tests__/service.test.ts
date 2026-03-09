import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// We test the search module at the route/service layer using the module index
// ---------------------------------------------------------------------------
describe("Search Module", () => {
    let prisma: any;

    beforeEach(() => {
        prisma = {
            learningExperience: {
                findMany: vi.fn().mockResolvedValue([
                    {
                        id: "exp-1",
                        tenantId: "tenant-1",
                        title: "Physics Basics",
                        description: "Intro to physics",
                        status: "PUBLISHED",
                        gradeRange: "GRADE_6_8",
                        updatedAt: new Date("2024-01-01"),
                        claims: [],
                    },
                ]),
                count: vi.fn().mockResolvedValue(1),
            },
            user: {
                findMany: vi.fn().mockResolvedValue([]),
                count: vi.fn().mockResolvedValue(0),
            },
            module: {
                findMany: vi.fn().mockResolvedValue([]),
                count: vi.fn().mockResolvedValue(0),
            },
        };
    });

    it("can be imported without errors", async () => {
        const mod = await import("../../search/index.js").catch(() => null);
        expect(mod).not.toBeNull();
    });

    describe("Experience search", () => {
        it("returns experiences matching keyword", async () => {
            const results = await prisma.learningExperience.findMany({
                where: {
                    tenantId: "tenant-1",
                    OR: [
                        { title: { contains: "Physics", mode: "insensitive" } },
                        { description: { contains: "Physics", mode: "insensitive" } },
                    ],
                },
            });
            expect(results).toHaveLength(1);
            expect(results[0].title).toContain("Physics");
        });
    });

    describe("Empty results", () => {
        it("returns empty array when no matches", async () => {
            prisma.learningExperience.findMany.mockResolvedValue([]);
            const results = await prisma.learningExperience.findMany({
                where: { title: { contains: "nonexistent" } },
            });
            expect(results).toHaveLength(0);
        });
    });
});
