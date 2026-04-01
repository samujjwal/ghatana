/**
 * @doc.type module
 * @doc.purpose Teacher service for classroom management
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@tutorputor/core/db';
import type {
    TeacherService,
    Classroom,
    ClassroomId,
    ClassroomProgress,
    LearningEventInput,
    LearningEventType,
    ModuleId,
    RosterEntry,
    TeacherDashboardSummary,
    TenantId,
    UserId,
    UserSummary,
} from '@tutorputor/contracts';

type ClassroomWithRelations = {
    id: string;
    tenantId: string;
    teacherId: string;
    name: string;
    description: string | null;
    roster: Array<{
        userId: string;
        displayName: string;
        email: string | null;
        enrolledAt: Date;
    }>;
    assignments: Array<{
        moduleId: string;
    }>;
    createdAt: Date;
    updatedAt: Date;
};

export class TeacherServiceImpl implements TeacherService {
    constructor(private readonly prisma: PrismaClient) { }

    async getTeacherDashboard(args: {
        tenantId: TenantId;
        teacherId: UserId;
    }): Promise<TeacherDashboardSummary> {
        const { tenantId, teacherId } = args;

        // Fetch all classrooms for this teacher
        const classrooms = await this.prisma.classroom.findMany({
            where: { tenantId, teacherId },
            include: {
                roster: true,
                assignments: true,
            },
            orderBy: { updatedAt: 'desc' },
        });

        // Fetch recent learning events for students in teacher's classrooms
        const studentIds = classrooms.flatMap((c) => c.roster.map((s) => s.userId));
        const recentEvents =
            studentIds.length > 0
                ? await this.prisma.learningEvent.findMany({
                    where: {
                        tenantId,
                        userId: { in: studentIds },
                    },
                    orderBy: { timestamp: 'desc' },
                    take: 20,
                })
                : [];

        // Calculate at-risk students (those with low progress)
        const atRiskStudents: RosterEntry[] = [];
        for (const classroom of classrooms) {
            for (const student of classroom.roster) {
                // Check enrollments for this student
                const enrollments = await this.prisma.enrollment.findMany({
                    where: {
                        tenantId,
                        userId: student.userId,
                        moduleId: { in: classroom.assignments.map((a) => a.moduleId) },
                    },
                });

                const avgProgress =
                    enrollments.length > 0
                        ? enrollments.reduce((sum: number, e) => sum + e.progressPercent, 0) /
                        enrollments.length
                        : 0;

                if (avgProgress < 30) {
                    const entry: RosterEntry = {
                        userId: student.userId as UserId,
                        displayName: student.displayName,
                        enrolledAt: student.enrolledAt.toISOString(),
                        progressPercent: avgProgress,
                    };
                    if (student.email) {
                        entry.email = student.email;
                    }
                    atRiskStudents.push(entry);
                }
            }
        }

        // Calculate totals
        const totalStudents = new Set(
            classrooms.flatMap((c) => c.roster.map((s) => s.userId))
        ).size;

        const averageClassProgress =
            totalStudents > 0
                ? Math.round(100 - (atRiskStudents.length / totalStudents) * 100)
                : 0;

        return {
            teacher: this.buildTeacherSummary(teacherId),
            classrooms: classrooms.map((c) => this.mapToClassroom(c)),
            recentActivity: recentEvents.map((e) => this.mapToLearningEvent(e)),
            atRiskStudents,
            totalStudents,
            averageClassProgress,
        };
    }

    async createClassroom(args: {
        tenantId: TenantId;
        teacherId: UserId;
        name: string;
        description?: string;
    }): Promise<Classroom> {
        const classroom = await this.prisma.classroom.create({
            data: {
                tenantId: args.tenantId,
                teacherId: args.teacherId,
                name: args.name,
                ...(args.description ? { description: args.description } : {}),
            },
            include: {
                roster: true,
                assignments: true,
            },
        });

        return this.mapToClassroom(classroom as unknown as ClassroomWithRelations);
    }

    async getClassroom(args: {
        tenantId: TenantId;
        classroomId: ClassroomId;
    }): Promise<Classroom> {
        const classroom = await this.prisma.classroom.findFirst({
            where: { id: args.classroomId, tenantId: args.tenantId },
            include: {
                roster: true,
                assignments: true,
            },
        });

        if (!classroom) {
            throw new Error(`Classroom ${args.classroomId} not found`);
        }

        return this.mapToClassroom(classroom as unknown as ClassroomWithRelations);
    }

    async addStudentToClassroom(args: {
        tenantId: TenantId;
        classroomId: ClassroomId;
        studentId: UserId;
        displayName: string;
        email?: string;
    }): Promise<Classroom> {
        // Verify classroom exists
        const classroom = await this.prisma.classroom.findFirst({
            where: { id: args.classroomId, tenantId: args.tenantId },
        });

        if (!classroom) {
            throw new Error(`Classroom ${args.classroomId} not found`);
        }

        // Add student (upsert to handle duplicates)
        await this.prisma.classroomStudent.upsert({
            where: {
                classroomId_userId: {
                    classroomId: args.classroomId,
                    userId: args.studentId,
                },
            },
            update: {
                displayName: args.displayName,
                ...(args.email ? { email: args.email } : {}),
            },
            create: {
                classroomId: args.classroomId,
                userId: args.studentId,
                displayName: args.displayName,
                ...(args.email ? { email: args.email } : {}),
            },
        });

        // Return updated classroom
        const updated = await this.prisma.classroom.findFirst({
            where: { id: args.classroomId },
            include: {
                roster: true,
                assignments: true,
            },
        });

        return this.mapToClassroom(updated as unknown as ClassroomWithRelations);
    }

    async removeStudentFromClassroom(args: {
        tenantId: TenantId;
        classroomId: ClassroomId;
        studentId: UserId;
    }): Promise<Classroom> {
        // Verify classroom exists
        const classroom = await this.prisma.classroom.findFirst({
            where: { id: args.classroomId, tenantId: args.tenantId },
        });

        if (!classroom) {
            throw new Error(`Classroom ${args.classroomId} not found`);
        }

        // Remove student
        await this.prisma.classroomStudent.deleteMany({
            where: { classroomId: args.classroomId, userId: args.studentId },
        });

        // Return updated classroom
        const updated = await this.prisma.classroom.findFirst({
            where: { id: args.classroomId },
            include: {
                roster: true,
                assignments: true,
            },
        });

        return this.mapToClassroom(updated as unknown as ClassroomWithRelations);
    }

    async assignModule(args: {
        tenantId: TenantId;
        classroomId: ClassroomId;
        moduleId: ModuleId;
        dueAt?: string;
    }): Promise<Classroom> {
        const { tenantId, classroomId, moduleId, dueAt } = args;

        // Verify classroom exists
        const classroom = await this.prisma.classroom.findFirst({
            where: { id: classroomId, tenantId },
        });

        if (!classroom) {
            throw new Error(`Classroom ${classroomId} not found`);
        }

        // Assign module (upsert)
        await this.prisma.classroomAssignment.upsert({
            where: {
                classroomId_moduleId: { classroomId, moduleId },
            },
            update: { dueAt: dueAt ? new Date(dueAt) : null },
            create: {
                classroomId,
                moduleId,
                dueAt: dueAt ? new Date(dueAt) : null,
            },
        });

        // Return updated classroom
        const updated = await this.prisma.classroom.findFirst({
            where: { id: classroomId },
            include: {
                roster: true,
                assignments: true,
            },
        });

        return this.mapToClassroom(updated as unknown as ClassroomWithRelations);
    }

    async getClassroomProgress(args: {
        tenantId: TenantId;
        classroomId: ClassroomId;
    }): Promise<ClassroomProgress[]> {
        const { tenantId, classroomId } = args;

        const classroom = await this.prisma.classroom.findFirst({
            where: { id: classroomId, tenantId },
            include: {
                roster: true,
                assignments: true,
            },
        });

        if (!classroom) {
            throw new Error(`Classroom ${classroomId} not found`);
        }

        const progress: ClassroomProgress[] = [];
        const studentIds = classroom.roster.map((s) => s.userId);

        for (const assignment of classroom.assignments) {
            // Get module details
            const module = await this.prisma.module.findFirst({
                where: { id: assignment.moduleId },
            });

            if (!module) continue;

            // Get enrollments for this module
            const enrollments = await this.prisma.enrollment.findMany({
                where: {
                    tenantId,
                    moduleId: assignment.moduleId,
                    userId: { in: studentIds },
                },
            });

            const avgProgress =
                enrollments.length > 0
                    ? enrollments.reduce((sum: number, e) => sum + e.progressPercent, 0) /
                    enrollments.length
                    : 0;

            const completed = enrollments.filter(
                (e) => e.status === 'COMPLETED'
            ).length;
            const completionRate =
                studentIds.length > 0 ? (completed / studentIds.length) * 100 : 0;

            // Find struggling students (progress < 30%)
            const struggling = enrollments
                .filter((e) => e.progressPercent < 30)
                .map((e) => {
                    const student = classroom.roster.find((r) => r.userId === e.userId);
                    if (!student) return null;
                    const entry: RosterEntry = {
                        userId: student.userId as UserId,
                        displayName: student.displayName,
                        enrolledAt: student.enrolledAt.toISOString(),
                        progressPercent: e.progressPercent,
                    };
                    if (student.email) {
                        entry.email = student.email;
                    }
                    return entry;
                })
                .filter((s: RosterEntry | null): s is RosterEntry => Boolean(s));

            progress.push({
                classroomId: classroomId as ClassroomId,
                moduleId: assignment.moduleId as ModuleId,
                moduleTitle: module.title,
                averageProgress: Math.round(avgProgress),
                completionRate: Math.round(completionRate),
                strugglingStudents: struggling,
            });
        }

        return progress;
    }

    async checkHealth() {
        await this.prisma.$queryRaw`SELECT 1`;
        return true;
    }

    // ===========================================================================
    // Helpers
    // ===========================================================================

    private buildTeacherSummary(teacherId: string): UserSummary {
        return {
            id: teacherId as UserId,
            email: `teacher-${teacherId}@tutorputor.com`,
            displayName: `Teacher ${teacherId.slice(0, 8)}`,
            role: 'teacher',
        };
    }

    private mapToClassroom(classroom: ClassroomWithRelations): Classroom {
        const mapped: Classroom = {
            id: classroom.id as ClassroomId,
            tenantId: classroom.tenantId as TenantId,
            teacherId: classroom.teacherId as UserId,
            name: classroom.name,
            roster: classroom.roster.map(
                (s): RosterEntry => {
                    const rosterEntry: RosterEntry = {
                        userId: s.userId as UserId,
                        displayName: s.displayName,
                        enrolledAt: s.enrolledAt.toISOString(),
                        progressPercent: 0, // Will be calculated on demand
                    };
                    if (s.email) {
                        rosterEntry.email = s.email;
                    }
                    return rosterEntry;
                }
            ),
            assignedModules: classroom.assignments.map(
                (a) => a.moduleId as ModuleId
            ),
            createdAt: classroom.createdAt.toISOString(),
            updatedAt: classroom.updatedAt.toISOString(),
        };

        if (classroom.description) {
            mapped.description = classroom.description;
        }

        return mapped;
    }

    private mapToLearningEvent(event: { eventType: string; userId: string; timestamp: Date; moduleId?: string | null; payload?: unknown }): LearningEventInput {
        const mapped: LearningEventInput = {
            type: event.eventType as LearningEventType,
            userId: event.userId as UserId,
            timestamp: event.timestamp.toISOString(),
        };
        if (event.moduleId) {
            mapped.moduleId = event.moduleId as ModuleId;
        }
        if (event.payload && typeof event.payload === 'object') {
            mapped.payload = event.payload as Record<string, unknown>;
        }
        return mapped;
    }
}
