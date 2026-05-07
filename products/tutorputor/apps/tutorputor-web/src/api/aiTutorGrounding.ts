export interface AITutorGroundingPayload {
  moduleId: string;
  claimIds: string[];
  currentSimulationState: Record<string, unknown>;
  recentAttempts: Array<{
    attemptId: string;
    taskId?: string;
    correct?: boolean;
    confidence?: "low" | "medium" | "high";
    misconceptionId?: string;
  }>;
  misconceptions: string[];
  allowedHelpMode: "hint" | "explain" | "socratic";
}

export function buildAITutorGroundingPayload(
  overrides: Partial<AITutorGroundingPayload> = {},
): AITutorGroundingPayload {
  return {
    moduleId: overrides.moduleId ?? "",
    claimIds: overrides.claimIds ?? [],
    currentSimulationState: overrides.currentSimulationState ?? {
      source: "web",
      available: false,
    },
    recentAttempts:
      overrides.recentAttempts ??
      [],
    misconceptions: overrides.misconceptions ?? [],
    allowedHelpMode: overrides.allowedHelpMode ?? "socratic",
  };
}
