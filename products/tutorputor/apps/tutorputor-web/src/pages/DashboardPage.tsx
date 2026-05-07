import { useNavigate, Link } from "react-router-dom";
import { useDashboard } from "../hooks/useDashboard";
import { useRecommendations } from "../hooks/useRecommendations";
import { Box, Card, Button } from "@/components/ui";
import { cardStyles, textStyles, badgeStyles, cn } from "../theme";
import { StatCard } from "../components/StatCard";
import { LearnerInsightPanel } from "../components/LearnerInsightPanel";
import { Sparkles, BookOpen, FlaskConical, FileText, MessageSquare } from "lucide-react";

// Local type definitions for component props
type EnrollmentStatus = "active" | "completed" | "paused" | "expired";

interface Enrollment {
  id: string;
  moduleId: string;
  moduleSlug?: string;
  moduleTitle?: string;
  status: EnrollmentStatus;
  progress: number;
  progressPercent: number;
  currentSectionId?: string;
  enrolledAt?: string;
  lastAccessedAt?: string;
  completedAt?: string;
  timeSpentSeconds: number;
}

interface ModuleSummary {
  id: string;
  title: string;
  slug: string;
  description?: string;
  thumbnailUrl?: string;
  estimatedMinutes?: number;
  estimatedTimeMinutes?: number;
  difficulty?: string;
  tags: string[];
  domain?: string;
  progressPercent?: number;
  isAiRecommended?: boolean;
  recommendationReason?: string;
}

interface ClaimMasteryState {
  claimId: string;
  masteryScore: number;
  evidenceCount: number;
  status: "not_started" | "developing" | "proficient" | "mastered";
}

