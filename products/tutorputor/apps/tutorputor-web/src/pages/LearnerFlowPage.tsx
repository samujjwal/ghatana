/**
 * LearnerFlowPage
 *
 * Implements the primary learner inquiry flow:
 *   Step 1 – Predict:       State prediction + confidence level
 *   Step 2 – Simulate:      Run the simulation
 *   Step 3 – Observe:       Review what happened
 *   Step 4 – Explain:       Write an explanation of the observation
 *   Step 5 – Feedback:      Receive AI-assisted feedback and remediation
 *   Step 6 – Prove Mastery: Answer a challenge question to confirm understanding
 *
 * The AI tutor operates invisibly throughout — it surfaces contextual hints
 * and feedback without requiring the learner to explicitly open a chat window.
 *
 * @doc.type component
 * @doc.purpose Guided scientific inquiry loop for learners
 * @doc.layer product
 * @doc.pattern Page
 */
import React, { useCallback, useReducer } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Box, Button, Card, Textarea } from "@/components/ui";
import { cardStyles, textStyles, cn } from "../theme";
import {
  FlaskConical,
  Eye,
  MessageSquare,
  Trophy,
  ChevronRight,
  ChevronLeft,
  CheckCircle,
  Lightbulb,
  Brain,
} from "lucide-react";

interface TutorPromptEventDetail {
  prompt: string;
  autoSend?: boolean;
  source?: string;
}

// ============================================================================
// Types
// ============================================================================

type FlowStep =
  | "predict"
  | "simulate"
  | "observe"
  | "explain"
  | "feedback"
  | "prove";

interface ConfidenceLevel {
  label: string;
  value: number;
  color: string;
}

interface SimulationConfig {
  id: string;
  title: string;
  description: string;
  domain: string;
  parameters: Record<string, string | number | boolean>;
}

interface SimulationResult {
  summary: string;
  details: string;
  keyObservations: string[];
  visualDataUrl?: string;
}

interface FeedbackResult {
  isCorrect: boolean;
  explanation: string;
  misconceptions: string[];
  hints: string[];
  remediationLinks: Array<{ label: string; href: string }>;
}

