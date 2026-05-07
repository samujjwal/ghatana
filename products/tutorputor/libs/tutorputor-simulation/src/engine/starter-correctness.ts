import type {
  SimulationManifest,
  SimulationOutputSpec,
  SimulationParameterBound,
} from "@tutorputor/contracts/v1/simulation";

export interface StarterEvaluationResult {
  seed: number;
  parameterId: string;
  inputValue: number;
  clampedValue: number;
  clamped: boolean;
  outputId: string;
  outputValue: number;
  expectedValue: number;
  tolerance: number;
  failureStateIds: string[];
}

export function clampSimulationParameter(
  bound: SimulationParameterBound,
  inputValue: number,
): { value: number; clamped: boolean } {
  const value = Math.min(bound.max, Math.max(bound.min, inputValue));
  return {
    value,
    clamped: value !== inputValue,
  };
}

export function evaluateStarterGoldenOutput(
  manifest: SimulationManifest,
  inputValue = manifest.canonical?.parameterBounds[0]?.defaultValue ?? 0,
): StarterEvaluationResult {
  const canonical = manifest.canonical;
  if (!canonical) {
    throw new Error(`Simulation ${manifest.id} is missing canonical metadata`);
  }

  const parameter = firstOrThrow(
    canonical.parameterBounds,
    `Simulation ${manifest.id} is missing parameter bounds`,
  );
  const output = firstOrThrow(
    canonical.outputs,
    `Simulation ${manifest.id} is missing output specs`,
  );
  const expectedValue = requiredNumber(
    output.expectedValue,
    output,
    "expectedValue",
  );
  const tolerance = requiredNumber(output.tolerance, output, "tolerance");
  const clamped = clampSimulationParameter(parameter, inputValue);

  return {
    seed: canonical.seed,
    parameterId: parameter.parameterId,
    inputValue,
    clampedValue: clamped.value,
    clamped: clamped.clamped,
    outputId: output.outputId,
    outputValue: clamped.value,
    expectedValue,
    tolerance,
    failureStateIds: clamped.clamped
      ? canonical.failureStates.map((state) => state.id)
      : [],
  };
}

export function isWithinTolerance(result: StarterEvaluationResult): boolean {
  return Math.abs(result.outputValue - result.expectedValue) <= result.tolerance;
}

function firstOrThrow<T>(items: T[] | undefined, message: string): T {
  const item = items?.[0];
  if (!item) {
    throw new Error(message);
  }
  return item;
}

function requiredNumber(
  value: number | undefined,
  output: SimulationOutputSpec,
  property: "expectedValue" | "tolerance",
): number {
  if (typeof value !== "number") {
    throw new Error(`Output ${output.outputId} is missing ${property}`);
  }
  return value;
}