interface LearnerActionState {
  currentClaimMastery?: ClaimMasteryState[];
  nextBestLesson?: {
    moduleId: string;
    moduleSlug?: string;
    moduleTitle: string;
    reason: string;
    targetClaimId?: string;
  } | null;
  unresolvedMisconceptions?: Array<{ description: string; claimId?: string }>;
  overdueSpacedRepetitionItems?: Array<{ claimId: string; reason: string }>;
  simulationAttemptsNeedingReview?: Array<{ reason: string; claimId?: string }>;
  recommendedRemediation?: Array<{ title: string; reason: string; claimId?: string }>;
  offlineResumeState?: { pendingItems: number; lastSyncedAt?: string } | null;
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useDashboard();
  const { data: aiRecommendations } = useRecommendations();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading your learning...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-red-600">Could not load dashboard. Please try again.</div>
      </div>
    );
  }

  if (!data) {
    return null;
  }

  const userName = data.user?.displayName?.split(" ")[0] || data.user?.email || "Learner";
  const currentEnrollments = data.currentEnrollments ?? [];
  const recommendedModules = aiRecommendations?.modules ?? data.recommendedModules ?? [];
  const hasActiveEnrollments = currentEnrollments.length > 0;
  const topEnrollment = hasActiveEnrollments ? currentEnrollments[0] : null;
  const actionState = data as LearnerActionState;

  const dashboardStats = {
    totalEnrollments: data.stats?.totalEnrollments ?? currentEnrollments.length,
    completedModules: data.stats?.completedModules ?? currentEnrollments.filter(e => e.status === "completed").length,
    averageProgress: data.stats?.averageProgress ?? 0,
  };

  const getModuleRoute = (enrollment: Enrollment) => {
    const modulePathSegment = enrollment.moduleSlug ?? enrollment.moduleId;
    return `/modules/${modulePathSegment}`;
  };

  const continueLearningHref = topEnrollment ? getModuleRoute(topEnrollment) : "/modules";
  const learnerActions = [
    { icon: BookOpen, label: "Continue Learning", href: continueLearningHref, color: "bg-blue-600" },
    { icon: FlaskConical, label: "Try Simulation", href: "/simulations", color: "bg-emerald-600" },
    { icon: FileText, label: "View Evidence", href: "/analytics", color: "bg-indigo-600" },
    { icon: MessageSquare, label: "Get Feedback", href: "/assessments", color: "bg-amber-600" },
  ];

  return (
    <Box className="p-6">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className={cn(textStyles.h1, "mb-2")}>
            Hello, {userName}!
          </h1>
          <p className={textStyles.muted}>
            {hasActiveEnrollments
              ? "Ready to continue your journey?"
              : "What would you like to learn today?"}
          </p>
        </div>

        {/* Stats Summary */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8 min-w-[260px]">
          <StatCard
            title="Enrolled"
            value={dashboardStats.totalEnrollments}
            color="blue"
          />
          <StatCard
            title="Completed"
            value={dashboardStats.completedModules}
            color="green"
          />
          <StatCard
            title="Avg. Progress"
            value={`${Math.round(dashboardStats.averageProgress)}%`}
            color="orange"
          />
        </div>

        {/* Engagement Insights Panel */}
        <LearnerInsightPanel className="mb-8" />

        <ActionableLearnerStatePanel state={actionState} />

        {/* Primary CTA: Continue Learning */}
        {hasActiveEnrollments && topEnrollment && (
          <section className="mb-8">
            <ContinueLearningCard 
              enrollment={topEnrollment}
              onResume={() => navigate(getModuleRoute(topEnrollment))}
              onViewAll={() => navigate("/enrollments")}
            />
          </section>
        )}

        {/* Secondary: Try Simulation */}
        <section className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className={textStyles.h2}>Try Simulation</h2>
            <Link 
              to="/simulations" 
              className="text-sm text-indigo-600 hover:text-indigo-700 font-medium"
            >
              Open simulation library →
            </Link>
          </div>
          
          {recommendedModules.length === 0 ? (
            <EmptyState 
              icon="🎯"
              title="Run a simulation to test your prediction"
              description="Pick a simulation, predict the outcome, and compare with observed results."
              action={{ label: "Try Simulation", href: "/simulations" }}
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {recommendedModules.slice(0, 3).map((module) => (
                <ModuleCard
                  key={module.id}
                  module={module}
                  onClick={() => navigate(`/modules/${module.slug}`)}
                />
              ))}
            </div>
          )}
        </section>

        {/* Quick Actions */}
        <section className="mb-8">
          <h2 className={cn(textStyles.h2, "mb-4")}>Quick Actions</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {learnerActions.map((action) => (
              <QuickActionButton key={action.label} {...action} />
            ))}
          </div>
        </section>

        {/* All Enrollments (if more than 1) */}
        {currentEnrollments.length > 1 && (
          <section className="mb-8">
            <div className="flex items-center justify-between mb-4">
              <h2 className={textStyles.h2}>My Learning</h2>
              <Link 
                to="/enrollments" 
                className="text-sm text-indigo-600 hover:text-indigo-700 font-medium"
              >
                See all →
              </Link>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {currentEnrollments.slice(1, 3).map((enrollment) => (
                <CompactEnrollmentCard
                  key={enrollment.id}
                  enrollment={enrollment}
                  onClick={() => navigate(getModuleRoute(enrollment))}
                />
              ))}
            </div>
          </section>
        )}
      </div>
    </Box>
  );
}

// Components