interface MasteryChallenge {
  question: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

interface FlowState {
  step: FlowStep;
  prediction: string;
  confidence: number;
  simulationResult: SimulationResult | null;
  explanation: string;
  feedback: FeedbackResult | null;
  selectedChallengeOption: number | null;
  masteryPassed: boolean;
}

type FlowAction =
  | { type: "SET_STEP"; step: FlowStep }
  | { type: "SET_PREDICTION"; prediction: string }
  | { type: "SET_CONFIDENCE"; confidence: number }
  | { type: "SET_SIMULATION_RESULT"; result: SimulationResult }
  | { type: "SET_EXPLANATION"; explanation: string }
  | { type: "SET_FEEDBACK"; feedback: FeedbackResult }
  | { type: "SELECT_CHALLENGE_OPTION"; index: number }
  | { type: "SET_MASTERY_PASSED"; passed: boolean };

// ============================================================================
// Constants
// ============================================================================

const STEPS: Array<{ id: FlowStep; label: string; icon: React.ElementType }> = [
  { id: "predict", label: "Predict", icon: Brain },
  { id: "simulate", label: "Simulate", icon: FlaskConical },
  { id: "observe", label: "Observe", icon: Eye },
  { id: "explain", label: "Explain", icon: MessageSquare },
  { id: "feedback", label: "Feedback", icon: Lightbulb },
  { id: "prove", label: "Prove", icon: Trophy },
];

const STEP_ORDER: FlowStep[] = [
  "predict",
  "simulate",
  "observe",
  "explain",
  "feedback",
  "prove",
];

const CONFIDENCE_LEVELS: ConfidenceLevel[] = [
  { label: "Just Guessing", value: 1, color: "bg-red-100 border-red-300 text-red-700" },
  { label: "Not Sure", value: 2, color: "bg-orange-100 border-orange-300 text-orange-700" },
  { label: "Somewhat Sure", value: 3, color: "bg-yellow-100 border-yellow-300 text-yellow-700" },
  { label: "Fairly Confident", value: 4, color: "bg-blue-100 border-blue-300 text-blue-700" },
  { label: "Very Confident", value: 5, color: "bg-green-100 border-green-300 text-green-700" },
];

// ============================================================================
// Reducer
// ============================================================================

function flowReducer(state: FlowState, action: FlowAction): FlowState {
  switch (action.type) {
    case "SET_STEP":
      return { ...state, step: action.step };
    case "SET_PREDICTION":
      return { ...state, prediction: action.prediction };
    case "SET_CONFIDENCE":
      return { ...state, confidence: action.confidence };
    case "SET_SIMULATION_RESULT":
      return { ...state, simulationResult: action.result };
    case "SET_EXPLANATION":
      return { ...state, explanation: action.explanation };
    case "SET_FEEDBACK":
      return { ...state, feedback: action.feedback };
    case "SELECT_CHALLENGE_OPTION":
      return { ...state, selectedChallengeOption: action.index };
    case "SET_MASTERY_PASSED":
      return { ...state, masteryPassed: action.passed };
    default:
      return state;
  }
}

const initialState: FlowState = {
  step: "predict",
  prediction: "",
  confidence: 0,
  simulationResult: null,
  explanation: "",
  feedback: null,
  selectedChallengeOption: null,
  masteryPassed: false,
};

// ============================================================================
// API helpers
// ============================================================================

async function fetchSimulation(simulationId: string): Promise<SimulationConfig> {
  const resp = await fetch(`/api/v1/simulations/${simulationId}`);
  if (!resp.ok) throw new Error("Failed to load simulation");
  return resp.json() as Promise<SimulationConfig>;
}

async function runSimulation(
  simulationId: string,
  prediction: string,
  confidence: number,
): Promise<SimulationResult> {
  const resp = await fetch(`/api/v1/simulations/${simulationId}/run`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ prediction, confidence }),
  });
  if (!resp.ok) throw new Error("Simulation run failed");
  return resp.json() as Promise<SimulationResult>;
}

async function evaluateExplanation(
  simulationId: string,
  explanation: string,
  prediction: string,
  simulationResult: SimulationResult,
): Promise<FeedbackResult> {
  const resp = await fetch(`/api/v1/simulations/${simulationId}/evaluate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ explanation, prediction, observedSummary: simulationResult.summary }),
  });
  if (!resp.ok) throw new Error("Evaluation failed");
  return resp.json() as Promise<FeedbackResult>;
}

async function fetchMasteryChallenge(simulationId: string): Promise<MasteryChallenge> {
  const resp = await fetch(`/api/v1/simulations/${simulationId}/mastery-challenge`);
  if (!resp.ok) throw new Error("Could not load mastery challenge");
  return resp.json() as Promise<MasteryChallenge>;
}

async function submitMastery(
  simulationId: string,
  selectedOption: number,
): Promise<{ passed: boolean; explanation: string }> {
  const resp = await fetch(`/api/v1/simulations/${simulationId}/mastery-challenge/submit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selectedOption }),
  });
  if (!resp.ok) throw new Error("Mastery submission failed");
  return resp.json() as Promise<{ passed: boolean; explanation: string }>;
}

// ============================================================================
// Step components
// ============================================================================

