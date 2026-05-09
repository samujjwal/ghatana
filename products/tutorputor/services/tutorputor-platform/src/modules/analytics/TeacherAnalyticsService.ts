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

export interface InstructorEvidenceDashboardTiles {
  calibrationGain: number;
  brierScore: number;
  masteryByClaim: Array<{
    claimId: string;
    mastery: number;
    evidenceCount: number;
  }>;
  processScoreDistribution: {
    low: number;
    medium: number;
    high: number;
  };
  vivaQueue: {
    total: number;
    highPriority: number;
  };
  atRiskLearners: Array<{
    userId: string;
    riskScore: number;
    reasons: string[];
  }>;
  remediationCompletion: {
    assigned: number;
    completed: number;
    completionRate: number;
  };
  misconceptionClusters: Array<{
    clusterId: string;
    description: string;
    affectedClaims: string[];
    affectedLearners: number;
    severity: 'low' | 'medium' | 'high';
  }>;
  cbmCalibrationMetrics: {
    reliabilityScore: number;
    discriminationIndex: number;
    confidenceAccuracy: {
      low: number;
      medium: number;
      high: number;
    };
    overconfidenceRate: number;
    underconfidenceRate: number;
  };
}

type EnrollmentAnalyticsRecord = {
  userId: string;
  status?: string | null;
  progressPercent?: number | null;
  user?: {
    displayName?: string | null;
  };
};

