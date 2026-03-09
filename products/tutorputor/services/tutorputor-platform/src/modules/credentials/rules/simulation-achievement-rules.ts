/**
 * Simulation Achievement Rules
 *
 * Rule engine for determining when to issue credentials based on
 * simulation performance and learning achievements.
 */

import type {
    AchievementCategory,
    CredentialType,
    SkillLevel,
    AchievementCriteria,
} from "../models/credential";

// =============================================================================
// Types
// =============================================================================

export interface SimulationResult {
    simulationId: string;
    simulationName: string;
    domainPackId?: string;
    userId: string;
    tenantId: string;
    domain: string;

    // Performance metrics
    score: number;
    maxScore: number;
    completionTime: number; // seconds
    attempts: number;
    hintsUsed: number;
    errorsCount: number;

    // State
    completed: boolean;
    passed: boolean;
    perfectScore: boolean;

    // Context
    difficulty: "easy" | "medium" | "hard" | "expert";
    startedAt: Date;
    completedAt: Date;

    // Additional metrics
    interactionsCount: number;
    correctInteractions: number;
    experimentsConducted?: number;
    hypothesesTested?: number;
}

export interface LearningProgress {
    userId: string;
    tenantId: string;

    // Streak tracking
    currentStreak: number;
    longestStreak: number;
    lastActivityDate: Date;

    // Completion metrics
    simulationsCompleted: number;
    domainPacksCompleted: number;
    totalTimeSpent: number; // minutes

    // Performance aggregates
    averageScore: number;
    perfectScoresCount: number;
    firstAttemptPassCount: number;

    // Domain-specific progress
    domainProgress: Map<string, DomainProgress>;
}

export interface DomainProgress {
    domain: string;
    simulationsCompleted: number;
    totalSimulations: number;
    averageScore: number;
    skillLevel: SkillLevel;
    lastCompletedAt: Date;
}

export interface AchievementRule {
    id: string;
    name: string;
    description: string;
    category: AchievementCategory;
    credentialType: CredentialType;

    // Criteria
    evaluate: (result: SimulationResult, progress: LearningProgress) => EvaluationResult;

    // Credential details
    tier: "bronze" | "silver" | "gold" | "platinum";
    rarity: "common" | "uncommon" | "rare" | "epic" | "legendary";
    points: number;
    imageUrl?: string;
}

export interface EvaluationResult {
    achieved: boolean;
    criteria: AchievementCriteria[];
    credentialName?: string;
    credentialDescription?: string;
    metadata?: Record<string, unknown>;
}

// =============================================================================
// Achievement Rules Registry
// =============================================================================