function ActionableLearnerStatePanel({ state }: { state: LearnerActionState }) {
  const claimMastery = state.currentClaimMastery ?? [];
  const weakestClaim = [...claimMastery].sort((a, b) => a.masteryScore - b.masteryScore)[0];
  const nextLessonHref = state.nextBestLesson?.moduleSlug
    ? `/modules/${state.nextBestLesson.moduleSlug}`
    : "/modules";

  return (
    <section className="mb-8" aria-label="Actionable learner state">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className={cardStyles.base}>
          <div className="p-5">
            <h2 className={cn(textStyles.h3, "mb-3")}>Current Claim Mastery</h2>
            {weakestClaim ? (
              <div>
                <p className="text-sm text-gray-600 mb-2">{weakestClaim.claimId}</p>
                <div className="flex items-center gap-3">
                  <div className="flex-1 bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-emerald-600 h-2 rounded-full"
                      style={{ width: `${Math.round(weakestClaim.masteryScore * 100)}%` }}
                    />
                  </div>
                  <span className="text-sm font-semibold">
                    {Math.round(weakestClaim.masteryScore * 100)}%
                  </span>
                </div>
                <p className="mt-2 text-xs text-gray-500">
                  {weakestClaim.evidenceCount} evidence events, {weakestClaim.status}
                </p>
              </div>
            ) : (
              <p className={textStyles.muted}>No claim evidence yet.</p>
            )}
          </div>
        </Card>

        <Card className={cardStyles.base}>
          <div className="p-5">
            <h2 className={cn(textStyles.h3, "mb-3")}>Next Best Lesson</h2>
            {state.nextBestLesson ? (
              <>
                <p className="font-medium text-gray-900">{state.nextBestLesson.moduleTitle}</p>
                <p className="text-sm text-gray-600 mt-1">{state.nextBestLesson.reason}</p>
                <Link className="inline-block mt-3 text-sm font-medium text-indigo-600" to={nextLessonHref}>
                  Open lesson
                </Link>
              </>
            ) : (
              <p className={textStyles.muted}>Complete the diagnostic to unlock recommendations.</p>
            )}
          </div>
        </Card>

        <Card className={cardStyles.base}>
          <div className="p-5">
            <h2 className={cn(textStyles.h3, "mb-3")}>Learning Decisions</h2>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <DecisionMetric label="Misconceptions" value={state.unresolvedMisconceptions?.length ?? 0} />
              <DecisionMetric label="Review Needed" value={state.simulationAttemptsNeedingReview?.length ?? 0} />
              <DecisionMetric label="Spaced Review" value={state.overdueSpacedRepetitionItems?.length ?? 0} />
              <DecisionMetric label="Remediation" value={state.recommendedRemediation?.length ?? 0} />
            </dl>
            {state.offlineResumeState && (
              <p className="mt-3 text-xs text-gray-500">
                Offline resumed with {state.offlineResumeState.pendingItems} pending sync items.
              </p>
            )}
          </div>
        </Card>
      </div>
    </section>
  );
}

function DecisionMetric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-gray-500">{label}</dt>
      <dd className="text-lg font-semibold text-gray-900">{value}</dd>
    </div>
  );
}

function ContinueLearningCard({ 
  enrollment, 
  onResume, 
  onViewAll 
}: { 
  enrollment: Enrollment; 
  onResume: () => void;
  onViewAll: () => void;
}) {
  return (
    <Card className={cn(cardStyles.base, "bg-gradient-to-r from-indigo-50 to-purple-50 border-indigo-100")}>
      <div className="p-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <span className={cn(badgeStyles.info, "mb-2 inline-block")}>Continue Learning</span>
            <h3 className={cn(textStyles.h3, "text-indigo-900")}>
              {enrollment.moduleTitle || "Your Current Module"}
            </h3>
          </div>
          <div className="text-right">
            <span className="text-2xl font-bold text-indigo-600">
              {Math.round(enrollment.progressPercent)}%
            </span>
            <p className="text-sm text-gray-500">complete</p>
          </div>
        </div>

        <div className="mb-4">
          <div className="w-full bg-white rounded-full h-3 shadow-inner">
            <div
              className="bg-gradient-to-r from-indigo-500 to-purple-500 h-3 rounded-full transition-all shadow-sm"
              style={{ width: `${enrollment.progressPercent}%` }}
            />
          </div>
        </div>

        <div className="flex gap-3">
          <Button 
            onClick={onResume}
            className="bg-indigo-600 hover:bg-indigo-700 text-white"
          >
            Resume Learning
          </Button>
          <Button 
            variant="outline" 
            onClick={onViewAll}
          >
            See All
          </Button>
        </div>
      </div>
    </Card>
  );
}

