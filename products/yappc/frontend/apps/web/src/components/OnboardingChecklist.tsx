import { ChevronDown as ExpandMoreIcon, CheckCircle, Circle as RadioButtonUnchecked, Play as PlayArrow, RefreshCw as Refresh, Trophy as EmojiEvents, Rocket as RocketLaunch, Zap as Bolt, GraduationCap as School } from 'lucide-react';
// Core UI components from @ghatana/yappc-ui
import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  IconButton,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
  Chip,
  Divider,
  InteractiveList as List,
} from '@ghatana/ui';
import { Collapse, ListItemButton } from '@ghatana/ui';

// Additional UI components
import { Alert, Button, LinearProgress as ProgressBar } from '@ghatana/ui';
import React, { useState, useEffect, useCallback } from 'react';

/**
 * Onboarding step definition
 */
interface OnboardingStep {
  id: string;
  title: string;
  description: string;
  category: 'setup' | 'features' | 'advanced';
  estimatedTime: string;
  actionUrl?: string;
  completed?: boolean;
}

/**
 * Onboarding checklist data
 */
const onboardingSteps: OnboardingStep[] = [
  // Setup Steps
  {
    id: 'install',
    title: 'Install YAPPC Canvas',
    description: 'Add the canvas package to your project using pnpm or npm',
    category: 'setup',
    estimatedTime: '2 min',
  },
  {
    id: 'create-canvas',
    title: 'Create Your First Canvas',
    description: 'Set up a basic canvas with nodes and edges',
    category: 'setup',
    estimatedTime: '5 min',
    actionUrl: '/canvas/quick-start',
  },
  {
    id: 'explore-demo',
    title: 'Explore Interactive Demo',
    description: 'Try the comprehensive demo with guided tours',
    category: 'setup',
    estimatedTime: '10 min',
    actionUrl: '/canvas/demo',
  },

  // Feature Steps
  {
    id: 'collaboration',
    title: 'Enable Real-Time Collaboration',
    description: 'Set up multi-user editing with presence awareness',
    category: 'features',
    estimatedTime: '10 min',
    actionUrl: '/canvas/demo?section=0',
  },
  {
    id: 'export',
    title: 'Configure Export Options',
    description: 'Enable PNG, SVG, PDF, and JSON exports',
    category: 'features',
    estimatedTime: '5 min',
    actionUrl: '/canvas/demo?section=1',
  },
  {
    id: 'performance',
    title: 'Optimize Performance',
    description: 'Enable virtual rendering for large diagrams',
    category: 'features',
    estimatedTime: '5 min',
    actionUrl: '/canvas/demo?section=2',
  },
  {
    id: 'security',
    title: 'Configure Security',
    description: 'Set up encryption and access control',
    category: 'features',
    estimatedTime: '10 min',
    actionUrl: '/canvas/demo?section=3',
  },

  // Advanced Steps
  {
    id: 'monitoring',
    title: 'Set Up Monitoring',
    description: 'Configure health checks and metrics',
    category: 'advanced',
    estimatedTime: '15 min',
    actionUrl: '/canvas/demo?section=4',
  },
  {
    id: 'custom-components',
    title: 'Build Custom Components',
    description: 'Create custom node and edge types',
    category: 'advanced',
    estimatedTime: '20 min',
  },
  {
    id: 'plugins',
    title: 'Develop Plugins',
    description: 'Extend canvas with custom functionality',
    category: 'advanced',
    estimatedTime: '30 min',
  },
  {
    id: 'deployment',
    title: 'Deploy to Production',
    description: 'Set up blue/green deployment with feature flags',
    category: 'advanced',
    estimatedTime: '20 min',
    actionUrl: '/canvas/demo?section=6',
  },
];

/**
 * Category configuration
 */
const categories = {
  setup: { label: 'Getting Started', color: '#2196f3', icon: <RocketLaunch size={16} /> },
  features: { label: 'Core Features', color: '#4caf50', icon: <Bolt size={16} /> },
  advanced: { label: 'Advanced Topics', color: '#ff9800', icon: <School size={16} /> },
};

/**
 * Onboarding checklist component
 */