export const achievementRules: AchievementRule[] = [
    // ===== SIMULATION MASTERY =====
    {
        id: "first_simulation",
        name: "First Steps",
        description: "Complete your first simulation",
        category: "simulation_mastery",
        credentialType: "badge",
        tier: "bronze",
        rarity: "common",
        points: 10,
        evaluate: (result, progress) => {
            const achieved = result.completed && progress.simulationsCompleted === 1;
            return {
                achieved,
                criteria: [
                    {
                        id: "complete_first",
                        name: "Complete First Simulation",
                        description: "Successfully complete any simulation",
                        met: achieved,
                    },
                ],
                credentialName: "First Steps",
                credentialDescription: "Completed your first simulation. The journey begins!",
            };
        },
    },

    {
        id: "simulation_veteran",
        name: "Simulation Veteran",
        description: "Complete 50 simulations",
        category: "simulation_mastery",
        credentialType: "badge",
        tier: "gold",
        rarity: "rare",
        points: 100,
        evaluate: (_result, progress) => {
            const achieved = progress.simulationsCompleted >= 50;
            return {
                achieved,
                criteria: [
                    {
                        id: "complete_50",
                        name: "Complete 50 Simulations",
                        description: "Accumulate 50 completed simulations",
                        met: achieved,
                        value: progress.simulationsCompleted,
                        threshold: 50,
                    },
                ],
                credentialName: "Simulation Veteran",
                credentialDescription:
                    "Completed 50 simulations. Your dedication to learning is remarkable!",
            };
        },
    },

    // ===== PERFECTIONIST =====
    {
        id: "perfect_score",
        name: "Perfectionist",
        description: "Achieve a perfect score on any simulation",
        category: "perfectionist",
        credentialType: "achievement",
        tier: "silver",
        rarity: "uncommon",
        points: 25,
        evaluate: (result) => {
            const achieved = result.perfectScore;
            return {
                achieved,
                criteria: [
                    {
                        id: "perfect",
                        name: "Perfect Score",
                        description: "Score 100% on a simulation",
                        met: achieved,
                        value: result.score,
                        threshold: result.maxScore,
                    },
                ],
                credentialName: `Perfect Score: ${result.simulationName}`,
                credentialDescription: `Achieved a perfect score on ${result.simulationName}. Flawless execution!`,
            };
        },
    },

    {
        id: "perfect_streak_5",
        name: "Perfect Streak",
        description: "Achieve 5 consecutive perfect scores",
        category: "perfectionist",
        credentialType: "achievement",
        tier: "gold",
        rarity: "rare",
        points: 75,
        evaluate: (_result, progress) => {
            const achieved = progress.perfectScoresCount >= 5;
            return {
                achieved,
                criteria: [
                    {
                        id: "streak_5",
                        name: "5 Perfect Scores",
                        description: "Achieve 5 consecutive perfect scores",
                        met: achieved,
                        value: progress.perfectScoresCount,
                        threshold: 5,
                    },
                ],
                credentialName: "Perfect Streak Master",
                credentialDescription: "Achieved 5 consecutive perfect scores. Unstoppable!",
            };
        },
    },

    // ===== SPEED RUN =====
    {
        id: "speed_demon",
        name: "Speed Demon",
        description: "Complete a simulation in under 2 minutes with a passing score",
        category: "speed_run",
        credentialType: "achievement",
        tier: "silver",
        rarity: "uncommon",
        points: 30,
        evaluate: (result) => {
            const achieved = result.passed && result.completionTime < 120;
            return {
                achieved,
                criteria: [
                    {
                        id: "fast_complete",
                        name: "Quick Completion",
                        description: "Complete in under 2 minutes",
                        met: result.completionTime < 120,
                        value: result.completionTime,
                        threshold: 120,
                    },
                    {
                        id: "passed",
                        name: "Passing Score",
                        description: "Achieve a passing score",
                        met: result.passed,
                    },
                ],
                credentialName: `Speed Demon: ${result.simulationName}`,
                credentialDescription: `Completed ${result.simulationName} in record time!`,
                metadata: {
                    completionTime: result.completionTime,
                },
            };
        },
    },

    {
        id: "lightning_fast",
        name: "Lightning Fast",
        description: "Complete an expert simulation in under 3 minutes with perfect score",
        category: "speed_run",
        credentialType: "achievement",
        tier: "platinum",
        rarity: "legendary",
        points: 200,
        evaluate: (result) => {
            const achieved =
                result.difficulty === "expert" &&
                result.perfectScore &&
                result.completionTime < 180;
            return {
                achieved,
                criteria: [
                    {
                        id: "expert_level",
                        name: "Expert Difficulty",
                        description: "Complete an expert-level simulation",
                        met: result.difficulty === "expert",
                    },
                    {
                        id: "perfect",
                        name: "Perfect Score",
                        description: "Achieve 100% score",
                        met: result.perfectScore,
                    },
                    {
                        id: "under_3_min",
                        name: "Under 3 Minutes",
                        description: "Complete in under 3 minutes",
                        met: result.completionTime < 180,
                        value: result.completionTime,
                        threshold: 180,
                    },
                ],
                credentialName: "Lightning Fast",
                credentialDescription:
                    "Mastered an expert simulation with perfect score in under 3 minutes. Legendary!",
            };
        },
    },

    // ===== LEARNING STREAK =====
    {
        id: "streak_7",
        name: "Week Warrior",
        description: "Maintain a 7-day learning streak",
        category: "learning_streak",
        credentialType: "badge",
        tier: "bronze",
        rarity: "common",
        points: 20,
        evaluate: (_result, progress) => {
            const achieved = progress.currentStreak >= 7;
            return {
                achieved,
                criteria: [
                    {
                        id: "streak_7_days",
                        name: "7 Day Streak",
                        description: "Learn every day for a week",
                        met: achieved,
                        value: progress.currentStreak,
                        threshold: 7,
                    },
                ],
                credentialName: "Week Warrior",
                credentialDescription: "Maintained a 7-day learning streak. Consistency is key!",
            };
        },
    },

    {
        id: "streak_30",
        name: "Monthly Master",
        description: "Maintain a 30-day learning streak",
        category: "learning_streak",
        credentialType: "achievement",
        tier: "gold",
        rarity: "rare",
        points: 100,
        evaluate: (_result, progress) => {
            const achieved = progress.currentStreak >= 30;
            return {
                achieved,
                criteria: [
                    {
                        id: "streak_30_days",
                        name: "30 Day Streak",
                        description: "Learn every day for a month",
                        met: achieved,
                        value: progress.currentStreak,
                        threshold: 30,
                    },
                ],
                credentialName: "Monthly Master",
                credentialDescription: "Maintained a 30-day learning streak. Incredible dedication!",
            };
        },
    },

    // ===== PROBLEM SOLVING =====
    {
        id: "no_hints",
        name: "Independent Thinker",
        description: "Complete a simulation without using any hints",
        category: "problem_solving",
        credentialType: "badge",
        tier: "silver",
        rarity: "uncommon",
        points: 25,
        evaluate: (result) => {
            const achieved = result.passed && result.hintsUsed === 0;
            return {
                achieved,
                criteria: [
                    {
                        id: "no_hints_used",
                        name: "No Hints",
                        description: "Complete without using hints",
                        met: result.hintsUsed === 0,
                        value: result.hintsUsed,
                        threshold: 0,
                    },
                    {
                        id: "passed",
                        name: "Passed",
                        description: "Achieve a passing score",
                        met: result.passed,
                    },
                ],
                credentialName: `Independent Thinker: ${result.simulationName}`,
                credentialDescription: `Completed ${result.simulationName} without any hints!`,
            };
        },
    },

    {
        id: "first_try",
        name: "First Try Success",
        description: "Pass a simulation on the first attempt",
        category: "problem_solving",
        credentialType: "badge",
        tier: "bronze",
        rarity: "common",
        points: 15,
        evaluate: (result) => {
            const achieved = result.passed && result.attempts === 1;
            return {
                achieved,
                criteria: [
                    {
                        id: "first_attempt",
                        name: "First Attempt",
                        description: "Pass on first try",
                        met: result.attempts === 1,
                    },
                    {
                        id: "passed",
                        name: "Passed",
                        description: "Achieve a passing score",
                        met: result.passed,
                    },
                ],
                credentialName: `First Try Success: ${result.simulationName}`,
                credentialDescription: `Passed ${result.simulationName} on the first attempt!`,
            };
        },
    },

    // ===== DOMAIN EXPERTISE =====
    {
        id: "domain_beginner",
        name: "Domain Explorer",
        description: "Complete 5 simulations in a single domain",
        category: "domain_expertise",
        credentialType: "skill",
        tier: "bronze",
        rarity: "common",
        points: 30,
        evaluate: (result, progress) => {
            const domainProgress = progress.domainProgress.get(result.domain);
            const achieved = domainProgress ? domainProgress.simulationsCompleted >= 5 : false;
            return {
                achieved,
                criteria: [
                    {
                        id: "domain_5",
                        name: "5 Domain Simulations",
                        description: `Complete 5 simulations in ${result.domain}`,
                        met: achieved,
                        value: domainProgress?.simulationsCompleted || 0,
                        threshold: 5,
                    },
                ],
                credentialName: `${result.domain} Explorer`,
                credentialDescription: `Completed 5 simulations in ${result.domain}. Your journey into ${result.domain} has begun!`,
                metadata: {
                    domain: result.domain,
                    skillLevel: "beginner",
                },
            };
        },
    },

    {
        id: "domain_expert",
        name: "Domain Expert",
        description: "Complete all simulations in a domain pack with 90%+ average score",
        category: "domain_expertise",
        credentialType: "certificate",
        tier: "platinum",
        rarity: "epic",
        points: 250,
        evaluate: (result, progress) => {
            const domainProgress = progress.domainProgress.get(result.domain);
            const allCompleted =
                domainProgress &&
                domainProgress.simulationsCompleted === domainProgress.totalSimulations;
            const highScore = domainProgress && domainProgress.averageScore >= 90;
            const achieved = !!(allCompleted && highScore);

            return {
                achieved,
                criteria: [
                    {
                        id: "all_complete",
                        name: "All Simulations Complete",
                        description: "Complete all simulations in the domain",
                        met: !!allCompleted,
                        value: domainProgress?.simulationsCompleted || 0,
                        threshold: domainProgress?.totalSimulations || 0,
                    },
                    {
                        id: "high_average",
                        name: "90%+ Average Score",
                        description: "Maintain 90% average across all simulations",
                        met: !!highScore,
                        value: domainProgress?.averageScore || 0,
                        threshold: 90,
                    },
                ],
                credentialName: `${result.domain} Expert Certification`,
                credentialDescription: `Achieved mastery in ${result.domain} with exceptional performance. You are now certified as a ${result.domain} Expert!`,
                metadata: {
                    domain: result.domain,
                    skillLevel: "expert",
                    averageScore: domainProgress?.averageScore,
                },
            };
        },
    },

    // ===== EXPLORER =====
    {
        id: "multi_domain",
        name: "Renaissance Learner",
        description: "Complete simulations in 5 different domains",
        category: "explorer",
        credentialType: "achievement",
        tier: "gold",
        rarity: "rare",
        points: 75,
        evaluate: (_result, progress) => {
            const domainsExplored = progress.domainProgress.size;
            const achieved = domainsExplored >= 5;

            return {
                achieved,
                criteria: [
                    {
                        id: "five_domains",
                        name: "5 Domains Explored",
                        description: "Complete simulations across 5 different domains",
                        met: achieved,
                        value: domainsExplored,
                        threshold: 5,
                    },
                ],
                credentialName: "Renaissance Learner",
                credentialDescription:
                    "Explored 5 different domains. Your curiosity knows no bounds!",
                metadata: {
                    domainsExplored,
                    domains: Array.from(progress.domainProgress.keys()),
                },
            };
        },
    },

    {
        id: "hypothesis_tester",
        name: "Scientific Mind",
        description: "Test 50 hypotheses across simulations",
        category: "explorer",
        credentialType: "badge",
        tier: "silver",
        rarity: "uncommon",
        points: 40,
        evaluate: (result) => {
            const achieved = (result.hypothesesTested || 0) >= 50;
            return {
                achieved,
                criteria: [
                    {
                        id: "hypotheses_50",
                        name: "50 Hypotheses Tested",
                        description: "Test 50 scientific hypotheses",
                        met: achieved,
                        value: result.hypothesesTested || 0,
                        threshold: 50,
                    },
                ],
                credentialName: "Scientific Mind",
                credentialDescription: "Tested 50 hypotheses. A true scientist at heart!",
            };
        },
    },
];

