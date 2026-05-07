import type {
  PlaybackConfig,
  SimulationManifest,
  SimulationParameterBound,
} from "@tutorputor/contracts/v1/simulation";

export interface KeyboardAdjustableControl {
  parameterId: string;
  ariaLabel: string;
  min: number;
  max: number;
  step: number;
  defaultValue: number;
  keys: {
    increment: string[];
    decrement: string[];
    commit: string[];
  };
}

export interface SimulationAccessibilityRuntime {
  aria: {
    role: "application";
    tabIndex: 0;
    "aria-label": string;
    "aria-describedby": string;
  };
  focusClassName: string;
  keyboardControls: KeyboardAdjustableControl[];
  reducedMotionPlayback: PlaybackConfig;
  chartAltText: string;
  nonVisualStateSummary: string;
}

export function buildSimulationAccessibilityRuntime(
  manifest: SimulationManifest,
  options: {
    currentStepIndex?: number;
    prefersReducedMotion?: boolean;
    descriptionId?: string;
  } = {},
): SimulationAccessibilityRuntime {
  const currentStepIndex = options.currentStepIndex ?? 0;
  const currentStep = manifest.steps[currentStepIndex] ?? manifest.steps[0];
  const descriptionId = options.descriptionId ?? `${manifest.id}-summary`;

  return {
    aria: {
      role: "application",
      tabIndex: 0,
      "aria-label": manifest.accessibility?.altText ?? manifest.title,
      "aria-describedby": descriptionId,
    },
    focusClassName:
      "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600",
    keyboardControls: (manifest.canonical?.parameterBounds ?? []).map(
      toKeyboardControl,
    ),
    reducedMotionPlayback: reducePlaybackMotion(
      manifest.playback,
      options.prefersReducedMotion === true ||
        manifest.accessibility?.reducedMotion === true,
    ),
    chartAltText: buildChartAltText(manifest),
    nonVisualStateSummary: [
      manifest.title,
      `Domain: ${manifest.domain}.`,
      `Step ${currentStepIndex + 1} of ${manifest.steps.length}: ${currentStep?.title ?? "Untitled step"}.`,
      currentStep?.description,
      `Outputs: ${(manifest.canonical?.outputs ?? [])
        .map((output) => `${output.label}${typeof output.expectedValue === "number" ? ` expected ${output.expectedValue}` : ""}`)
        .join(", ")}.`,
    ]
      .filter(Boolean)
      .join(" "),
  };
}

export function validateSimulationAccessibilityRuntime(
  manifest: SimulationManifest,
): string[] {
  const runtime = buildSimulationAccessibilityRuntime(manifest);
  const issues: string[] = [];

  if (!manifest.accessibility?.altText) {
    issues.push("Simulation must provide text alternatives through accessibility.altText");
  }
  if (runtime.keyboardControls.length === 0) {
    issues.push("Simulation must expose keyboard-adjustable controls");
  }
  if (!runtime.focusClassName.includes("focus-visible")) {
    issues.push("Simulation must expose visible focus states");
  }
  if (!runtime.chartAltText) {
    issues.push("Simulation must provide text alternatives for chart/output state");
  }
  if (!runtime.nonVisualStateSummary) {
    issues.push("Simulation must provide a non-visual state summary");
  }

  return issues;
}

function toKeyboardControl(
  parameter: SimulationParameterBound,
): KeyboardAdjustableControl {
  return {
    parameterId: parameter.parameterId,
    ariaLabel: `${parameter.label}: ${parameter.min} to ${parameter.max}`,
    min: parameter.min,
    max: parameter.max,
    step: Math.max((parameter.max - parameter.min) / 100, 0.1),
    defaultValue: parameter.defaultValue,
    keys: {
      increment: ["ArrowRight", "ArrowUp", "PageUp"],
      decrement: ["ArrowLeft", "ArrowDown", "PageDown"],
      commit: ["Enter", "Space"],
    },
  };
}

function reducePlaybackMotion(
  playback: PlaybackConfig,
  reduceMotion: boolean,
): PlaybackConfig {
  if (!reduceMotion) {
    return playback;
  }

  return {
    ...playback,
    autoPlay: false,
    loop: false,
    defaultSpeed: Math.min(playback.defaultSpeed, 0.5),
  };
}

function buildChartAltText(manifest: SimulationManifest): string {
  const outputs = manifest.canonical?.outputs ?? [];
  return outputs
    .map((output) => {
      const expected =
        typeof output.expectedValue === "number"
          ? ` expected value ${output.expectedValue}`
          : "";
      return `${output.label} (${output.type})${expected}`;
    })
    .join("; ");
}
