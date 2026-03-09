/**
 * Bootstrap Complete Page
 *
 * @description Success confirmation page shown after completing the bootstrapping
 * process. Shows project summary and provides navigation to next steps.
 *
 * @doc.type page
 * @doc.purpose Bootstrapping completion confirmation
 * @doc.layer page
 * @doc.phase bootstrapping
 */

import React, { useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import {
  CheckCircle2,
  ArrowRight,
  Sparkles,
  FileText,
  Code2,
  Users,
  Database,
  Settings,
  Layers,
  Download,
  Share2,
  Rocket,
  Home,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';

import { bootstrapSessionAtom } from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface ProjectStat {
  icon: React.ElementType;
  label: string;
  value: string | number;
  color: string;
}

interface NextStep {
  id: string;
  title: string;
  description: string;
  icon: React.ElementType;
  route: string;
  primary?: boolean;
}

// =============================================================================
// Animation Variants
// =============================================================================

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
      delayChildren: 0.2,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

const successIconVariants = {
  hidden: { scale: 0, rotate: -180 },
  visible: {
    scale: 1,
    rotate: 0,
    transition: {
      type: 'spring' as const,
      stiffness: 200,
      damping: 15,
    },
  },
};

// =============================================================================
// Project Stats Component
// =============================================================================

interface ProjectStatsProps {
  stats: ProjectStat[];
}

const ProjectStats: React.FC<ProjectStatsProps> = ({ stats }) => {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
      {stats.map((stat) => {
        const Icon = stat.icon;
        return (
          <motion.div
            key={stat.label}
            variants={itemVariants}
            className="rounded-lg border border-zinc-800 bg-zinc-900/50 p-4 text-center"
          >
            <div
              className={cn(
                'mx-auto flex h-10 w-10 items-center justify-center rounded-full',
                stat.color
              )}
            >
              <Icon className="h-5 w-5" />
            </div>
            <p className="mt-2 text-2xl font-bold text-zinc-100">{stat.value}</p>
            <p className="text-xs text-zinc-500">{stat.label}</p>
          </motion.div>
        );
      })}
    </div>
  );
};

// =============================================================================
// Extracted Items Component
// =============================================================================

interface ExtractedItemsProps {
  title: string;
  items: string[];
  icon: React.ElementType;
  maxShow?: number;
}

const ExtractedItems: React.FC<ExtractedItemsProps> = ({
  title,
  items,
  icon: Icon,
  maxShow = 5,
}) => {
  const displayItems = items.slice(0, maxShow);
  const remaining = items.length - maxShow;

  return (
    <motion.div
      variants={itemVariants}
      className="rounded-lg border border-zinc-800 bg-zinc-900/50 p-4"
    >
      <div className="mb-3 flex items-center gap-2">
        <Icon className="h-4 w-4 text-zinc-400" />
        <h3 className="font-medium text-zinc-200">{title}</h3>
        <span className="ml-auto rounded-full bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300">
          {items.length}
        </span>
      </div>
      <ul className="space-y-2">
        {displayItems.map((item, index) => (
          <li
            key={index}
            className="flex items-center gap-2 text-sm text-zinc-400"
          >
            <CheckCircle2 className="h-3.5 w-3.5 text-success-400" />
            <span className="truncate">{item}</span>
          </li>
        ))}
      </ul>
      {remaining > 0 && (
        <p className="mt-2 text-xs text-zinc-500">+{remaining} more</p>
      )}
    </motion.div>
  );
};

// =============================================================================
// Next Steps Component
// =============================================================================

interface NextStepsProps {
  steps: NextStep[];
}

const NextSteps: React.FC<NextStepsProps> = ({ steps }) => {
  const navigate = useNavigate();

  return (
    <motion.div variants={itemVariants} className="space-y-3">
      <h3 className="mb-4 text-lg font-semibold text-zinc-100">
        Continue Your Journey
      </h3>
      {steps.map((step) => {
        const Icon = step.icon;
        return (
          <motion.div
            key={step.id}
            whileHover={{ x: 4 }}
            className={cn(
              'flex cursor-pointer items-center gap-4 rounded-lg border p-4 transition-colors',
              step.primary
                ? 'border-primary-500/50 bg-primary-500/10 hover:bg-primary-500/20'
                : 'border-zinc-800 bg-zinc-900/50 hover:bg-zinc-900'
            )}
            onClick={() => navigate(step.route)}
          >
            <div
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-lg',
                step.primary ? 'bg-primary-500/20' : 'bg-zinc-800'
              )}
            >
              <Icon
                className={cn(
                  'h-5 w-5',
                  step.primary ? 'text-primary-400' : 'text-zinc-400'
                )}
              />
            </div>
            <div className="flex-1">
              <h4
                className={cn(
                  'font-medium',
                  step.primary ? 'text-primary-300' : 'text-zinc-200'
                )}
              >
                {step.title}
              </h4>
              <p className="text-sm text-zinc-500">{step.description}</p>
            </div>
            <ArrowRight
              className={cn(
                'h-5 w-5',
                step.primary ? 'text-primary-400' : 'text-zinc-600'
              )}
            />
          </motion.div>
        );
      })}
    </motion.div>
  );
};

