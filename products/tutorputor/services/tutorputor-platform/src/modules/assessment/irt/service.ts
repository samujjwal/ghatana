/**
 * IRT Calibration Service
 *
 * Lightweight 2PL-style ranking for adaptive assessment selection.
 *
 * @doc.type service
 * @doc.purpose Item Response Theory calibration and adaptive selection
 * @doc.layer product
 * @doc.pattern Domain Service
 */

export interface IRTParameters {
  discrimination: number;
  difficulty: number;
  guessing: number;
}

export interface IRTCandidate<T> {
  item: T;
  irt: IRTParameters;
}

export interface IRTSelectionResult<T> {
  items: T[];
  targetTheta: number;
}

export class IRTCalibrationService {
  probabilityCorrect(theta: number, params: IRTParameters): number {
    const { discrimination, difficulty, guessing } = params;
    const logistic = 1 / (1 + Math.exp(-discrimination * (theta - difficulty)));
    return guessing + (1 - guessing) * logistic;
  }

  information(theta: number, params: IRTParameters): number {
    const probability = this.probabilityCorrect(theta, params);
    const adjustedProbability = (probability - params.guessing) / (1 - params.guessing);
    const q = 1 - adjustedProbability;

    return (
      (params.discrimination ** 2) *
      adjustedProbability *
      q /
      Math.max((1 - params.guessing) ** 2, 0.0001)
    );
  }

  estimateThetaFromMastery(averageMastery: number): number {
    const centered = (averageMastery - 0.5) * 4;
    return Math.max(-3, Math.min(3, centered));
  }

  calibrateForDifficulty(
    difficulty: "INTRO" | "INTERMEDIATE" | "ADVANCED" | string,
    taxonomyLevel: string,
  ): IRTParameters {
    const difficultyMap: Record<string, number> = {
      INTRO: -1.2,
      INTERMEDIATE: 0,
      ADVANCED: 1.2,
      beginner: -1.4,
      easy: -0.8,
      medium: 0,
      hard: 1.1,
      expert: 1.8,
    };

    const taxonomyBoost = taxonomyLevel.toLowerCase().includes("analy")
      ? 0.3
      : taxonomyLevel.toLowerCase().includes("apply")
        ? 0.15
        : 0;

    return {
      discrimination: taxonomyBoost > 0 ? 1.3 : 1.0,
      difficulty: (difficultyMap[difficulty] ?? 0) + taxonomyBoost,
      guessing: 0.2,
    };
  }

  selectNextItems<T>(
    candidates: Array<IRTCandidate<T>>,
    targetTheta: number,
    count: number,
  ): IRTSelectionResult<T> {
    const ranked = candidates
      .map((candidate) => ({
        ...candidate,
        info: this.information(targetTheta, candidate.irt),
        closeness: Math.abs(candidate.irt.difficulty - targetTheta),
      }))
      .sort((a, b) => {
        if (b.info !== a.info) {
          return b.info - a.info;
        }
        return a.closeness - b.closeness;
      });

    return {
      items: ranked.slice(0, count).map((candidate) => candidate.item),
      targetTheta,
    };
  }
}
