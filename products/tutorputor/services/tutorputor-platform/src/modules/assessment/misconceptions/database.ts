/**
 * Misconception Database
 *
 * Curated misconception seeds used for targeted distractors and diagnosis.
 *
 * @doc.type service
 * @doc.purpose Store and retrieve common misconceptions by domain
 * @doc.layer product
 * @doc.pattern Domain Knowledge Base
 */

export interface MisconceptionRecord {
  id: string;
  domain: string;
  conceptKeywords: string[];
  label: string;
  explanation: string;
  distractor: string;
  prerequisiteConceptId?: string;
}

const MISCONCEPTIONS: MisconceptionRecord[] = [
  {
    id: "physics-force-motion",
    domain: "SCIENCE",
    conceptKeywords: ["force", "motion", "newton"],
    label: "Force is required to keep an object moving",
    explanation:
      "Learners often confuse constant velocity with constant applied force.",
    distractor: "Objects must keep experiencing force to remain in motion.",
    prerequisiteConceptId: "inertia",
  },
  {
    id: "physics-mass-weight",
    domain: "SCIENCE",
    conceptKeywords: ["mass", "weight", "gravity"],
    label: "Mass and weight are interchangeable",
    explanation:
      "Mass is intrinsic while weight depends on the gravitational field.",
    distractor: "Mass changes whenever gravity changes.",
    prerequisiteConceptId: "gravity",
  },
  {
    id: "chemistry-bond-energy",
    domain: "SCIENCE",
    conceptKeywords: ["bond", "energy", "reaction"],
    label: "Breaking bonds releases energy",
    explanation:
      "Bond breaking requires energy; net reaction energy depends on both breaking and forming bonds.",
    distractor: "Chemical bonds release stored energy when they break.",
  },
  {
    id: "math-fraction-size",
    domain: "MATH",
    conceptKeywords: ["fraction", "denominator", "numerator"],
    label: "A larger denominator means a larger fraction",
    explanation:
      "Learners may compare denominators without reasoning about partition size.",
    distractor: "One eighth is larger than one fourth because 8 is bigger than 4.",
  },
  {
    id: "math-equals-operator",
    domain: "MATH",
    conceptKeywords: ["equation", "equals", "expression"],
    label: "The equals sign means compute the answer",
    explanation:
      "The equals sign represents equivalence between both sides, not just an output marker.",
    distractor: "The equals sign tells you where to write the final answer only.",
  },
  {
    id: "tech-variable-storage",
    domain: "TECH",
    conceptKeywords: ["variable", "assignment", "programming"],
    label: "Variables remember every value ever assigned",
    explanation:
      "Variables represent the current bound value, not full historical state.",
    distractor: "A variable permanently stores each previous value unless deleted.",
  },
];

export class MisconceptionDatabase {
  findByDomainAndTopic(domain: string, topic: string): MisconceptionRecord[] {
    const normalizedTopic = topic.toLowerCase();
    return MISCONCEPTIONS.filter(
      (record) =>
        record.domain === domain &&
        record.conceptKeywords.some((keyword) =>
          normalizedTopic.includes(keyword.toLowerCase()),
        ),
    );
  }

  findById(id: string): MisconceptionRecord | undefined {
    return MISCONCEPTIONS.find((record) => record.id === id);
  }
}