// =============================================================================
// Confetti Component (Simple CSS-based)
// =============================================================================

const SimpleConfetti: React.FC<{ active: boolean }> = ({ active }) => {
  if (!active) return null;

  return (
    <div className="pointer-events-none fixed inset-0 overflow-hidden">
      {Array.from({ length: 50 }).map((_, i) => (
        <motion.div
          key={i}
          className="absolute h-3 w-3 rounded-sm"
          style={{
            backgroundColor: ['#3b82f6', '#8b5cf6', '#22c55e', '#f59e0b', '#ef4444'][i % 5],
            left: `${Math.random() * 100}%`,
          }}
          initial={{ y: -20, opacity: 1 }}
          animate={{
            y: window.innerHeight + 20,
            x: (Math.random() - 0.5) * 200,
            rotate: Math.random() * 720,
            opacity: 0,
          }}
          transition={{
            duration: 2 + Math.random() * 2,
            delay: Math.random() * 0.5,
            ease: 'easeOut',
          }}
        />
      ))}
    </div>
  );
};

// =============================================================================
// Main Page Component
// =============================================================================

const BootstrapCompletePage: React.FC = () => {
  const navigate = useNavigate();
  const { projectId, sessionId } = useParams<{
    projectId: string;
    sessionId: string;
  }>();

  const session = useAtomValue(bootstrapSessionAtom);

  const [showConfetti, setShowConfetti] = React.useState(true);

  // Hide confetti after 3 seconds
  useEffect(() => {
    const timer = setTimeout(() => setShowConfetti(false), 3000);
    return () => clearTimeout(timer);
  }, []);

  // Project statistics derived from session
  const projectStats: ProjectStat[] = useMemo(
    () => [
      {
        icon: FileText,
        label: 'Requirements',
        value: 12,
        color: 'bg-blue-500/20 text-blue-400',
      },
      {
        icon: Layers,
        label: 'Features',
        value: session?.features?.length || 8,
        color: 'bg-purple-500/20 text-purple-400',
      },
      {
        icon: Code2,
        label: 'Tech Stack',
        value: session?.techStack?.length || 5,
        color: 'bg-green-500/20 text-green-400',
      },
      {
        icon: Database,
        label: 'Data Models',
        value: 4,
        color: 'bg-amber-500/20 text-amber-400',
      },
    ],
    [session]
  );

  // Extracted items for display
  const features = useMemo(
    () =>
      session?.features?.map((f: unknown) => f.name || f) || [
        'User Authentication',
        'Dashboard Analytics',
        'Real-time Notifications',
        'Data Export',
        'Team Collaboration',
      ],
    [session]
  );

  const techStack = useMemo(
    () => {
      if (!session?.techStack) {
        return ['Next.js', 'TypeScript', 'Tailwind CSS', 'PostgreSQL', 'Prisma'];
      }
      return session.techStack.map((item: unknown) => 
        typeof item === 'string' ? item : item.name || String(item)
      );
    },
    [session]
  );

  // Next steps configuration
  const nextSteps: NextStep[] = useMemo(
    () => [
      {
        id: 'setup',
        title: 'Project Setup',
        description: 'Configure your development environment and dependencies',
        icon: Settings,
        route: ROUTES.setup.root(projectId || ''),
        primary: true,
      },
      {
        id: 'team',
        title: 'Invite Team',
        description: 'Add collaborators and assign roles',
        icon: Users,
        route: ROUTES.team.root(projectId || ''),
      },
      {
        id: 'development',
        title: 'Start Building',
        description: 'Jump into development with AI assistance',
        icon: Code2,
        route: ROUTES.development.root(projectId || ''),
      },
      {
        id: 'export',
        title: 'Export Project',
        description: 'Download project documentation and specs',
        icon: Download,
        route: ROUTES.bootstrap.session(projectId || '', sessionId || ''),
      },
    ],
    [projectId, sessionId]
  );

  const projectName = session?.projectName || 'Your Project';

  return (
    <div className="min-h-screen bg-zinc-950">
      {/* Confetti */}
      <SimpleConfetti active={showConfetti} />

      <div className="mx-auto max-w-4xl px-6 py-12">
        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          className="space-y-8"
        >
          {/* Success Header */}
          <motion.div variants={itemVariants} className="text-center">
            <motion.div
              variants={successIconVariants}
              className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-success-400 to-success-600 shadow-lg shadow-success-500/30"
            >
              <CheckCircle2 className="h-10 w-10 text-white" />
            </motion.div>

            <h1 className="text-3xl font-bold text-zinc-100">
              Project Bootstrapped! 🎉
            </h1>
            <p className="mx-auto mt-2 max-w-lg text-zinc-400">
              <span className="font-medium text-primary-400">{projectName}</span>{' '}
              has been successfully set up. AI has analyzed your requirements and
              created a comprehensive project foundation.
            </p>
          </motion.div>

          {/* Project Stats */}
          <motion.div variants={itemVariants}>
            <ProjectStats stats={projectStats} />
          </motion.div>

          {/* Extracted Content */}
          <motion.div
            variants={itemVariants}
            className="grid gap-4 sm:grid-cols-2"
          >
            <ExtractedItems
              title="Key Features"
              items={features}
              icon={Sparkles}
            />
            <ExtractedItems
              title="Tech Stack"
              items={techStack}
              icon={Code2}
            />
          </motion.div>

          {/* AI Summary */}
          <motion.div
            variants={itemVariants}
            className="rounded-lg border border-primary-500/30 bg-primary-500/5 p-6"
          >
            <div className="flex items-start gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-500/20">
                <Sparkles className="h-5 w-5 text-primary-400" />
              </div>
              <div>
                <h3 className="font-semibold text-zinc-100">AI Analysis Summary</h3>
                <p className="mt-2 text-sm leading-relaxed text-zinc-400">
                  Based on your requirements, I've identified this as a{' '}
                  <span className="text-primary-300">
                    full-stack web application
                  </span>{' '}
                  with focus on user engagement and data analytics. The
                  recommended architecture includes a React frontend with
                  server-side rendering, a RESTful API backend, and a relational
                  database for structured data storage. Key considerations
                  include scalability for future growth and robust authentication.
                </p>
              </div>
            </div>
          </motion.div>

          {/* Next Steps */}
          <NextSteps steps={nextSteps} />

          {/* Footer Actions */}
          <motion.div
            variants={itemVariants}
            className="flex items-center justify-between border-t border-zinc-800 pt-6"
          >
            <Button
              variant="ghost"
              onClick={() => navigate(ROUTES.DASHBOARD)}
              className="gap-2"
            >
              <Home className="h-4 w-4" />
              Go to Dashboard
            </Button>
            <div className="flex gap-3">
              <Button
                variant="outline"
                onClick={() => {
                  // NOTE: Implement share functionality
                  navigator.clipboard.writeText(window.location.href);
                }}
                className="gap-2"
              >
                <Share2 className="h-4 w-4" />
                Share
              </Button>
              <Button
                onClick={() =>
                  navigate(ROUTES.setup.root(projectId || ''))
                }
                className="gap-2"
              >
                <Rocket className="h-4 w-4" />
                Start Setup
              </Button>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
};

export default BootstrapCompletePage;
