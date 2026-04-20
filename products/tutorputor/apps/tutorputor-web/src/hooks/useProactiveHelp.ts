/**
 * Proactive Help Detection Hook
 *
 * Detects struggle patterns and triggers proactive AI tutor assistance.
 * Monitors: time on task, error patterns, inactivity, repeated actions.
 *
 * @doc.type hook
 * @doc.purpose Detect learner struggle patterns for proactive AI help
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useState, useEffect, useRef, useCallback } from "react";

interface StrugglePattern {
  type: "long_time_on_task" | "repeated_errors" | "inactivity" | "multiple_attempts";
  severity: "low" | "medium" | "high";
  context: string;
  timestamp: number;
}

interface ProactiveHelpState {
  shouldShowHelp: boolean;
  pattern: StrugglePattern | null;
  suggestedAction: string;
}

export interface ProactiveHelpController extends ProactiveHelpState {
  dismissHelp: () => void;
  acceptHelp: () => void;
  recordActivity: () => void;
  recordError: () => void;
  recordAttempt: () => void;
}

interface UseProactiveHelpOptions {
  enabled?: boolean;
  taskType?: "lesson" | "quiz" | "exercise" | "general";
  moduleId?: string;
  lessonId?: string;
}

// Thresholds for struggle detection
const THRESHOLDS = {
  // Time thresholds (in milliseconds)
  LONG_TIME_ON_TASK: 5 * 60 * 1000, // 5 minutes stuck
  INACTIVITY_TIMEOUT: 2 * 60 * 1000, // 2 minutes no interaction
  REPEATED_ERROR_WINDOW: 3 * 60 * 1000, // 3 minutes for error counting

  // Error thresholds
  MAX_ERRORS_BEFORE_HELP: 3,
  MAX_ATTEMPTS_BEFORE_HELP: 5,

  // Cooldown between proactive suggestions
  COOLDOWN_PERIOD: 10 * 60 * 1000, // 10 minutes
};

const SUGGESTIONS: Record<string, string[]> = {
  lesson: [
    "Would you like me to explain this concept differently?",
    "Need help understanding this lesson? I can break it down for you.",
    "Stuck on this topic? Let me provide some additional examples.",
  ],
  quiz: [
    "Having trouble with this question? I can give you a hint.",
    "Would you like me to explain the concept this question is testing?",
    "Need help? I can walk you through the solution approach.",
  ],
  exercise: [
    "Struggling with this exercise? I can guide you step-by-step.",
    "Need help completing this task? Let me provide some hints.",
    "Having difficulty? I can explain the key concepts needed here.",
  ],
  general: [
    "I noticed you've been here a while. Need any help?",
    "Looks like you might be stuck. Can I assist you?",
    "Would you like some guidance on what to do next?",
  ],
};

export function useProactiveHelp(options: UseProactiveHelpOptions = {}): ProactiveHelpController {
  const { enabled = true, taskType = "general", moduleId, lessonId } = options;

  const [state, setState] = useState<ProactiveHelpState>({
    shouldShowHelp: false,
    pattern: null,
    suggestedAction: "",
  });

  // Refs for tracking state without re-renders
  const activityTimestamps = useRef<number[]>([]);
  const errorTimestamps = useRef<number[]>([]);
  const taskStartTime = useRef<number>(Date.now());
  const lastInteractionTime = useRef<number>(Date.now());
  const lastHelpTime = useRef<number>(0);
  const attempts = useRef<number>(0);
  const isDismissing = useRef<boolean>(false);

  // Reset tracking when module/lesson changes
  useEffect(() => {
    activityTimestamps.current = [];
    errorTimestamps.current = [];
    taskStartTime.current = Date.now();
    lastInteractionTime.current = Date.now();
    attempts.current = 0;
    isDismissing.current = false;

    setState({
      shouldShowHelp: false,
      pattern: null,
      suggestedAction: "",
    });
  }, [moduleId, lessonId]);

  // Track user activity
  const recordActivity = useCallback(() => {
    lastInteractionTime.current = Date.now();
    activityTimestamps.current.push(Date.now());

    // Clean old timestamps (> 5 minutes)
    const cutoff = Date.now() - 5 * 60 * 1000;
    activityTimestamps.current = activityTimestamps.current.filter(t => t > cutoff);
  }, []);

  // Track errors
  const recordError = useCallback(() => {
    errorTimestamps.current.push(Date.now());
    attempts.current += 1;

    // Clean old errors
    const cutoff = Date.now() - THRESHOLDS.REPEATED_ERROR_WINDOW;
    errorTimestamps.current = errorTimestamps.current.filter(t => t > cutoff);

    checkForStruggle();
  }, []);

  // Track attempt/try
  const recordAttempt = useCallback(() => {
    attempts.current += 1;
    recordActivity();

    if (attempts.current >= THRESHOLDS.MAX_ATTEMPTS_BEFORE_HELP) {
      checkForStruggle();
    }
  }, [recordActivity]);

  // Dismiss help
  const dismissHelp = useCallback(() => {
    isDismissing.current = true;
    lastHelpTime.current = Date.now();

    setState(prev => ({
      ...prev,
      shouldShowHelp: false,
    }));

    // Allow new help after cooldown
    setTimeout(() => {
      isDismissing.current = false;
    }, THRESHOLDS.COOLDOWN_PERIOD);
  }, []);

  // Accept help
  const acceptHelp = useCallback(() => {
    lastHelpTime.current = Date.now();
    attempts.current = 0;
    errorTimestamps.current = [];

    setState(prev => ({
      ...prev,
      shouldShowHelp: false,
    }));
  }, []);

  // Check for struggle patterns
  const checkForStruggle = useCallback(() => {
    if (!enabled || isDismissing.current) return;

    const now = Date.now();
    const timeOnTask = now - taskStartTime.current;
    const timeSinceLastInteraction = now - lastInteractionTime.current;
    const timeSinceLastHelp = now - lastHelpTime.current;

    // Check cooldown
    if (timeSinceLastHelp < THRESHOLDS.COOLDOWN_PERIOD && lastHelpTime.current > 0) {
      return;
    }

    let detectedPattern: StrugglePattern | null = null;

    // Pattern 1: Long time on task without success
    if (timeOnTask > THRESHOLDS.LONG_TIME_ON_TASK && attempts.current > 0) {
      const recentActivity = activityTimestamps.current.filter(
        t => t > now - THRESHOLDS.LONG_TIME_ON_TASK
      ).length;

      if (recentActivity < 5) { // Low interaction count suggests struggling
        detectedPattern = {
          type: "long_time_on_task",
          severity: timeOnTask > 10 * 60 * 1000 ? "high" : "medium",
          context: `Spent ${Math.round(timeOnTask / 60000)} minutes on task with low activity`,
          timestamp: now,
        };
      }
    }

    // Pattern 2: Repeated errors
    if (errorTimestamps.current.length >= THRESHOLDS.MAX_ERRORS_BEFORE_HELP) {
      detectedPattern = {
        type: "repeated_errors",
        severity: errorTimestamps.current.length >= 5 ? "high" : "medium",
        context: `${errorTimestamps.current.length} errors in the last ${THRESHOLDS.REPEATED_ERROR_WINDOW / 60000} minutes`,
        timestamp: now,
      };
    }

    // Pattern 3: Inactivity
    if (timeSinceLastInteraction > THRESHOLDS.INACTIVITY_TIMEOUT) {
      detectedPattern = {
        type: "inactivity",
        severity: timeSinceLastInteraction > 5 * 60 * 1000 ? "high" : "low",
        context: `No activity for ${Math.round(timeSinceLastInteraction / 60000)} minutes`,
        timestamp: now,
      };
    }

    // Pattern 4: Multiple attempts
    if (attempts.current >= THRESHOLDS.MAX_ATTEMPTS_BEFORE_HELP) {
      detectedPattern = {
        type: "multiple_attempts",
        severity: attempts.current >= 8 ? "high" : "medium",
        context: `${attempts.current} attempts made`,
        timestamp: now,
      };
    }

    // If pattern detected, trigger help
    if (detectedPattern) {
      const suggestions = SUGGESTIONS[taskType] || SUGGESTIONS.general;
      const suggestion = suggestions[Math.floor(Math.random() * suggestions.length)];

      setState({
        shouldShowHelp: true,
        pattern: detectedPattern,
        suggestedAction: suggestion,
      });

      // Log for analytics
      if (typeof window !== "undefined" && (window as unknown as Record<string, unknown>).gtag) {
        const gtag = (window as unknown as { gtag: (name: string, eventName: string, data: Record<string, unknown>) => void }).gtag;
        gtag("event", "proactive_help_triggered", {
          pattern_type: detectedPattern.type,
          severity: detectedPattern.severity,
          task_type: taskType,
        });
      }
    }
  }, [enabled, taskType]);

  // Periodic check for inactivity
  useEffect(() => {
    if (!enabled) return;

    const interval = setInterval(() => {
      checkForStruggle();
    }, 30000); // Check every 30 seconds

    return () => clearInterval(interval);
  }, [enabled, checkForStruggle]);

  // Attach global activity listeners
  useEffect(() => {
    if (!enabled) return;

    const events = ["mousedown", "keydown", "touchstart", "scroll"];

    const handleActivity = () => {
      recordActivity();
    };

    events.forEach(event => {
      document.addEventListener(event, handleActivity, { passive: true });
    });

    return () => {
      events.forEach(event => {
        document.removeEventListener(event, handleActivity);
      });
    };
  }, [enabled, recordActivity]);

  return {
    ...state,
    dismissHelp,
    acceptHelp,
    recordActivity,
    recordError,
    recordAttempt,
  };
}