function PredictStep({
  prediction,
  confidence,
  onPredictionChange,
  onConfidenceChange,
  onNext,
}: {
  prediction: string;
  confidence: number;
  onPredictionChange: (v: string) => void;
  onConfidenceChange: (v: number) => void;
  onNext: () => void;
}): React.ReactElement {
  const canProceed = prediction.trim().length >= 10 && confidence > 0;

  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>What do you predict will happen?</h2>
        <p className={textStyles.muted}>
          Before running the simulation, write down what you think will happen and why.
        </p>
      </div>

      <div>
        <label className={cn(textStyles.label, "block mb-2")}>Your prediction</label>
        <Textarea
          value={prediction}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onPredictionChange(e.target.value)}
          placeholder="I predict that... because..."
          className="min-h-[120px] w-full"
        />
        <p className={cn(textStyles.xs, "mt-1")}>
          {prediction.length < 10 ? "Please write at least a few words." : ""}
        </p>
      </div>

      <div>
        <label className={cn(textStyles.label, "block mb-3")}>How confident are you?</label>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
          {CONFIDENCE_LEVELS.map((level) => (
            <button
              key={level.value}
              type="button"
              onClick={() => onConfidenceChange(level.value)}
              className={cn(
                "p-3 rounded-lg border-2 text-sm font-medium transition-all text-center",
                confidence === level.value
                  ? level.color + " border-2"
                  : "bg-white border-gray-200 text-gray-600 hover:border-gray-300",
              )}
            >
              {level.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex justify-end">
        <Button
          onClick={onNext}
          disabled={!canProceed}
          className="bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
        >
          Run Simulation <ChevronRight className="w-4 h-4 ml-1" />
        </Button>
      </div>
    </div>
  );
}

function SimulateStep({
  simulationId,
  prediction,
  confidence,
  isRunning,
  hasResult,
  onRun,
  onNext,
}: {
  simulationId: string;
  prediction: string;
  confidence: number;
  isRunning: boolean;
  hasResult: boolean;
  onRun: () => void;
  onNext: () => void;
}): React.ReactElement {
  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>Run the Simulation</h2>
        <p className={textStyles.muted}>
          Launch the simulation and watch what happens. Then we will compare with your prediction.
        </p>
      </div>

      <Card className={cn(cardStyles.base, "p-6")}>
        <div className="flex items-center gap-3 mb-4">
          <FlaskConical className="w-6 h-6 text-emerald-600" />
          <span className={textStyles.h4}>Simulation #{simulationId}</span>
        </div>

        <div className={cn(cardStyles.base, "p-3 mb-4 bg-indigo-50 border-indigo-100")}>
          <p className={cn(textStyles.xs, "font-medium text-indigo-700 mb-1")}>Your prediction:</p>
          <p className={cn(textStyles.small, "text-indigo-800")}>{prediction}</p>
          <p className={cn(textStyles.xs, "text-indigo-600 mt-1")}>
            Confidence: {CONFIDENCE_LEVELS.find((l) => l.value === confidence)?.label}
          </p>
        </div>

        {!hasResult ? (
          <Button
            onClick={onRun}
            disabled={isRunning}
            className="bg-emerald-600 hover:bg-emerald-700 text-white w-full"
          >
            {isRunning ? (
              <>
                <span className="animate-spin mr-2">⟳</span> Running...
              </>
            ) : (
              <>
                <FlaskConical className="w-4 h-4 mr-2" /> Launch Simulation
              </>
            )}
          </Button>
        ) : (
          <div className="flex items-center gap-2 text-emerald-700">
            <CheckCircle className="w-5 h-5" />
            <span className="font-medium">Simulation complete!</span>
          </div>
        )}
      </Card>

      <div className="flex justify-between">
        <div />
        <Button
          onClick={onNext}
          disabled={!hasResult}
          className="bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
        >
          View Results <ChevronRight className="w-4 h-4 ml-1" />
        </Button>
      </div>
    </div>
  );
}

function ObserveStep({
  result,
  onNext,
  onBack,
}: {
  result: SimulationResult;
  onNext: () => void;
  onBack: () => void;
}): React.ReactElement {
  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>What did you observe?</h2>
        <p className={textStyles.muted}>
          Review the simulation results carefully before explaining them.
        </p>
      </div>

      <Card className={cn(cardStyles.base, "p-6")}>
        <h3 className={cn(textStyles.h3, "mb-3")}>Simulation Results</h3>
        <p className={cn(textStyles.body, "mb-4")}>{result.summary}</p>

        {result.details && (
          <div className="mb-4">
            <p className={cn(textStyles.label, "mb-2")}>Details</p>
            <p className={cn(textStyles.small)}>{result.details}</p>
          </div>
        )}

        {result.keyObservations.length > 0 && (
          <div>
            <p className={cn(textStyles.label, "mb-2")}>Key observations</p>
            <ul className="space-y-2">
              {result.keyObservations.map((obs, idx) => (
                <li key={idx} className="flex items-start gap-2">
                  <CheckCircle className="w-4 h-4 text-emerald-500 mt-0.5 flex-shrink-0" />
                  <span className={textStyles.small}>{obs}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Card>

      <div className="flex justify-between">
        <Button variant="outline" onClick={onBack}>
          <ChevronLeft className="w-4 h-4 mr-1" /> Back
        </Button>
        <Button onClick={onNext} className="bg-indigo-600 hover:bg-indigo-700 text-white">
          Explain What Happened <ChevronRight className="w-4 h-4 ml-1" />
        </Button>
      </div>
    </div>
  );
}

function ExplainStep({
  explanation,
  onChange,
  onNext,
  onBack,
  isEvaluating,
}: {
  explanation: string;
  onChange: (v: string) => void;
  onNext: () => void;
  onBack: () => void;
  isEvaluating: boolean;
}): React.ReactElement {
  const canProceed = explanation.trim().length >= 20;

  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>Explain what happened</h2>
        <p className={textStyles.muted}>
          In your own words, explain why the simulation produced these results. Reference the key
          observations you noted.
        </p>
      </div>

      <div>
        <label className={cn(textStyles.label, "block mb-2")}>Your explanation</label>
        <Textarea
          value={explanation}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)}
          placeholder="The simulation showed... because... This is consistent with the principle that..."
          className="min-h-[140px] w-full"
        />
      </div>

      <div className={cn(cardStyles.base, "p-4 bg-amber-50 border-amber-100")}>
        <div className="flex gap-2">
          <Lightbulb className="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
          <p className={cn(textStyles.small, "text-amber-800")}>
            Try to connect what you observed to the underlying concept. Does this match, partly match,
            or contradict your original prediction?
          </p>
        </div>
      </div>

      <div className="flex justify-between">
        <Button variant="outline" onClick={onBack}>
          <ChevronLeft className="w-4 h-4 mr-1" /> Back
        </Button>
        <Button
          onClick={onNext}
          disabled={!canProceed || isEvaluating}
          className="bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
        >
          {isEvaluating ? (
            <>
              <span className="animate-spin mr-2">⟳</span> Evaluating...
            </>
          ) : (
            <>
              Get Feedback <ChevronRight className="w-4 h-4 ml-1" />
            </>
          )}
        </Button>
      </div>
    </div>
  );
}

function FeedbackStep({
  feedback,
  onNext,
  onBack,
}: {
  feedback: FeedbackResult;
  onNext: () => void;
  onBack: () => void;
}): React.ReactElement {
  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>Feedback</h2>
        <p className={textStyles.muted}>
          Here is how your explanation compares to the underlying concept.
        </p>
      </div>

      <Card
        className={cn(
          cardStyles.base,
          "p-6",
          feedback.isCorrect
            ? "bg-green-50 border-green-200"
            : "bg-amber-50 border-amber-200",
        )}
      >
        <div className="flex items-center gap-2 mb-3">
          {feedback.isCorrect ? (
            <>
              <CheckCircle className="w-6 h-6 text-green-600" />
              <span className="font-semibold text-green-800">Great explanation!</span>
            </>
          ) : (
            <>
              <Lightbulb className="w-6 h-6 text-amber-600" />
              <span className="font-semibold text-amber-800">Almost there — let us refine it</span>
            </>
          )}
        </div>
        <p className={cn(textStyles.body, feedback.isCorrect ? "text-green-800" : "text-amber-800")}>
          {feedback.explanation}
        </p>
      </Card>

      {feedback.misconceptions.length > 0 && (
        <Card className={cn(cardStyles.base, "p-5")}>
          <h3 className={cn(textStyles.h4, "mb-3 text-red-700")}>Common misconceptions to watch out for</h3>
          <ul className="space-y-2">
            {feedback.misconceptions.map((m, idx) => (
              <li key={idx} className={cn(textStyles.small, "text-red-700")}>
                • {m}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {feedback.hints.length > 0 && (
        <Card className={cn(cardStyles.base, "p-5")}>
          <h3 className={cn(textStyles.h4, "mb-3")}>Hints to deepen understanding</h3>
          <ul className="space-y-2">
            {feedback.hints.map((h, idx) => (
              <li key={idx} className={cn(textStyles.small)}>
                💡 {h}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {feedback.remediationLinks.length > 0 && (
        <div>
          <p className={cn(textStyles.label, "mb-2")}>Explore further</p>
          <div className="flex flex-wrap gap-2">
            {feedback.remediationLinks.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="text-sm text-indigo-600 hover:text-indigo-700 underline"
              >
                {link.label}
              </a>
            ))}
          </div>
        </div>
      )}

      <div className="flex justify-between">
        <Button variant="outline" onClick={onBack}>
          <ChevronLeft className="w-4 h-4 mr-1" /> Back
        </Button>
        <Button onClick={onNext} className="bg-indigo-600 hover:bg-indigo-700 text-white">
          Prove Mastery <ChevronRight className="w-4 h-4 ml-1" />
        </Button>
      </div>
    </div>
  );
}

function ProveMasteryStep({
  challenge,
  selectedOption,
  masteryPassed,
  onSelectOption,
  onSubmit,
  onBack,
  onFinish,
  isSubmitting,
}: {
  challenge: MasteryChallenge;
  selectedOption: number | null;
  masteryPassed: boolean;
  onSelectOption: (idx: number) => void;
  onSubmit: () => void;
  onBack: () => void;
  onFinish: () => void;
  isSubmitting: boolean;
}): React.ReactElement {
  return (
    <div className="space-y-6">
      <div>
        <h2 className={cn(textStyles.h2, "mb-2")}>Prove Your Mastery</h2>
        <p className={textStyles.muted}>
          Answer this question to confirm you have understood the core concept.
        </p>
      </div>

      {masteryPassed ? (
        <Card className={cn(cardStyles.base, "p-8 text-center bg-green-50 border-green-200")}>
          <Trophy className="w-12 h-12 text-amber-500 mx-auto mb-4" />
          <h3 className={cn(textStyles.h2, "text-green-800 mb-2")}>Mastery Achieved!</h3>
          <p className={cn(textStyles.body, "text-green-700 mb-6")}>
            {challenge.explanation}
          </p>
          <Button onClick={onFinish} className="bg-emerald-600 hover:bg-emerald-700 text-white">
            Back to Dashboard
          </Button>
        </Card>
      ) : (
        <>
          <Card className={cn(cardStyles.base, "p-6")}>
            <p className={cn(textStyles.body, "mb-5 font-medium")}>{challenge.question}</p>
            <div className="space-y-3">
              {challenge.options.map((option, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => onSelectOption(idx)}
                  className={cn(
                    "w-full text-left p-4 rounded-lg border-2 transition-all",
                    selectedOption === idx
                      ? "border-indigo-500 bg-indigo-50"
                      : "border-gray-200 hover:border-gray-300 bg-white",
                  )}
                >
                  <span className="font-medium mr-2">{String.fromCharCode(65 + idx)}.</span>
                  {option}
                </button>
              ))}
            </div>
          </Card>

          <div className="flex justify-between">
            <Button variant="outline" onClick={onBack}>
              <ChevronLeft className="w-4 h-4 mr-1" /> Back
            </Button>
            <Button
              onClick={onSubmit}
              disabled={selectedOption === null || isSubmitting}
              className="bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <span className="animate-spin mr-2">⟳</span> Checking...
                </>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4 mr-2" /> Submit Answer
                </>
              )}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

// ============================================================================
// StepIndicator
// ============================================================================

function StepIndicator({ currentStep }: { currentStep: FlowStep }): React.ReactElement {
  const currentIdx = STEP_ORDER.indexOf(currentStep);

  return (
    <div className="flex items-center gap-1 mb-8">
      {STEPS.map((step, idx) => {
        const isCompleted = idx < currentIdx;
        const isCurrent = idx === currentIdx;
        const Icon = step.icon;
        return (
          <React.Fragment key={step.id}>
            <div
              className={cn(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all",
                isCompleted
                  ? "bg-emerald-100 text-emerald-700"
                  : isCurrent
                    ? "bg-indigo-600 text-white"
                    : "bg-gray-100 text-gray-400",
              )}
            >
              {isCompleted ? (
                <CheckCircle className="w-3.5 h-3.5" />
              ) : (
                <Icon className="w-3.5 h-3.5" />
              )}
              <span className="hidden sm:inline">{step.label}</span>
            </div>
            {idx < STEPS.length - 1 && (
              <div
                className={cn(
                  "h-0.5 flex-1",
                  idx < currentIdx ? "bg-emerald-300" : "bg-gray-200",
                )}
              />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
}

// ============================================================================
// Main Page
// ============================================================================

export function LearnerFlowPage(): React.ReactElement {
  const { simulationId = "demo" } = useParams<{ simulationId: string }>();
  const navigate = useNavigate();
  const [state, dispatch] = useReducer(flowReducer, initialState);

  // Fetch simulation config
  const { data: simulation, isLoading: isLoadingSimulation } = useQuery({
    queryKey: ["simulation", simulationId],
    queryFn: () => fetchSimulation(simulationId),
  });

  // Fetch mastery challenge once we reach prove step
  const { data: challenge, isLoading: isLoadingChallenge } = useQuery({
    queryKey: ["mastery-challenge", simulationId],
    queryFn: () => fetchMasteryChallenge(simulationId),
    enabled: state.step === "prove",
  });

  // Run simulation
  const runMutation = useMutation({
    mutationFn: () => runSimulation(simulationId, state.prediction, state.confidence),
    onSuccess: (result) => {
      dispatch({ type: "SET_SIMULATION_RESULT", result });
    },
  });

  // Evaluate explanation
  const evaluateMutation = useMutation({
    mutationFn: () =>
      state.simulationResult
        ? evaluateExplanation(simulationId, state.explanation, state.prediction, state.simulationResult)
        : Promise.reject(new Error("No simulation result")),
    onSuccess: (feedback) => {
      dispatch({ type: "SET_FEEDBACK", feedback });
      dispatch({ type: "SET_STEP", step: "feedback" });
    },
  });

  // Submit mastery
  const masteryMutation = useMutation({
    mutationFn: () =>
      state.selectedChallengeOption !== null
        ? submitMastery(simulationId, state.selectedChallengeOption)
        : Promise.reject(new Error("No option selected")),
    onSuccess: (result) => {
      dispatch({ type: "SET_MASTERY_PASSED", passed: result.passed });
    },
  });

  const goToStep = useCallback((step: FlowStep) => {
    dispatch({ type: "SET_STEP", step });
  }, []);

  const requestTutorAssist = useCallback(
    (prompt: string) => {
      const detail: TutorPromptEventDetail = {
        prompt,
        autoSend: true,
        source: `learner-flow:${state.step}`,
      };
      window.dispatchEvent(new CustomEvent<TutorPromptEventDetail>("tutorputor:ai-tutor-prompt", { detail }));
    },
    [state.step],
  );

  if (isLoadingSimulation) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className={textStyles.muted}>Loading simulation...</div>
      </div>
    );
  }

  return (
    <Box className="p-6">
      <div className="max-w-3xl mx-auto">
        {/* Header */}
        <div className="mb-6">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className={cn(textStyles.small, "text-indigo-600 hover:text-indigo-700 mb-2 flex items-center gap-1")}
          >
            <ChevronLeft className="w-3.5 h-3.5" /> Back
          </button>
          <h1 className={textStyles.h1}>{simulation?.title ?? "Simulation"}</h1>
          {simulation?.description && (
            <p className={cn(textStyles.muted, "mt-1")}>{simulation.description}</p>
          )}
        </div>

        {/* Step progress */}
        <StepIndicator currentStep={state.step} />

        {/* Step content */}
        <Card className={cn(cardStyles.base, "p-6 md:p-8")}>
          <div className={cn(cardStyles.base, "p-3 mb-5 bg-slate-50 border-slate-200 flex items-center justify-between gap-3")}>
            <p className={cn(textStyles.small, "text-slate-700")}>
              AI is assisting in the background for this step. Ask for a targeted hint anytime.
            </p>
            <Button
              variant="outline"
              size="sm"
              className="border-slate-300 text-slate-700"
              onClick={() => {
                const stepPrompt: Record<FlowStep, string> = {
                  predict: "Give me one concise hint for making a strong prediction before I run this simulation.",
                  simulate: "What should I watch carefully while this simulation runs?",
                  observe: "Help me identify the most important pattern in these simulation results.",
                  explain: "Help me structure a scientific explanation from prediction to evidence to conclusion.",
                  feedback: "Based on my feedback, what is the fastest remediation strategy before mastery check?",
                  prove: "Give me a quick checklist to avoid mistakes in this mastery question.",
                };
                requestTutorAssist(stepPrompt[state.step]);
              }}
            >
              Ask AI for Step Hint
            </Button>
          </div>

          {state.step === "predict" && (
            <PredictStep
              prediction={state.prediction}
              confidence={state.confidence}
              onPredictionChange={(v) => dispatch({ type: "SET_PREDICTION", prediction: v })}
              onConfidenceChange={(v) => dispatch({ type: "SET_CONFIDENCE", confidence: v })}
              onNext={() => goToStep("simulate")}
            />
          )}

          {state.step === "simulate" && (
            <SimulateStep
              simulationId={simulationId}
              prediction={state.prediction}
              confidence={state.confidence}
              isRunning={runMutation.isPending}
              hasResult={state.simulationResult !== null}
              onRun={() => runMutation.mutate()}
              onNext={() => goToStep("observe")}
            />
          )}

          {state.step === "observe" && state.simulationResult && (
            <ObserveStep
              result={state.simulationResult}
              onNext={() => goToStep("explain")}
              onBack={() => goToStep("simulate")}
            />
          )}

          {state.step === "explain" && (
            <ExplainStep
              explanation={state.explanation}
              onChange={(v) => dispatch({ type: "SET_EXPLANATION", explanation: v })}
              onNext={() => evaluateMutation.mutate()}
              onBack={() => goToStep("observe")}
              isEvaluating={evaluateMutation.isPending}
            />
          )}

          {state.step === "feedback" && state.feedback && (
            <FeedbackStep
              feedback={state.feedback}
              onNext={() => goToStep("prove")}
              onBack={() => goToStep("explain")}
            />
          )}

          {state.step === "prove" && (
            <>
              {isLoadingChallenge || !challenge ? (
                <div className="text-center py-12">
                  <div className={textStyles.muted}>Loading challenge...</div>
                </div>
              ) : (
                <ProveMasteryStep
                  challenge={challenge}
                  selectedOption={state.selectedChallengeOption}
                  masteryPassed={state.masteryPassed}
                  onSelectOption={(idx) =>
                    dispatch({ type: "SELECT_CHALLENGE_OPTION", index: idx })
                  }
                  onSubmit={() => masteryMutation.mutate()}
                  onBack={() => goToStep("feedback")}
                  onFinish={() => navigate("/dashboard")}
                  isSubmitting={masteryMutation.isPending}
                />
              )}
            </>
          )}
        </Card>
      </div>
    </Box>
  );
}
