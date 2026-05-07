import { OnboardingFlow, type StarterModule } from "../components/OnboardingFlow";

const starterModules: StarterModule[] = [
  {
    assetId: "intro-to-motion",
    title: "Motion From Evidence",
    description: "Use a simple simulation to connect prediction, observation, and claim mastery.",
    difficulty: "beginner",
    estimatedMinutes: 18,
    matchScore: 0.92,
    matchReasons: ["visual-first", "simulation-first", "diagnostic-ready"],
  },
  {
    assetId: "cbm-foundations",
    title: "Confidence and Evidence",
    description: "Practice certainty-based marking before your first scored assessment.",
    difficulty: "beginner",
    estimatedMinutes: 12,
    matchScore: 0.84,
    matchReasons: ["assessment-ready", "mastery evidence"],
  },
];

export function OnboardingPage() {
  const handleComplete = (selectedModules: string[]) => {
    window.localStorage.setItem(
      "tutorputor.onboarding.selectedModules",
      JSON.stringify(selectedModules),
    );
    window.localStorage.setItem("tutorputor.onboarding.completed", "true");
  };

  const handleSkip = () => {
    window.localStorage.setItem("tutorputor.onboarding.skipped", "true");
  };

  return (
    <OnboardingFlow
      interests={[]}
      suggestedModules={starterModules}
      onComplete={handleComplete}
      onSkip={handleSkip}
    />
  );
}
