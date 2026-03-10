/**
 * Value Mappers and Utility Functions
 *
 * @doc.type module
 * @doc.purpose Map and transform raw values to typed enums
 * @doc.layer product
 * @doc.pattern Utility
 */

import type { Difficulty } from "@ghatana/tutorputor-contracts/v1/types";
import type { CurriculumLevel } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

/**
 * Parse time strings like "20 min", "1 hr", "1.5 hours" to minutes.
 */
export function parseTimeToMinutes(timeStr: string): number {
  if (!timeStr) return 15; // Default

  const normalized = timeStr.toLowerCase().trim();

  // Match patterns like "20 min", "1 hr", "1.5 hours", "2–3 hours"
  const hourMatch = normalized.match(/(\d+(?:\.\d+)?)\s*(?:hr|hour)/);
  const minMatch = normalized.match(/(\d+)\s*min/);
  const rangeMatch = normalized.match(/(\d+)[-–](\d+)\s*(?:hr|hour)/);

  if (rangeMatch) {
    // For ranges like "2–3 hours", take the average
    const low = parseFloat(rangeMatch[1]);
    const high = parseFloat(rangeMatch[2]);
    return Math.round(((low + high) / 2) * 60);
  }

  if (hourMatch) {
    return Math.round(parseFloat(hourMatch[1]) * 60);
  }

  if (minMatch) {
    return parseInt(minMatch[1], 10);
  }

  // Try to parse as a plain number (assume minutes)
  const num = parseInt(normalized, 10);
  if (!isNaN(num)) {
    return num;
  }

  return 15; // Default fallback
}

/**
 * Map difficulty strings to Difficulty enum.
 */
export function mapDifficultyString(difficulty: string): Difficulty {
  const normalized = difficulty.toLowerCase().trim();

  if (normalized === "easy" || normalized === "beginner") {
    return "INTRO";
  }
  if (normalized === "intermediate" || normalized === "medium") {
    return "INTERMEDIATE";
  }
  if (normalized === "hard" || normalized === "advanced" || normalized === "very_hard") {
    return "ADVANCED";
  }

  return "INTERMEDIATE"; // Default
}

/**
 * Map level strings from JSON to CurriculumLevel enum.
 */
export function mapLevelString(level: string): CurriculumLevel {
  const normalized = level.toLowerCase().trim().replace(/[_-]/g, "_");

  if (normalized === "foundational" || normalized === "foundation" || normalized === "basic") {
    return "FOUNDATIONAL";
  }
  if (normalized === "intermediate" || normalized === "medium") {
    return "INTERMEDIATE";
  }
  if (normalized === "advanced" || normalized === "expert") {
    return "ADVANCED";
  }
  if (
    normalized === "research" ||
    normalized === "research_independent" ||
    normalized.includes("research")
  ) {
    return "RESEARCH";
  }

  return "INTERMEDIATE"; // Default
}

/**
 * Map CurriculumLevel to Difficulty for module creation.
 */
export function levelToDifficulty(level: CurriculumLevel): Difficulty {
  switch (level) {
    case "FOUNDATIONAL":
      return "INTRO";
    case "INTERMEDIATE":
      return "INTERMEDIATE";
    case "ADVANCED":
    case "RESEARCH":
      return "ADVANCED";
    default:
      return "INTERMEDIATE";
  }
}

/**
 * Map simulation domain to module domain.
 * Our ModuleDomain is limited to MATH | SCIENCE | TECH
 */
export function domainToModuleDomain(
  domain: "PHYSICS" | "CHEMISTRY" | string
): "MATH" | "SCIENCE" | "TECH" {
  const normalized = domain.toUpperCase();

  if (
    normalized === "PHYSICS" ||
    normalized === "CHEMISTRY" ||
    normalized === "BIOLOGY" ||
    normalized === "MEDICINE"
  ) {
    return "SCIENCE";
  }

  if (normalized === "MATHEMATICS" || normalized === "MATH") {
    return "MATH";
  }

  if (
    normalized === "CS_DISCRETE" ||
    normalized === "ENGINEERING" ||
    normalized === "ECONOMICS"
  ) {
    return "TECH";
  }

  return "SCIENCE"; // Default
}

/**
 * Generate a slug from a concept name.
 */
export function generateSlug(name: string, id: string): string {
  const baseSlug = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .substring(0, 50);

  // Append concept ID for uniqueness
  const idSuffix = id.toLowerCase().replace(/_/g, "-");
  return `${baseSlug}-${idSuffix}`;
}

/**
 * Validate concept ID format.
 */
export function isValidConceptId(id: string): boolean {
  // Expected formats: phy_F_1, chem_I_3, physics_I_1, etc.
  return /^[a-z]+_[A-Z_]+_[A-Za-z0-9]+$/i.test(id);
}

/**
 * Extract domain hint from concept ID.
 */
export function extractDomainFromId(id: string): string | null {
  const match = id.match(/^([a-z]+)_/i);
  if (match) {
    const prefix = match[1].toLowerCase();
    if (prefix === "phy" || prefix === "physics") return "PHYSICS";
    if (prefix === "chem" || prefix === "chemistry") return "CHEMISTRY";
    if (prefix === "bio" || prefix === "biology") return "BIOLOGY";
    if (prefix === "math" || prefix === "mathematics") return "MATHEMATICS";
    if (prefix === "cs") return "CS_DISCRETE";
    if (prefix === "med") return "MEDICINE";
    if (prefix === "econ") return "ECONOMICS";
    if (prefix === "eng") return "ENGINEERING";
  }
  return null;
}