type AssessmentAttemptAnalyticsRecord = {
  userId: string;
  scorePercent?: number | null;
};

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

    const [classroom, classroomMembers] = await Promise.all([
      this.prisma.classroom.findFirst({
        where: { id: classroomId, tenantId },
        select: { id: true, title: true },
      }),
      this.prisma.classroomMember.findMany({
        where: { classroomId },
        select: { userId: true },
      }),
    ]);

    if (!classroom) {
      throw new Error('Classroom not found');
    }

    const memberIds = classroomMembers.map((member) => member.userId);

    const [enrollments, users, attempts] = memberIds.length === 0
      ? [[], [], []]
      : await Promise.all([
          this.prisma.enrollment.findMany({
            where: { tenantId, userId: { in: memberIds } },
          }),
          this.prisma.user.findMany({
            where: { tenantId, id: { in: memberIds } },
            select: { id: true, displayName: true },
          }),
          this.prisma.assessmentAttempt.findMany({
            where: { tenantId, userId: { in: memberIds } },
          }),
        ]);

    const usersById = new Map(
      users.map((user) => [user.id, user.displayName ?? ''])
    );
    const enrollmentsWithUser = enrollments.map((enrollment) => ({
      ...enrollment,
      user: { displayName: usersById.get(enrollment.userId) ?? '' },
    }));

    const totalStudents = memberIds.length;
    const activeStudents = enrollmentsWithUser.filter((e) => e.status === 'IN_PROGRESS').length;
    const averageProgress = enrollmentsWithUser.reduce((sum, e) => sum + (e.progressPercent || 0), 0) / totalStudents || 0;
    const averageScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length || 0;
    const completionRate = enrollmentsWithUser.filter((e) => e.status === 'COMPLETED').length / totalStudents || 0;

    const atRiskStudents = this.calculateAtRiskCount(enrollmentsWithUser, attempts);
    const topPerformers = this.getTopPerformers(enrollmentsWithUser, attempts);
    const strugglingStudents = this.getStrugglingStudents(enrollmentsWithUser, attempts);
    const moduleProgress = await this.calculateModuleProgress(tenantId, classroomId);
    const assignmentPerformance = await this.calculateAssignmentPerformance(tenantId, classroomId);

    return {
      classroomId: classroom.id,
      classroomName: classroom.name,
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

  async getInstructorEvidenceDashboardTiles(
    tenantId: string,
    classroomId: string,
  ): Promise<InstructorEvidenceDashboardTiles> {
    const classroom = await this.prisma.classroom.findFirst({
      where: { id: classroomId, tenantId },
      select: { id: true },
    });

    if (!classroom) {
      throw new Error('Classroom not found');
    }

    const members = await this.prisma.classroomMember.findMany({
      where: { classroomId },
      select: { userId: true },
    });
    const memberIds = members.map((member) => member.userId);

    if (memberIds.length === 0) {
      return {
        calibrationGain: 0,
        brierScore: 0,
        masteryByClaim: [],
        processScoreDistribution: { low: 0, medium: 0, high: 0 },
        vivaQueue: { total: 0, highPriority: 0 },
        atRiskLearners: [],
        remediationCompletion: { assigned: 0, completed: 0, completionRate: 0 },
        misconceptionClusters: [],
        cbmCalibrationMetrics: {
          reliabilityScore: 0,
          discriminationIndex: 0,
          confidenceAccuracy: { low: 0, medium: 0, high: 0 },
          overconfidenceRate: 0,
          underconfidenceRate: 0,
        },
      } as InstructorEvidenceDashboardTiles;
    }

    const [attempts, events] = await Promise.all([
      this.prisma.assessmentAttempt.findMany({
        where: { tenantId, userId: { in: memberIds } },
        select: {
          userId: true,
          scorePercent: true,
          responses: true,
          feedback: true,
        },
      }),
      this.prisma.learningEvent.findMany({
        where: { tenantId, userId: { in: memberIds } },
        select: {
          userId: true,
          eventType: true,
          payload: true,
        },
      }),
    ]);

    const answerEvents = events.filter((event) => event.eventType === "assess.answer");
    const simCaptureEvents = events.filter((event) => event.eventType === "sim.capture");
    const vivaEvents = events.filter((event) => event.eventType === "viva.scheduled");
    const remediationAssigned = events.filter(
      (event) => event.eventType === "remediation.assigned",
    ).length;
    const remediationCompleted = events.filter(
      (event) => event.eventType === "remediation.completed",
    ).length;

    const brierScore = this.calculateBrierScore(answerEvents);
    const baselineBrier = this.averageNumericFeedback(attempts, "baselineBrier", 0.25);
    const calibrationGain = Math.max(0, baselineBrier - brierScore);

    return {
      calibrationGain,
      brierScore,
      masteryByClaim: this.calculateMasteryByClaim(answerEvents),
      processScoreDistribution: this.calculateProcessScoreDistribution(simCaptureEvents),
      vivaQueue: {
        total: vivaEvents.length,
        highPriority: vivaEvents.filter((event) =>
          JSON.stringify(event.payload).includes('"priority":"high"'),
        ).length,
      },
      atRiskLearners: this.calculateEvidenceBackedRisk(memberIds, attempts, events),
      remediationCompletion: {
        assigned: remediationAssigned,
        completed: remediationCompleted,
        completionRate:
          remediationAssigned > 0 ? remediationCompleted / remediationAssigned : 0,
      },
      misconceptionClusters: this.detectMisconceptionClusters(answerEvents, memberIds),
      cbmCalibrationMetrics: this.calculateCBMCalibrationMetrics(answerEvents),
    };
  }

  private calculateAtRiskCount(
    _enrollments: EnrollmentAnalyticsRecord[],
    attempts: AssessmentAttemptAnalyticsRecord[],
  ): number {
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

  private calculateBrierScore(events: Array<{ payload: unknown }>): number {
    if (events.length === 0) {
      return 0;
    }
    const confidenceToProbability = {
      low: 0.33,
      medium: 0.66,
      high: 0.9,
    } as const;
    const total = events.reduce((sum, event) => {
      const payload = event.payload as {
        result?: { confidence?: "low" | "medium" | "high"; correct?: boolean };
      };
      const confidence = payload.result?.confidence ?? "medium";
      const probability = confidenceToProbability[confidence];
      const outcome = payload.result?.correct ? 1 : 0;
      return sum + (probability - outcome) ** 2;
    }, 0);
    return Number((total / events.length).toFixed(4));
  }

  private averageNumericFeedback(
    attempts: Array<{ feedback: unknown }>,
    key: string,
    fallback: number,
  ): number {
    const values = attempts
      .map((attempt) => (attempt.feedback as Record<string, unknown> | null)?.[key])
      .filter((value): value is number => typeof value === "number");
    if (values.length === 0) {
      return fallback;
    }
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  }

  private calculateMasteryByClaim(
    events: Array<{ payload: unknown }>,
  ): InstructorEvidenceDashboardTiles["masteryByClaim"] {
    const byClaim = new Map<string, { score: number; count: number }>();
    for (const event of events) {
      const payload = event.payload as {
        object?: { claimId?: string };
        result?: { score?: number; maxScore?: number; correct?: boolean };
      };
      const claimId = payload.object?.claimId;
      if (!claimId) {
        continue;
      }
      const maxScore = payload.result?.maxScore ?? 1;
      const score =
        typeof payload.result?.score === "number"
          ? payload.result.score / maxScore
          : payload.result?.correct
            ? 1
            : 0;
      const current = byClaim.get(claimId) ?? { score: 0, count: 0 };
      current.score += score;
      current.count += 1;
      byClaim.set(claimId, current);
    }
    return Array.from(byClaim.entries()).map(([claimId, value]) => ({
      claimId,
      mastery: Number((value.score / value.count).toFixed(4)),
      evidenceCount: value.count,
    }));
  }

  private calculateProcessScoreDistribution(
    events: Array<{ payload: unknown }>,
  ): InstructorEvidenceDashboardTiles["processScoreDistribution"] {
    const distribution = { low: 0, medium: 0, high: 0 };
    for (const event of events) {
      const payload = event.payload as {
        result?: { processFeatures?: Record<string, unknown> };
      };
      const raw = payload.result?.processFeatures?.processScore;
      const score = typeof raw === "number" ? raw : 0;
      if (score < 0.4) {
        distribution.low += 1;
      } else if (score < 0.75) {
        distribution.medium += 1;
      } else {
        distribution.high += 1;
      }
    }
    return distribution;
  }

  private calculateEvidenceBackedRisk(
    memberIds: string[],
    attempts: Array<{ userId: string; scorePercent: number | null }>,
    events: Array<{ userId: string; eventType: string; payload: unknown }>,
  ): InstructorEvidenceDashboardTiles["atRiskLearners"] {
    return memberIds
      .map((userId) => {
        const learnerAttempts = attempts.filter((attempt) => attempt.userId === userId);
        const averageScore =
          learnerAttempts.length > 0
            ? learnerAttempts.reduce(
                (sum, attempt) => sum + (attempt.scorePercent ?? 0),
                0,
              ) / learnerAttempts.length
            : 100;
        const learnerEvents = events.filter((event) => event.userId === userId);
        const hintCount = learnerEvents.filter(
          (event) => event.eventType === "assist.hint",
        ).length;
        const failedEvidence = learnerEvents.filter((event) =>
          JSON.stringify(event.payload).includes('"correct":false'),
        ).length;
        const reasons: string[] = [];
        if (averageScore < 60) reasons.push("low assessment score");
        if (hintCount >= 2) reasons.push("repeated hint usage");
        if (failedEvidence >= 2) reasons.push("repeated invalid evidence");
        return {
          userId,
          riskScore: Math.min(1, (100 - averageScore) / 100 + hintCount * 0.1 + failedEvidence * 0.15),
          reasons,
        };
      })
      .filter((risk) => risk.reasons.length > 0)
      .sort((a, b) => b.riskScore - a.riskScore);
  }

  private getTopPerformers(
    enrollments: EnrollmentAnalyticsRecord[],
    attempts: AssessmentAttemptAnalyticsRecord[],
  ): Array<{ userId: string; displayName: string; averageScore: number }> {
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

  private getStrugglingStudents(
    enrollments: EnrollmentAnalyticsRecord[],
    attempts: AssessmentAttemptAnalyticsRecord[],
  ): Array<{ userId: string; displayName: string; averageScore: number; riskFactors: string[] }> {
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
    });

    // Get module IDs and fetch module names
    const moduleIds = [...new Set(enrollments.map((e) => e.moduleId))];
    const modules = await this.prisma.module.findMany({
      where: { id: { in: moduleIds } },
      select: { id: true, title: true },
    });

    const moduleNamesById = new Map(modules.map((m) => [m.id, m.title || '']));

    const moduleProgress = new Map<string, { moduleName: string; progresses: number[]; completions: number }>();
    enrollments.forEach((e) => {
      const moduleId = e.moduleId;
      const data = moduleProgress.get(moduleId) || { moduleName: moduleNamesById.get(moduleId) || '', progresses: [], completions: 0 };
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
    });

    // Get assessment IDs and fetch assessment names
    const assessmentIds = [...new Set(attempts.map((a) => a.assessmentId))];
    const assessments = await this.prisma.assessment.findMany({
      where: { id: { in: assessmentIds } },
      select: { id: true, title: true },
    });

    const assessmentNamesById = new Map(assessments.map((a) => [a.id, a.title || '']));

    const assignmentData = new Map<string, { assignmentName: string; scores: number[]; totalStudents: number }>();
    const studentCount = new Set(attempts.map((a) => a.userId)).size;

    attempts.forEach((a) => {
      const assignmentId = a.assessmentId;
      const data = assignmentData.get(assignmentId) || { assignmentName: assessmentNamesById.get(assignmentId) || '', scores: [], totalStudents: 0 };
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

  private calculateStudentRisk(
    enrollments: EnrollmentAnalyticsRecord[],
    attempts: AssessmentAttemptAnalyticsRecord[],
  ): { riskLevel: 'low' | 'medium' | 'high' | 'critical'; riskFactors: string[]; recommendations: string[] } {
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

  /**
   * Detect misconception clusters from assessment answer events.
   * Groups similar incorrect answers to identify common misconceptions.
   */
  private detectMisconceptionClusters(
    answerEvents: Array<{ userId: string; payload: unknown }>,
    memberIds: string[],
  ): InstructorEvidenceDashboardTiles["misconceptionClusters"] {
    const incorrectAnswers = answerEvents.filter((event) => {
      const payload = event.payload as { result?: { correct?: boolean } };
      return !payload.result?.correct;
    });

    // Group by claim and answer pattern
    const byClaim = new Map<string, Map<string, Set<string>>>();
    for (const event of incorrectAnswers) {
      const payload = event.payload as {
        object?: { claimId?: string };
        result?: { answer?: string };
      };
      const claimId = payload.object?.claimId || 'unknown';
      const answer = payload.result?.answer || 'unknown';

      if (!byClaim.has(claimId)) {
        byClaim.set(claimId, new Map());
      }
      const claimMap = byClaim.get(claimId)!;

      if (!claimMap.has(answer)) {
        claimMap.set(answer, new Set());
      }
      claimMap.get(answer)!.add(event.userId);
    }

    // Identify clusters (claims with multiple learners making similar mistakes)
    const clusters: InstructorEvidenceDashboardTiles["misconceptionClusters"] = [];
    let clusterId = 0;

    for (const [claimId, answerMap] of byClaim.entries()) {
      const affectedLearners = new Set<string>();
      const answers: string[] = [];

      for (const [answer, learners] of answerMap.entries()) {
        if (learners.size >= 2) {
          // Only consider if at least 2 learners made the same mistake
          answers.push(answer);
          learners.forEach((learnerId) => affectedLearners.add(learnerId));
        }
      }

      if (affectedLearners.size >= 3) {
        // Only create cluster if at least 3 learners are affected
        const severity: 'low' | 'medium' | 'high' =
          affectedLearners.size >= 10 ? 'high' : affectedLearners.size >= 5 ? 'medium' : 'low';

        clusters.push({
          clusterId: `cluster-${clusterId++}`,
          description: `Common incorrect answer pattern on claim ${claimId}: ${answers.slice(0, 3).join(', ')}`,
          affectedClaims: [claimId],
          affectedLearners: affectedLearners.size,
          severity,
        });
      }
    }

    return clusters.sort((a: InstructorEvidenceDashboardTiles["misconceptionClusters"][0], b: InstructorEvidenceDashboardTiles["misconceptionClusters"][0]) => b.affectedLearners - a.affectedLearners).slice(0, 10);
  }

  /**
   * Calculate CBM (Confidence-Based Marking) calibration metrics.
   * Measures how well learners' confidence levels match their actual performance.
   */
  private calculateCBMCalibrationMetrics(
    answerEvents: Array<{ payload: unknown }>,
  ): InstructorEvidenceDashboardTiles["cbmCalibrationMetrics"] {
    if (answerEvents.length === 0) {
      return {
        reliabilityScore: 0,
        discriminationIndex: 0,
        confidenceAccuracy: { low: 0, medium: 0, high: 0 },
        overconfidenceRate: 0,
        underconfidenceRate: 0,
      };
    }

    const confidenceToProbability = {
      low: 0.33,
      medium: 0.66,
      high: 0.9,
    } as const;

    let correctByConfidence = { low: 0, medium: 0, high: 0 };
    let totalByConfidence = { low: 0, medium: 0, high: 0 };
    let overconfidentCount = 0;
    let underconfidentCount = 0;

    for (const event of answerEvents) {
      const payload = event.payload as {
        result?: { confidence?: 'low' | 'medium' | 'high'; correct?: boolean };
      };
      const confidence = payload.result?.confidence ?? 'medium';
      const isCorrect = payload.result?.correct ?? false;

      totalByConfidence[confidence]++;
      if (isCorrect) {
        correctByConfidence[confidence]++;
      }

      // Detect overconfidence (high confidence but incorrect)
      if (confidence === 'high' && !isCorrect) {
        overconfidentCount++;
      }

      // Detect underconfidence (low confidence but correct)
      if (confidence === 'low' && isCorrect) {
        underconfidentCount++;
      }
    }

    // Calculate accuracy by confidence level
    const confidenceAccuracy = {
      low: totalByConfidence.low > 0 ? correctByConfidence.low / totalByConfidence.low : 0,
      medium: totalByConfidence.medium > 0 ? correctByConfidence.medium / totalByConfidence.medium : 0,
      high: totalByConfidence.high > 0 ? correctByConfidence.high / totalByConfidence.high : 0,
    };

    // Calculate reliability score (how well confidence predicts correctness)
    const reliabilityScore = this.calculateReliabilityScore(confidenceAccuracy);

    // Calculate discrimination index (separation between confidence levels)
    const discriminationIndex = this.calculateDiscriminationIndex(confidenceAccuracy);

    return {
      reliabilityScore,
      discriminationIndex,
      confidenceAccuracy,
      overconfidenceRate: overconfidentCount / answerEvents.length,
      underconfidenceRate: underconfidentCount / answerEvents.length,
    };
  }

  /**
   * Calculate reliability score based on confidence accuracy.
   * Measures monotonic relationship between confidence and accuracy.
   */
  private calculateReliabilityScore(accuracy: { low: number; medium: number; high: number }): number {
    // Ideal: low < medium < high
    const monotonicIncrease = accuracy.low < accuracy.medium && accuracy.medium < accuracy.high;
    if (!monotonicIncrease) {
      return 0;
    }

    // Score based on how much accuracy increases with confidence
    const range = accuracy.high - accuracy.low;
    const idealRange = 1.0; // Perfect calibration would have 0.33 to 1.0 range
    return Math.min(range / idealRange, 1.0);
  }

  /**
   * Calculate discrimination index between confidence levels.
   * Measures how well confidence levels separate correct from incorrect answers.
   */
  private calculateDiscriminationIndex(accuracy: { low: number; medium: number; high: number }): number {
    // High confidence should have high accuracy, low confidence should have low accuracy
    const separation = accuracy.high - accuracy.low;
    return Math.min(separation, 1.0);
  }
}
