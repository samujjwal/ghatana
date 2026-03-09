/**
 * IC - Personal Growth Plan
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { MainLayout } from '@/app/Layout';
import { GrowthPlanDashboard } from '@/components/ic/GrowthPlanDashboard';
import type {
    Competency,
    DevelopmentGoal,
    GoalCategory,
    GoalStatus,
    GrowthPlan,
    Skill,
    SkillLevel,
} from '@/components/ic/GrowthPlanDashboard';
import { rootApi } from '@/services/api';
import type { GrowthPlanResponse } from '@/services/api';
import { useGrowthPlansList } from '@/hooks/useGrowthPlansApi';
import { useQuery } from '@tanstack/react-query';
import { Typography } from '@ghatana/ui';

export default function IcGrowthRoute() {
    const demoUserEmail = 'ic1@example.com';

    const userQuery = useQuery({
        queryKey: ['root', 'users', 'search', demoUserEmail],
        queryFn: () => rootApi.searchUsers(demoUserEmail),
    });

    const demoUser = userQuery.data?.[0];

    const plansQuery = useGrowthPlansList({
        userId: demoUser?.id,
        limit: 1,
        offset: 0,
    });

    const apiPlan = plansQuery.data?.data?.[0];
    const dashboardPlan = apiPlan && demoUser ? toDashboardGrowthPlan(apiPlan, demoUser) : undefined;

    return (
        <MainLayout>
            <div className="p-6">
                {(userQuery.isLoading || plansQuery.isLoading) && (
                    <Typography variant="body2" className="mb-3 text-slate-600 dark:text-neutral-400">
                        Loading growth plan…
                    </Typography>
                )}
                {(userQuery.isError || plansQuery.isError) && (
                    <Typography variant="body2" className="mb-3 text-slate-600 dark:text-neutral-400">
                        Could not load growth plan; showing demo data.
                    </Typography>
                )}
                <GrowthPlanDashboard plan={dashboardPlan} />
            </div>
        </MainLayout>
    );
}

function toDashboardGrowthPlan(apiPlan: GrowthPlanResponse, user: { id: string; name: string }): GrowthPlan {
    const skills = parseSkills(apiPlan.skills);
    const goals = parseGoals(apiPlan.goals);

    const competencies = toCompetencies(skills, apiPlan.updatedAt);
    const dashboardGoals = toDevelopmentGoals(goals, apiPlan.createdAt, apiPlan.updatedAt);

    const currentRole = extractRoleFromName(user.name) ?? 'Engineer';
    const targetRole = extractTargetRoleFromTitle(apiPlan.title);

    const totalGoals = dashboardGoals.length;
    const completedGoals = dashboardGoals.filter((g) => g.status === 'completed').length;

    return {
        id: apiPlan.id,
        userId: apiPlan.userId,
        currentRole,
        targetRole,
        startDate: apiPlan.createdAt,
        visibility: 'manager',
        competencies,
        goals: dashboardGoals,
        completedGoals,
        totalGoals,
        overallProgress: clampPercent(apiPlan.progress),
    };
}

type ParsedSkill = { name: string; current: number; target: number; category: string };
type ParsedGoal = { title: string; description: string; targetDate: string; status: string; progress: number };

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
}

function parseSkills(value: unknown): ParsedSkill[] {
    if (!Array.isArray(value)) return [];
    return value
        .filter(isRecord)
        .map((s) => ({
            name: typeof s.name === 'string' ? s.name : 'Skill',
            current: typeof s.current === 'number' ? s.current : 0,
            target: typeof s.target === 'number' ? s.target : 0,
            category: typeof s.category === 'string' ? s.category : 'technical',
        }));
}

function parseGoals(value: unknown): ParsedGoal[] {
    if (!Array.isArray(value)) return [];
    return value
        .filter(isRecord)
        .map((g) => ({
            title: typeof g.title === 'string' ? g.title : 'Goal',
            description: typeof g.description === 'string' ? g.description : '',
            targetDate: typeof g.targetDate === 'string' ? g.targetDate : new Date().toISOString(),
            status: typeof g.status === 'string' ? g.status : 'not_started',
            progress: typeof g.progress === 'number' ? g.progress : 0,
        }));
}

function toCompetencies(skills: ParsedSkill[], lastUpdated: string): Competency[] {
    const groups = new Map<string, ParsedSkill[]>();
    for (const s of skills) {
        const key = normalizeCategory(s.category);
        const group = groups.get(key);
        if (group) group.push(s);
        else groups.set(key, [s]);
    }

    const competencies: Competency[] = [];
    for (const [category, groupSkills] of groups.entries()) {
        const dashboardSkills: Skill[] = groupSkills.map((s, idx) => {
            const progress = s.target > 0 ? clampPercent((s.current / s.target) * 100) : 0;
            return {
                id: `skill-${slugify(s.name)}-${idx}`,
                name: s.name,
                category: categoryLabel(category),
                currentLevel: numberToSkillLevel(s.current),
                targetLevel: numberToSkillLevel(s.target),
                progress,
                lastUpdated,
            };
        });

        const currentAvg = average(groupSkills.map((s) => s.current));
        const targetAvg = average(groupSkills.map((s) => s.target));

        competencies.push({
            id: `comp-${category}`,
            name: categoryLabel(category),
            description: categoryDescription(category),
            currentScore: roundTo1(currentAvg),
            targetScore: roundTo1(targetAvg),
            skills: dashboardSkills,
        });
    }

    return competencies.length
        ? competencies
        : [
            {
                id: 'comp-technical',
                name: 'Technical',
                description: 'Core technical skills and expertise',
                currentScore: 0,
                targetScore: 0,
                skills: [],
            },
        ];
}

function toDevelopmentGoals(goals: ParsedGoal[], createdAt: string, updatedAt: string): DevelopmentGoal[] {
    return goals.map((g, idx) => {
        const category: GoalCategory = inferGoalCategory(g.title);
        const status: GoalStatus = normalizeGoalStatus(g.status);
        return {
            id: `goal-${idx}`,
            title: g.title,
            description: g.description,
            category,
            status,
            progress: clampPercent(g.progress),
            targetDate: g.targetDate,
            milestones: [],
            createdAt,
            updatedAt,
        };
    });
}

function extractRoleFromName(name: string): string | null {
    const start = name.indexOf('(');
    const end = name.lastIndexOf(')');
    if (start === -1 || end === -1 || end <= start + 1) return null;
    return name.slice(start + 1, end).trim() || null;
}

function extractTargetRoleFromTitle(title: string): string | undefined {
    const prefix = 'Path to ';
    if (title.startsWith(prefix)) return title.slice(prefix.length).trim() || undefined;
    return title.includes('Staff Engineer') ? 'Staff Engineer' : undefined;
}

function normalizeCategory(category: string): string {
    const c = category.trim().toLowerCase();
    if (c.includes('lead')) return 'leadership';
    if (c.includes('business')) return 'business';
    if (c.includes('comm')) return 'communication';
    return 'technical';
}

function categoryLabel(category: string): string {
    switch (category) {
        case 'leadership':
            return 'Leadership';
        case 'business':
            return 'Business';
        case 'communication':
            return 'Communication';
        default:
            return 'Technical';
    }
}

function categoryDescription(category: string): string {
    switch (category) {
        case 'leadership':
            return 'Leading teams and mentoring engineers';
        case 'business':
            return 'Understanding and driving business outcomes';
        case 'communication':
            return 'Influencing and communicating effectively';
        default:
            return 'Core technical skills and expertise';
    }
}

function inferGoalCategory(title: string): GoalCategory {
    const t = title.toLowerCase();
    if (t.includes('mentor') || t.includes('mentorship') || t.includes('lead')) return 'leadership';
    if (t.includes('stakeholder') || t.includes('present') || t.includes('communicat')) return 'communication';
    if (t.includes('product') || t.includes('business') || t.includes('metrics')) return 'business';
    return 'technical';
}

function normalizeGoalStatus(status: string): GoalStatus {
    const s = status.toLowerCase();
    if (s === 'completed') return 'completed';
    if (s === 'on_hold' || s === 'on-hold') return 'on-hold';
    if (s === 'in_progress' || s === 'in-progress') return 'in-progress';
    return 'not-started';
}

function numberToSkillLevel(value: number): SkillLevel {
    if (value >= 4) return 'expert';
    if (value >= 3) return 'advanced';
    if (value >= 2) return 'intermediate';
    return 'beginner';
}

function clampPercent(value: number): number {
    if (!Number.isFinite(value)) return 0;
    return Math.max(0, Math.min(100, Math.round(value)));
}

function average(values: number[]): number {
    if (!values.length) return 0;
    const sum = values.reduce((a, b) => a + b, 0);
    return sum / values.length;
}

function roundTo1(value: number): number {
    return Math.round(value * 10) / 10;
}

function slugify(value: string): string {
    return value
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)/g, '');
}