function ModuleCard({
  module,
  onClick,
}: {
  module: ModuleSummary;
  onClick?: () => void;
}) {
  return (
    <Card
      padded={false}
      className={cn(cardStyles.interactive, "cursor-pointer hover:shadow-md transition-all")}
      onClick={onClick}
    >
      <div className="p-5">
        <div className="flex items-start justify-between mb-2">
          <h3 className={cn(textStyles.h4, "line-clamp-2 flex-1 mr-2")}>{module.title}</h3>
          {module.isAiRecommended && (
            <Sparkles className="w-4 h-4 text-amber-500 flex-shrink-0" />
          )}
        </div>
        
        {module.recommendationReason && (
          <p className="text-xs text-amber-600 mb-2 flex items-center gap-1">
            <Sparkles className="w-3 h-3" />
            {module.recommendationReason}
          </p>
        )}
        
        <p className={cn(textStyles.small, "text-gray-500 mb-3 line-clamp-2")}>
          {module.description || module.tags.join(", ")}
        </p>
        
        <div className="flex items-center justify-between text-xs text-gray-500">
          <span className={cn(badgeStyles.info, "text-xs py-0.5 px-2")}>{module.domain}</span>
          <span>{module.estimatedTimeMinutes} min</span>
        </div>
      </div>
    </Card>
  );
}

function CompactEnrollmentCard({
  enrollment,
  onClick,
}: {
  enrollment: Enrollment;
  onClick?: () => void;
}) {
  return (
    <Card
      padded={false}
      className={cn(cardStyles.interactive, "cursor-pointer hover:shadow-sm transition-all")}
      onClick={onClick}
    >
      <div className="p-4 flex items-center gap-4">
        <div className="flex-1 min-w-0">
          <h4 className={cn(textStyles.h4, "truncate")}>
            {enrollment.moduleTitle || "Module"}
          </h4>
          <div className="flex items-center gap-2 mt-1">
            <div className="flex-1 bg-gray-200 rounded-full h-1.5">
              <div
                className="bg-blue-500 h-1.5 rounded-full"
                style={{ width: `${enrollment.progressPercent}%` }}
              />
            </div>
            <span className="text-xs text-gray-600 font-medium">
              {Math.round(enrollment.progressPercent)}%
            </span>
          </div>
        </div>
        <span className={cn(
          "text-xs px-2 py-1 rounded-full",
          enrollment.status === "active" ? "bg-green-100 text-green-700" :
          enrollment.status === "completed" ? "bg-blue-100 text-blue-700" :
          "bg-gray-100 text-gray-700"
        )}>
          {enrollment.status}
        </span>
      </div>
    </Card>
  );
}

function QuickActionButton({
  icon: Icon,
  label,
  href,
  color,
}: {
  icon: React.ElementType;
  label: string;
  href: string;
  color: string;
}) {
  return (
    <Link
      to={href}
      className={cn(
        "flex items-center gap-3 p-3 rounded-lg transition-all",
        "hover:bg-gray-50 dark:hover:bg-gray-800",
        "border border-gray-200 dark:border-gray-700 hover:border-gray-300"
      )}
    >
      <div className={cn("w-10 h-10 rounded-lg flex items-center justify-center text-white", color)}>
        <Icon className="w-5 h-5" />
      </div>
      <span className="font-medium text-gray-900 dark:text-gray-100">{label}</span>
    </Link>
  );
}

function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: string;
  title: string;
  description: string;
  action: { label: string; href: string };
}) {
  const navigate = useNavigate();
  
  return (
    <Card className={cn(cardStyles.base, "text-center py-12")}>
      <div className="text-5xl mb-4">{icon}</div>
      <h3 className={cn(textStyles.h3, "mb-2")}>{title}</h3>
      <p className={cn(textStyles.muted, "mb-6 max-w-md mx-auto")}>{description}</p>
      <Button 
        onClick={() => navigate(action.href)}
        className="bg-indigo-600 hover:bg-indigo-700 text-white"
      >
        {action.label}
      </Button>
    </Card>
  );
}
