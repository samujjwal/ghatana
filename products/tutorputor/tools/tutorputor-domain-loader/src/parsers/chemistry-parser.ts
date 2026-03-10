/**
 * Chemistry JSON Parser
 *
 * Parses chemistry.json domain content and normalizes to DomainConcept format.
 *
 * @doc.type module
 * @doc.purpose Parse chemistry domain content from JSON
 * @doc.layer product
 * @doc.pattern Parser
 */

import type {
  DomainConcept,
  CurriculumLevel,
  ChemistryJSONStructure,
  RawConceptJSON,
} from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import { parseRawConcept } from "./physics-parser";
import { mapLevelString } from "../utils/mappers";

/**
 * Parse chemistry.json content into normalized DomainConcept array.
 *
 * chemistry.json is an object with { domain, levels: { Foundational: { concepts: [...] }, ... } }
 */
export function parseChemistryJSON(json: unknown): DomainConcept[] {
  if (typeof json !== "object" || json === null) {
    throw new Error("chemistry.json must be an object");
  }

  const data = json as ChemistryJSONStructure;

  if (!data.levels || typeof data.levels !== "object") {
    throw new Error("chemistry.json must have a 'levels' object");
  }

  const concepts: DomainConcept[] = [];

  for (const [levelName, levelData] of Object.entries(data.levels) as [string, { concepts: RawConceptJSON[] }][]) {
    if (!levelData.concepts || !Array.isArray(levelData.concepts)) {
      continue;
    }

    const level = mapLevelString(levelName);

    for (const rawConcept of levelData.concepts) {
      try {
        const concept = parseRawConcept(rawConcept, "CHEMISTRY", level);
        concepts.push(concept);
      } catch (error) {
        console.warn(
          `Failed to parse chemistry concept ${rawConcept.id}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }
  }

  return concepts;
}