export default function OnboardingChecklist() {
  const [completedSteps, setCompletedSteps] = useState<Set<string>>(new Set());
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(['setup'])
  );

  // Load completed steps from localStorage
  useEffect(() => {
    const stored = localStorage.getItem('canvas-onboarding-completed');
    if (stored) {
      try {
        setCompletedSteps(new Set(JSON.parse(stored)));
      } catch (error) {
        console.error('Failed to load onboarding progress', error);
      }
    }
  }, []);

  // Save completed steps to localStorage
  const saveProgress = useCallback((steps: Set<string>) => {
    localStorage.setItem('canvas-onboarding-completed', JSON.stringify(Array.from(steps)));
  }, []);

  // Toggle step completion
  const toggleStep = useCallback(
    (stepId: string) => {
      const newCompleted = new Set(completedSteps);
      if (newCompleted.has(stepId)) {
        newCompleted.delete(stepId);
      } else {
        newCompleted.add(stepId);
      }
      setCompletedSteps(newCompleted);
      saveProgress(newCompleted);
    },
    [completedSteps, saveProgress]
  );

  // Toggle category expansion
  const toggleCategory = useCallback((category: string) => {
    setExpandedCategories((prev) => {
      const newExpanded = new Set(prev);
      if (newExpanded.has(category)) {
        newExpanded.delete(category);
      } else {
        newExpanded.add(category);
      }
      return newExpanded;
    });
  }, []);

  // Reset progress
  const resetProgress = useCallback(() => {
    setCompletedSteps(new Set());
    localStorage.removeItem('canvas-onboarding-completed');
  }, []);

  // Calculate progress
  const totalSteps = onboardingSteps.length;
  const completedCount = completedSteps.size;
  const progressPercent = (completedCount / totalSteps) * 100;

  // Group steps by category
  const stepsByCategory = onboardingSteps.reduce((acc, step) => {
    if (!acc[step.category]) {
      acc[step.category] = [];
    }
    acc[step.category].push(step);
    return acc;
  }, {} as Record<string, OnboardingStep[]>);

  // Check if all steps completed
  const allCompleted = completedCount === totalSteps;

  return (
    <Card elevation={3}>
      <CardHeader
        title={
          <Box className="flex items-center gap-2">
            <Typography variant="h5">Canvas Onboarding Checklist</Typography>
            {allCompleted && <EmojiEvents className="text-[gold]" />}
          </Box>
        }
        subheader={
          <Box className="mt-2">
            <Box className="flex justify-between mb-2">
              <Typography variant="body2" color="text.secondary">
                {completedCount} of {totalSteps} steps completed
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {Math.round(progressPercent)}%
              </Typography>
            </Box>
            <div style={{ marginTop: 4 }}>
              <ProgressBar percentage={progressPercent} height="h-2" />
            </div>
          </Box>
        }
        action={
          <Button
            startIcon={<Refresh />}
            onClick={resetProgress}
            size="small"
            disabled={completedCount === 0}
          >
            Reset
          </Button>
        }
      />

      <CardContent>
        {allCompleted && (
          <Alert severity="success" className="mb-6" icon={<EmojiEvents />}>
            <Typography variant="h6" gutterBottom>
              Congratulations! You've completed the onboarding.
            </Typography>
            <Typography variant="body2">
              You're now ready to build amazing canvas applications. Check out the{' '}
              <a href="/canvas/demo">interactive demo</a> for advanced features, or dive into the{' '}
              <a href="/docs">documentation</a> for detailed guides.
            </Typography>
          </Alert>
        )}

        {(['setup', 'features', 'advanced'] as const).map((category) => {
          const categorySteps = stepsByCategory[category] || [];
          const categoryCompleted = categorySteps.filter((step) =>
            completedSteps.has(step.id)
          ).length;
          const isExpanded = expandedCategories.has(category);
          const categoryConfig = categories[category];

          return (
            <Box key={category} className="mb-4">
              <Box
                className="flex items-center cursor-pointer p-2 rounded hover:bg-gray-100 hover:dark:bg-gray-800"
                onClick={() => toggleCategory(category)}
              >
                <Box className="flex items-center grow gap-2">
                  <Typography variant="h6" className="flex items-center gap-2">
                    {categoryConfig.icon}
                    {categoryConfig.label}
                  </Typography>
                  <Chip
                    label={`${categoryCompleted}/${categorySteps.length}`}
                    size="small"
                    color={categoryCompleted === categorySteps.length ? 'success' : 'default'}
                  />
                </Box>
                <IconButton
                  size="small"
                  className="transition-all duration-300" style={{ transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
                >
                  <ExpandMoreIcon />
                </IconButton>
              </Box>

              <Collapse in={isExpanded}>
                <List dense className="pl-4">
                  {categorySteps.map((step) => {
                    const isCompleted = completedSteps.has(step.id);
                    return (
                      <ListItem
                        key={step.id}
                        disablePadding
                        secondaryAction={
                          step.actionUrl && (
                            <IconButton
                              edge="end"
                              size="small"
                              onClick={(e) => {
                                e.stopPropagation();
                                window.location.href = step.actionUrl!;
                              }}
                              title="Go to guide"
                            >
                              <PlayArrow />
                            </IconButton>
                          )
                        }
                      >
                        <ListItemButton onClick={() => toggleStep(step.id)}>
                          <ListItemIcon>
                            <Checkbox
                              edge="start"
                              checked={isCompleted}
                              tabIndex={-1}
                              disableRipple
                              icon={<RadioButtonUnchecked />}
                              checkedIcon={<CheckCircle color="success" />}
                            />
                          </ListItemIcon>
                          <ListItemText
                            primary={
                              <Box className="flex items-center gap-2">
                                <Typography
                                  style={{ textDecoration: isCompleted ? 'line-through' : 'none', color: isCompleted ? 'text.secondary' : 'text.primary' }}
                                >
                                  {step.title}
                                </Typography>
                                <Chip label={step.estimatedTime} size="small" variant="outlined" />
                              </Box>
                            }
                            secondary={step.description}
                          />
                        </ListItemButton>
                      </ListItem>
                    );
                  })}
                </List>
              </Collapse>
              {category !== 'advanced' && <Divider className="my-2" />}
            </Box>
          );
        })}

        <Box className="mt-6 p-4 rounded bg-gray-100 dark:bg-gray-800">
          <Typography variant="body2" color="text.secondary">
            💡 <strong>Tip:</strong> You can complete these steps in any order. Click on the play
            icon to jump to relevant guides and examples.
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
}
