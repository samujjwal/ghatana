/**
 * Teacher Analytics Service
 *
 * Teacher-specific analytics with classroom and student insights.
 *
 * @doc.type class
 * @doc.purpose Provide analytics for teachers to monitor classroom performance
 * @doc.layer product
 * @doc.pattern Analytics Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type { TutorPrismaClient } from '@tutorputor/core/db';

const logger = createStandaloneLogger({ component: 'TeacherAnalyticsService' });

export interface ClassroomAnalytics {
  classroomId: string;
  classroomName: string;
  totalStudents: number;
  activeStudents: number;
  averageProgress: number;
  averageScore: number;
  completionRate: number;
  atRiskStudents: number;
  topPerformers: Array<{ userId: string; displayName: string; averageScore: number }>;
  strugglingStudents: Array<{ userId: string; displayName: string; averageScore: number; riskFactors: string[] }>;
  moduleProgress: Array<{ moduleId: string; moduleName: string; averageProgress: number; completionRate: number }>;
  assignmentPerformance: Array<{ assignmentId: string; assignmentName: string; averageScore: number; submissionRate: number }>;
}

export interface StudentAnalytics {
  userId: string;
  displayName: string;
  email: string;
  totalEnrollments: number;
  completedModules: number;
  averageScore: number;
  totalTimeMinutes: number;
  lastActivity: string;
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  riskFactors: string[];
  recommendations: string[];
}

export class TeacherAnalyticsService {
  constructor(private readonly prisma: TutorPrismaClient) {}

  /**
   * Get classroom analytics
   */
  async getClassroomAnalytics(tenantId: string, classroomId: string): Promise<ClassroomAnalytics> {
    logger.info({
      message: 'Fetching classroom analytics',
      tenantId,
      classroomId,
    });

    const [classroom, enrollments, attempts] = await Promise.all([
      this.prisma.classroom.findUnique({
        where: { id: classroomId },
        select: { id: true, title: true },
      }),
      this.prisma.enrollment.findMany({
        where: { tenantId, classroom: { id: classroomId } },
        include: { user: { select: { id: true, displayName: true } } },
      }),
      this.prisma.assessmentAttempt.findMany({
        where: {
          tenantId,
          user: { enrollments: { some: { classroom: { id: classroomId } } } },
        },
      }),
    ]);

    if (!classroom) {
      throw new Error('Classroom not found');
    }

    const totalStudents = enrollments.length;
    const activeStudents = enrollments.filter((e) => e.status === 'IN_PROGRESS').length;
    const averageProgress = enrollments.reduce((sum, e) => sum + (e.progressPercent || 0), 0) / totalStudents || 0;
    const averageScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length || 0;
    const completionRate = enrollments.filter((e) => e.status === 'COMPLETED').length / totalStudents || 0;

    const atRiskStudents = this.calculateAtRiskCount(enrollments, attempts);
    const topPerformers = this.getTopPerformers(enrollments, attempts);
    const strugglingStudents = this.getStrugglingStudents(enrollments, attempts);
    const moduleProgress = await this.calculateModuleProgress(tenantId, classroomId);
    const assignmentPerformance = await this.calculateAssignmentPerformance(tenantId, classroomId);

    return {
      classroomId: classroom.id,
      classroomName: classroom.title,
      totalStudents,
      activeStudents,
      averageProgress,
      averageScore,
      completionRate,
      atRiskStudents,
      topPerformers,
      strugglingStudents,
      moduleProgress,
      assignmentPerformance,
    };
  }

  /**
   * Get student analytics
   */
  async getStudentAnalytics(tenantId: string, studentId: string): Promise<StudentAnalytics> {
    logger.info({
      message: 'Fetching student analytics',
      tenantId,
      studentId,
    });

    const [user, enrollments, attempts, recentEvents] = await Promise.all([
      this.prisma.user.findUnique({
        where: { id: studentId },
        select: { id: true, displayName: true, email: true },
      }),
      this.prisma.enrollment.findMany({
        where: { tenantId, userId: studentId },
      }),
      this.prisma.assessmentAttempt.findMany({
        where: { tenantId, userId: studentId },
      }),
      this.prisma.learningEvent.findMany({
        where: { tenantId, userId: studentId },
        orderBy: { timestamp: 'desc' },
        take: 1,
      }),
    ]);

    if (!user) {
      throw new Error('Student not found');
    }

    const totalEnrollments = enrollments.length;
    const completedModules = enrollments.filter((e) => e.status === 'COMPLETED').length;
    const averageScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length || 0;
    const totalTimeMinutes = enrollments.reduce((sum, e) => sum + (e.timeSpentSeconds || 0), 0) / 60;
    const lastActivity = recentEvents[0]?.timestamp?.toISOString() || null;

    const { riskLevel, riskFactors, recommendations } = this.calculateStudentRisk(enrollments, attempts);

    return {
      userId: user.id,
      displayName: user.displayName || '',
      email: user.email || '',
      totalEnrollments,
      completedModules,
      averageScore,
      totalTimeMinutes,
      lastActivity: lastActivity || '',
      riskLevel,
      riskFactors,
      recommendations,
    };
  }

  /**
   * Get intervention recommendations
   */
  async getInterventionRecommendations(tenantId: string, classroomId: string): Promise<Array<{
    type: 'email' | 'notification' | 'assignment' | 'meeting';
    priority: 'low' | 'medium' | 'high';
    description: string;
    targetStudents: string[];
  }>> {
    const classroomAnalytics = await this.getClassroomAnalytics(tenantId, classroomId);
    const recommendations: Array<{
      type: 'email' | 'notification' | 'assignment' | 'meeting';
      priority: 'low' | 'medium' | 'high';
      description: string;
      targetStudents: string[];
    }> = [];

    if (classroomAnalytics.atRiskStudents > 0) {
      recommendations.push({
        type: 'email',
        priority: 'high',
        description: 'Send re-engagement email to at-risk students',
        targetStudents: classroomAnalytics.strugglingStudents.map((s) => s.userId),
      });
    }

    if (classroomAnalytics.averageScore < 70) {
      recommendations.push({
        type: 'assignment',
        priority: 'medium',
        description: 'Assign additional practice materials',
        targetStudents: [],
      });
    }

    if (classroomAnalytics.completionRate < 0.5) {
      recommendations.push({
        type: 'notification',
        priority: 'medium',
        description: 'Send progress reminders to inactive students',
        targetStudents: classroomAnalytics.strugglingStudents.map((s) => s.userId),
      });
    }

    return recommendations;
  }

  private calculateAtRiskCount(enrollments: any[], attempts: any[]): number {
    const studentAttempts = new Map<string, number[]>();
    attempts.forEach((a) => {
      const scores = studentAttempts.get(a.userId) || [];
      scores.push(a.scorePercent || 0);
      studentAttempts.set(a.userId, scores);
    });

    let atRiskCount = 0;
    studentAttempts.forEach((scores) => {
      const avgScore = scores.reduce((sum, s) => sum + s, 0) / scores.length;
      if (avgScore < 60) atRiskCount++;
    });

    return atRiskCount;
  }

  private getTopPerformers(enrollments: any[], attempts: any[]): Array<{ userId: string; displayName: string; averageScore: number }> {
    const studentScores = new Map<string, { displayName: string; scores: number[] }>();
    enrollments.forEach((e) => {
      studentScores.set(e.userId, { displayName: e.user?.displayName || '', scores: [] });
    });
    attempts.forEach((a) => {
      const data = studentScores.get(a.userId);
      if (data) data.scores.push(a.scorePercent || 0);
    });

    return Array.from(studentScores.entries())
      .map(([userId, data]) => ({
        userId,
        displayName: data.displayName,
        averageScore: data.scores.length > 0 ? data.scores.reduce((sum, s) => sum + s, 0) / data.scores.length : 0,
      }))
      .sort((a, b) => b.averageScore - a.averageScore)
      .slice(0, 5);
  }

  private getStrugglingStudents(enrollments: any[], attempts: any[]): Array<{ userId: string; displayName: string; averageScore: number; riskFactors: string[] }> {
    const studentScores = new Map<string, { displayName: string; scores: number[]; progress: number }>();
    enrollments.forEach((e) => {
      studentScores.set(e.userId, { displayName: e.user?.displayName || '', scores: [], progress: e.progressPercent || 0 });
    });
    attempts.forEach((a) => {
      const data = studentScores.get(a.userId);
      if (data) data.scores.push(a.scorePercent || 0);
    });

    return Array.from(studentScores.entries())
      .map(([userId, data]) => {
        const avgScore = data.scores.length > 0 ? data.scores.reduce((sum, s) => sum + s, 0) / data.scores.length : 0;
        const riskFactors: string[] = [];
        if (avgScore < 60) riskFactors.push('Low assessment scores');
        if (data.progress < 30) riskFactors.push('Low progress');
        if (data.scores.length === 0) riskFactors.push('No assessment attempts');
        return { userId, displayName: data.displayName, averageScore: avgScore, riskFactors };
      })
      .filter((s) => s.riskFactors.length > 0)
      .sort((a, b) => a.averageScore - b.averageScore)
      .slice(0, 5);
  }

  private async calculateModuleProgress(tenantId: string, classroomId: string): Promise<Array<{ moduleId: string; moduleName: string; averageProgress: number; completionRate: number }>> {
    const enrollments = await this.prisma.enrollment.findMany({
      where: { tenantId, classroom: { id: classroomId } },
      include: { module: { select: { id: true, title: true } } },
    });

    const moduleProgress = new Map<string, { moduleName: string; progresses: number[]; completions: number }>();
    enrollments.forEach((e) => {
      const moduleId = e.moduleId;
      const data = moduleProgress.get(moduleId) || { moduleName: e.module?.title || '', progresses: [], completions: 0 };
      data.progresses.push(e.progressPercent || 0);
      if (e.status === 'COMPLETED') data.completions++;
      moduleProgress.set(moduleId, data);
    });

    return Array.from(moduleProgress.entries()).map(([moduleId, data]) => ({
      moduleId,
      moduleName: data.moduleName,
      averageProgress: data.progresses.reduce((sum, p) => sum + p, 0) / data.progresses.length,
      completionRate: data.completions / data.progresses.length,
    }));
  }

  private async calculateAssignmentPerformance(tenantId: string, classroomId: string): Promise<Array<{ assignmentId: string; assignmentName: string; averageScore: number; submissionRate: number }>> {
    const attempts = await this.prisma.assessmentAttempt.findMany({
      where: {
        tenantId,
        user: { enrollments: { some: { classroom: { id: classroomId } } } },
      },
      include: { assessment: { select: { id: true, title: true } } },
    });

    const assignmentData = new Map<string, { assignmentName: string; scores: number[]; totalStudents: number }>();
    const studentCount = new Set(attempts.map((a) => a.userId)).size;

    attempts.forEach((a) => {
      const assignmentId = a.assessmentId;
      const data = assignmentData.get(assignmentId) || { assignmentName: a.assessment?.title || '', scores: [], totalStudents: 0 };
      data.scores.push(a.scorePercent || 0);
      data.totalStudents = studentCount;
      assignmentData.set(assignmentId, data);
    });

    return Array.from(assignmentData.entries()).map(([assignmentId, data]) => ({
      assignmentId,
      assignmentName: data.assignmentName,
      averageScore: data.scores.reduce((sum, s) => sum + s, 0) / data.scores.length,
      submissionRate: data.scores.length / data.totalStudents,
    }));
  }

  private calculateStudentRisk(enrollments: any[], attempts: any[]): { riskLevel: 'low' | 'medium' | 'high' | 'critical'; riskFactors: string[]; recommendations: string[] } {
    const riskFactors: string[] = [];
    const recommendations: string[] = [];
    let riskScore = 0;

    const avgProgress = enrollments.reduce((sum, e) => sum + (e.progressPercent || 0), 0) / enrollments.length || 0;
    if (avgProgress < 30) {
      riskFactors.push('Low progress');
      riskScore += 20;
      recommendations.push('Offer additional support and resources');
    }

    const avgScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length || 0;
    if (avgScore < 60) {
      riskFactors.push('Low assessment scores');
      riskScore += 30;
      recommendations.push('Provide targeted practice materials');
    }

    const completedCount = enrollments.filter((e) => e.status === 'COMPLETED').length;
    if (completedCount === 0 && enrollments.length > 0) {
      riskFactors.push('No completions');
      riskScore += 15;
      recommendations.push('Encourage completion of current modules');
    }

    let riskLevel: 'low' | 'medium' | 'high' | 'critical' = 'low';
    if (riskScore >= 80) riskLevel = 'critical';
    else if (riskScore >= 60) riskLevel = 'high';
    else if (riskScore >= 30) riskLevel = 'medium';

    return { riskLevel, riskFactors, recommendations };
  }
}
