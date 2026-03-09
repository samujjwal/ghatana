import { useNavigate, Link } from "react-router-dom";
import { useDashboard } from "../hooks/useDashboard";
import { Box, Card } from "@/components/ui";
import { cardStyles, textStyles, badgeStyles, cn } from "../theme";
import { StatCard } from "../components/StatCard";
import { PageHeader } from "../components/PageHeader";
import { ContentGenerationNav } from "../components/content-generation/ContentGenerationNav";

// Local type definitions for component props
type EnrollmentStatus = "active" | "completed" | "paused" | "expired";

interface Enrollment {
  id: string;
  moduleId: string;
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
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useDashboard();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading dashboard...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-red-600">Error loading dashboard</div>
      </div>
    );
  }

  if (!data) {
    return null;
  }

  const userName = data.user?.displayName || data.user?.email || "Student";
  const userEmail = data.user?.email;
  const currentEnrollments = data.currentEnrollments ?? [];
  const recommendedModules = data.recommendedModules ?? [];
  const stats = data.stats;

  const totalEnrollments = currentEnrollments.length;
  const completedEnrollments = currentEnrollments.filter(
    (enrollment) => enrollment.status === "completed",
  ).length;
  const averageProgress =
    totalEnrollments > 0
      ? currentEnrollments.reduce(
          (sum, enrollment) => sum + (enrollment.progressPercent ?? 0),
          0,
        ) / totalEnrollments
      : 0;

  const dashboardStats = {
    totalEnrollments: stats?.totalEnrollments ?? totalEnrollments,
    completedModules: stats?.completedModules ?? completedEnrollments,
    averageProgress: stats?.averageProgress ?? averageProgress,
  };
  const featureTiles = [
    {
      icon: "🧭",
      title: "Learning Pathways",
      description: "Follow curated paths tailored to your goals.",
      href: "/pathways",
    },
    {
      icon: "📚",
      title: "Browse Modules",
      description: "Search and explore all available modules.",
      href: "/search",
    },
    {
      icon: "🤖",
      title: "AI Tutor",
      description: "Get on-demand explanations and guidance.",
      href: "/ai-tutor",
    },
    {
      icon: "📝",
      title: "Assessments",
      description: "Check your understanding with quizzes and exams.",
      href: "/assessments",
    },
    {
      icon: "📈",
      title: "Analytics",
      description: "Track your progress and learning trends.",
      href: "/analytics",
    },
    {
      icon: "🛒",
      title: "Marketplace",
      description: "Discover new simulations and learning content.",
      href: "/marketplace",
    },
  ];

  return (
    <Box className="p-6">
      <div className="max-w-6xl mx-auto">
        <PageHeader
          title={`Welcome back, ${userName}`}
          description={userEmail || undefined}
        />

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8 min-w-[260px]">
          <StatCard
            title="Enrollments"
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
            value={`${dashboardStats.averageProgress.toFixed(0)}%`}
            color="orange"
          />
        </div>

        <ContentGenerationNav />

        <section className="mb-12">
          <h2 className={cn(textStyles.h2, "mb-2")}>
            Design your learning journey
          </h2>
          <p className={cn(textStyles.muted, "mb-4")}>
            Jump into the core areas of TutorPutor just like the Design section
            in AEP.
          </p>
          <div className="grid grid-cols-1 gap-4">
            {featureTiles.map((tile) => (
              <Link
                key={tile.title}
                to={tile.href}
                className={cn(
                  cardStyles.interactive,
                  cardStyles.padded,
                  "flex items-start gap-4",
                )}
              >
                <span className="text-3xl mt-1">{tile.icon}</span>
                <div>
                  <h3 className={cn(textStyles.h3, "mb-1")}>{tile.title}</h3>
                  <p className={textStyles.muted}>{tile.description}</p>
                </div>
              </Link>
            ))}
          </div>
        </section>

        <section className="mb-12 mt-8">
          <h2 className={cn(textStyles.h2, "mb-4")}>Current Enrollments</h2>
          {currentEnrollments.length === 0 ? (
            <Card padded={false} className={cn(cardStyles.base, "text-center")}>
              <div className={cn("p-6", textStyles.muted)}>
                No active enrollments. Start learning by exploring modules
                below!
              </div>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {currentEnrollments.map((enrollment) => (
                <EnrollmentCard
                  key={enrollment.id}
                  enrollment={enrollment}
                  module={recommendedModules.find(
                    (m) => m.id === enrollment.moduleId,
                  )}
                />
              ))}
            </div>
          )}
        </section>

        <section>
          <h2 className={cn(textStyles.h2, "mb-4")}>Recommended Modules</h2>
          {recommendedModules.length === 0 ? (
            <Card padded={false} className={cn(cardStyles.base, "text-center")}>
              <div className={cn("p-6", textStyles.muted)}>
                No recommended modules yet. Explore pathways or modules to get
                personalized suggestions.
              </div>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {recommendedModules.map((module) => (
                <ModuleCard
                  key={module.id}
                  module={module}
                  onClick={() => navigate(`/modules/${module.slug}`)}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </Box>
  );
}

function EnrollmentCard({
  enrollment,
  module,
}: {
  enrollment: Enrollment;
  module?: ModuleSummary;
}) {
  return (
    <Card
      padded={false}
      className={cn(cardStyles.base, "hover:shadow-md transition-shadow")}
    >
      <div className="p-6">
        <h3 className={cn(textStyles.h3, "mb-2")}>
          {module?.title || "Module"}
        </h3>
        <div className="mb-4">
          <div className={cn("flex justify-between mb-2", textStyles.small)}>
            <span>Progress</span>
            <span>{enrollment.progressPercent.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
            <div
              className="bg-blue-600 h-2 rounded-full transition-all"
              style={{ width: `${enrollment.progressPercent}%` }}
            />
          </div>
        </div>
        <div
          className={cn(textStyles.small, "text-gray-500 dark:text-gray-400")}
        >
          Status: <span className="font-medium">{enrollment.status}</span>
        </div>
        {enrollment.timeSpentSeconds > 0 && (
          <div
            className={cn(
              textStyles.small,
              "text-gray-500 dark:text-gray-400 mt-1",
            )}
          >
            Time spent: {Math.floor(enrollment.timeSpentSeconds / 60)} minutes
          </div>
        )}
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
      className={cn(cardStyles.interactive, "cursor-pointer")}
      onClick={onClick}
    >
      <div className="p-6">
        <div className="flex items-start justify-between mb-2">
          <h3 className={textStyles.h3}>{module.title}</h3>
          <span className={badgeStyles.info}>{module.domain}</span>
        </div>
        <p className={cn(textStyles.small, "mb-4")}>{module.tags.join(", ")}</p>
        <div
          className={cn("flex items-center justify-between", textStyles.small)}
        >
          <span className="text-gray-500 dark:text-gray-400">
            {module.estimatedTimeMinutes} min
          </span>
          <span className="text-gray-500 dark:text-gray-400 capitalize">
            {module.difficulty}
          </span>
        </div>
        {module.progressPercent !== undefined && (
          <div className="mt-4">
            <div className="flex justify-between text-xs text-gray-600 dark:text-gray-400 mb-1">
              <span>Your progress</span>
              <span>{module.progressPercent.toFixed(1)}%</span>
            </div>
            <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-1.5">
              <div
                className="bg-green-600 h-1.5 rounded-full"
                style={{ width: `${module.progressPercent}%` }}
              />
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