// =============================================================================
// Rule Engine
// =============================================================================

export function evaluateAchievements(
    result: SimulationResult,
    progress: LearningProgress
): EvaluationResult[] {
    const achieved: EvaluationResult[] = [];

    for (const rule of achievementRules) {
        const evaluation = rule.evaluate(result, progress);
        if (evaluation.achieved) {
            achieved.push({
                ...evaluation,
                metadata: {
                    ...evaluation.metadata,
                    ruleId: rule.id,
                    ruleName: rule.name,
                    category: rule.category,
                    tier: rule.tier,
                    rarity: rule.rarity,
                    points: rule.points,
                    credentialType: rule.credentialType,
                },
            });
        }
    }

    return achieved;
}

export function getRulesByCategory(category: AchievementCategory): AchievementRule[] {
    return achievementRules.filter((rule) => rule.category === category);
}

export function getRuleById(id: string): AchievementRule | undefined {
    return achievementRules.find((rule) => rule.id === id);
}

export interface AchievementProgress {
    ruleId: string;
    ruleName: string;
    category: AchievementCategory;
    achieved: boolean;
    progress: number;
    criteria: AchievementCriteria[];
}

export function getProgressTowardsAchievements(
    result: SimulationResult,
    progress: LearningProgress
): AchievementProgress[] {
    return achievementRules.map((rule) => {
        const evaluation = rule.evaluate(result, progress);
        const metCriteria = evaluation.criteria.filter((c) => c.met).length;
        const totalCriteria = evaluation.criteria.length;

        return {
            ruleId: rule.id,
            ruleName: rule.name,
            category: rule.category,
            achieved: evaluation.achieved,
            progress: totalCriteria > 0 ? (metCriteria / totalCriteria) * 100 : 0,
            criteria: evaluation.criteria,
        };
    });
}
