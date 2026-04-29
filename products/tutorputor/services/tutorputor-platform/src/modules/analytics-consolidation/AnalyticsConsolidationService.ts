/**
 * Analytics Consolidation Service
 *
 * Merge /analytics, /teacher, and dashboard insights into persona-appropriate single surfaces.
 *
 * @doc.type service
 * @doc.purpose Consolidate analytics entry points for different personas
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface PersonaAnalytics {
  persona: "student" | "teacher" | "admin" | "parent";
  metrics: {
    engagement: {
      timeSpent: number;
      sessionsCompleted: number;
      streakDays: number;
    };
    learning: {
      conceptsMastered: number;
      avgAccuracy: number;
      progressPercent: number;
    };
    content: {
      questionsAnswered: number;
      aiInteractions: number;
      simulationsCompleted: number;
    };
  };
  insights: string[];
  recommendations: string[];
}

interface MasteryData {
  attempts: number;
  correctAttempts: number;
  masteryProbability: number;
}

interface ModuleData {
  id: string;
  questionCount?: number;
}

interface LearnerProfileData {
  avgSessionMinutes: number;
  streakDays: number;
  masteries: MasteryData[];
}

export class AnalyticsConsolidationService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Get consolidated analytics for a specific persona
   */
  async getPersonaAnalytics(
    tenantId: string,
    userId: string,
    persona: PersonaAnalytics["persona"],
  ): Promise<PersonaAnalytics> {
    switch (persona) {
      case "student":
        return this.getStudentAnalytics(tenantId, userId);
      case "teacher":
        return this.getTeacherAnalytics(tenantId, userId);
      case "admin":
        return this.getAdminAnalytics(tenantId, userId);
      case "parent":
        return this.getParentAnalytics(tenantId, userId);
      default:
        throw new Error(`Unknown persona: ${persona}`);
    }
  }

  /**
   * Get student-specific analytics
   */
  private async getStudentAnalytics(tenantId: string, userId: string): Promise<PersonaAnalytics> {
    const learnerProfile = await this.prisma.learnerProfile.findUnique({
      where: { userId },
      include: {
        masteries: true,
      },
    });

    const engagement = {
      timeSpent: learnerProfile?.avgSessionMinutes || 30,
      sessionsCompleted: 0, // Would come from session tracking
      streakDays: learnerProfile?.streakDays || 0,
    };

    const learning = {
      conceptsMastered: learnerProfile?.masteries.filter((m) => m.masteryProbability > 0.8).length || 0,
      avgAccuracy: this.calculateAverageAccuracy(learnerProfile?.masteries || []),
      progressPercent: this.calculateProgress(learnerProfile?.masteries || []),
    };

    const content = {
      questionsAnswered: learnerProfile?.masteries.reduce((sum, m) => sum + m.attempts, 0) || 0,
      aiInteractions: await this.getAIInteractionCount(userId),
      simulationsCompleted: 0, // Would come from simulation tracking
    };

    const insights = this.generateStudentInsights(engagement, learning, content);
    const recommendations = this.generateStudentRecommendations(engagement, learning, content);

    return {
      persona: "student",
      metrics: { engagement, learning, content },
      insights,
      recommendations,
    };
  }

  /**
   * Get teacher-specific analytics
   */
  private async getTeacherAnalytics(tenantId: string, userId: string): Promise<PersonaAnalytics> {
    const modules = await this.prisma.module.findMany({
      where: { tenantId },
    });

    const students = await this.prisma.learnerProfile.count({
      where: { tenantId },
    });

    const engagement = {
      timeSpent: 0, // Would come from teacher activity tracking
      sessionsCompleted: modules.length,
      streakDays: 0,
    };

    const learning = {
      conceptsMastered: 0,
      avgAccuracy: 0,
      progressPercent: 0,
    };

    const content = {
      questionsAnswered: modules.reduce((sum, m) => sum + ((m as ModuleData).questionCount || 0), 0),
      aiInteractions: await this.getAIInteractionCount(userId),
      simulationsCompleted: 0,
    };

    const insights = this.generateTeacherInsights(modules, students);
    const recommendations = this.generateTeacherRecommendations(modules, students);

    return {
      persona: "teacher",
      metrics: { engagement, learning, content },
      insights,
      recommendations,
    };
  }

  /**
   * Get admin-specific analytics
   */
  private async getAdminAnalytics(tenantId: string, userId: string): Promise<PersonaAnalytics> {
    const users = await this.prisma.user.count({ where: { tenantId } });
    const modules = await this.prisma.module.count({ where: { tenantId } });

    const engagement = {
      timeSpent: 0,
      sessionsCompleted: 0,
      streakDays: 0,
    };

    const learning = {
      conceptsMastered: 0,
      avgAccuracy: 0,
      progressPercent: 0,
    };

    const content = {
      questionsAnswered: 0,
      aiInteractions: await this.getTenantAIInteractions(tenantId),
      simulationsCompleted: 0,
    };

    const insights = this.generateAdminInsights(users, modules, tenantId);
    const recommendations = this.generateAdminRecommendations(users, modules);

    return {
      persona: "admin",
      metrics: { engagement, learning, content },
      insights,
      recommendations,
    };
  }

  /**
   * Get parent-specific analytics
   */
  private async getParentAnalytics(tenantId: string, userId: string): Promise<PersonaAnalytics> {
    // Find the student(s) associated with this parent
    const students = await this.prisma.user.findMany({
      where: {
        tenantId,
        parentId: userId,
      },
    });

    const studentIds = students.map((s) => s.id);

    const learnerProfiles = await this.prisma.learnerProfile.findMany({
      where: { userId: { in: studentIds } },
      include: { masteries: true },
    });

    const engagement = {
      timeSpent: learnerProfiles.reduce((sum, lp) => sum + (lp.avgSessionMinutes || 0), 0),
      sessionsCompleted: 0,
      streakDays: learnerProfiles.reduce((sum, lp) => sum + (lp.streakDays || 0), 0),
    };

    const learning = {
      conceptsMastered: learnerProfiles.reduce(
        (sum, lp) => sum + lp.masteries.filter((m) => m.masteryProbability > 0.8).length,
        0,
      ),
      avgAccuracy: this.calculateAverageAccuracy(
        learnerProfiles.flatMap((lp) => lp.masteries),
      ),
      progressPercent: this.calculateProgress(
        learnerProfiles.flatMap((lp) => lp.masteries),
      ),
    };

    const content = {
      questionsAnswered: learnerProfiles.reduce(
        (sum, lp) => sum + lp.masteries.reduce((s, m) => s + m.attempts, 0),
        0,
      ),
      aiInteractions: await Promise.all(studentIds.map((id) => this.getAIInteractionCount(id))).then((counts) => counts.reduce((sum, count) => sum + count, 0)),
      simulationsCompleted: 0,
    };

    const insights = this.generateParentInsights(learnerProfiles);
    const recommendations = this.generateParentRecommendations(learnerProfiles);

    return {
      persona: "parent",
      metrics: { engagement, learning, content },
      insights,
      recommendations,
    };
  }

  /**
   * Calculate average accuracy from mastery data
   */
  private calculateAverageAccuracy(masteries: MasteryData[]): number {
    if (masteries.length === 0) return 0;
    const totalAttempts = masteries.reduce((sum, m) => sum + m.attempts, 0);
    const totalCorrect = masteries.reduce((sum, m) => sum + m.correctAttempts, 0);
    return totalAttempts > 0 ? totalCorrect / totalAttempts : 0;
  }

  /**
   * Calculate overall progress percentage
   */
  private calculateProgress(masteries: MasteryData[]): number {
    if (masteries.length === 0) return 0;
    const avgMastery = masteries.reduce((sum, m) => sum + m.masteryProbability, 0) / masteries.length;
    return avgMastery * 100;
  }

  /**
   * Get AI interaction count for a user
   */
  private async getAIInteractionCount(userId: string): Promise<number> {
    const count = await this.prisma.aIAuditLog.count({
      where: { userId },
    });
    return count;
  }

  /**
   * Get total AI interactions for a tenant
   */
  private async getTenantAIInteractions(tenantId: string): Promise<number> {
    const count = await this.prisma.aIAuditLog.count({
      where: { tenantId },
    });
    return count;
  }

  /**
   * Generate student-specific insights
   */
  private generateStudentInsights(
    engagement: PersonaAnalytics["metrics"]["engagement"],
    learning: PersonaAnalytics["metrics"]["learning"],
    content: PersonaAnalytics["metrics"]["content"],
  ): string[] {
    const insights: string[] = [];

    if (engagement.streakDays >= 7) {
      insights.push("Great job maintaining a consistent learning streak!");
    }

    if (learning.avgAccuracy > 0.8) {
      insights.push("Your accuracy is excellent - keep up the good work!");
    }

    if (content.aiInteractions > 10) {
      insights.push("You're making good use of the AI tutor to support your learning.");
    }

    if (insights.length === 0) {
      insights.push("Keep practicing to see more personalized insights.");
    }

    return insights;
  }

  /**
   * Generate student-specific recommendations
   */
  private generateStudentRecommendations(
    engagement: PersonaAnalytics["metrics"]["engagement"],
    learning: PersonaAnalytics["metrics"]["learning"],
    content: PersonaAnalytics["metrics"]["content"],
  ): string[] {
    const recommendations: string[] = [];

    if (learning.avgAccuracy < 0.6) {
      recommendations.push("Review concepts where accuracy is below 60% to strengthen your foundation.");
    }

    if (engagement.streakDays === 0) {
      recommendations.push("Try to practice daily to build a learning streak.");
    }

    if (content.aiInteractions === 0) {
      recommendations.push("Use the AI tutor to get personalized help with difficult concepts.");
    }

    if (recommendations.length === 0) {
      recommendations.push("Continue your current learning path - you're on track!");
    }

    return recommendations;
  }

  /**
   * Generate teacher-specific insights
   */
  private generateTeacherInsights(modules: ModuleData[], studentCount: number): string[] {
    const insights: string[] = [];

    if (modules.length > 5) {
      insights.push(`You've created ${modules.length} learning modules - impressive contribution!`);
    }

    if (studentCount > 10) {
      insights.push(`Your content is being used by ${studentCount} students.`);
    }

    if (insights.length === 0) {
      insights.push("Create more content to reach more students.");
    }

    return insights;
  }

  /**
   * Generate teacher-specific recommendations
   */
  private generateTeacherRecommendations(modules: ModuleData[], studentCount: number): string[] {
    const recommendations: string[] = [];

    if (modules.length < 3) {
      recommendations.push("Consider creating more learning modules to expand your curriculum.");
    }

    if (studentCount < 5) {
      recommendations.push("Share your modules with more students to increase engagement.");
    }

    if (recommendations.length === 0) {
      recommendations.push("Your teaching materials are well-utilized. Keep up the great work!");
    }

    return recommendations;
  }

  /**
   * Generate admin-specific insights
   */
  private generateAdminInsights(userCount: number, moduleCount: number, tenantId: string): string[] {
    const insights: string[] = [];

    if (userCount > 10) {
      insights.push(`Your tenant has ${userCount} active users.`);
    }

    if (moduleCount > 20) {
      insights.push(`Excellent content library with ${moduleCount} modules.`);
    }

    if (insights.length === 0) {
      insights.push("Focus on user acquisition and content creation to grow your platform.");
    }

    return insights;
  }

  /**
   * Generate admin-specific recommendations
   */
  private generateAdminRecommendations(userCount: number, moduleCount: number): string[] {
    const recommendations: string[] = [];

    if (userCount < 5) {
      recommendations.push("Consider outreach programs to increase user adoption.");
    }

    if (moduleCount < 10) {
      recommendations.push("Invest in content creation to build a comprehensive library.");
    }

    if (recommendations.length === 0) {
      recommendations.push("Your platform metrics are healthy. Continue optimizing for growth.");
    }

    return recommendations;
  }

  /**
   * Generate parent-specific insights
   */
  private generateParentInsights(learnerProfiles: LearnerProfileData[]): string[] {
    const insights: string[] = [];

    if (learnerProfiles.length > 0) {
      const avgStreak = learnerProfiles.reduce((sum, lp) => sum + (lp.streakDays || 0), 0) / learnerProfiles.length;
      if (avgStreak > 5) {
        insights.push("Your child is maintaining consistent learning habits.");
      }
    }

    if (insights.length === 0) {
      insights.push("Encourage your child to practice regularly to see insights.");
    }

    return insights;
  }

  /**
   * Generate parent-specific recommendations
   */
  private generateParentRecommendations(learnerProfiles: LearnerProfileData[]): string[] {
    const recommendations: string[] = [];

    if (learnerProfiles.length > 0) {
      const avgAccuracy = this.calculateAverageAccuracy(
        learnerProfiles.flatMap((lp) => lp.masteries),
      );
      if (avgAccuracy < 0.7) {
        recommendations.push("Encourage your child to review challenging concepts.");
      }
    }

    if (recommendations.length === 0) {
      recommendations.push("Your child is making good progress. Keep supporting their learning!");
    }

    return recommendations;
  }
}
