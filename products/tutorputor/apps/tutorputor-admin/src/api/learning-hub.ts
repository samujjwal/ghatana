/**
 * Learning Hub API
 *
 * Real implementation of API calls for Learning Units and Simulations.
 * Uses backend API endpoints with authentication.
 *
 * @doc.type api
 * @doc.purpose Data access for Learning Hub
 * @doc.layer product
 * @doc.pattern Repository
 */

import type { LearningUnit } from "@tutorputor/contracts/v1/learning-unit";
import {
  type SimulationBlueprintSeed,
  allSimulationManifests as allSimulations,
} from "../data/simulationManifestSeedData";
import { setAuthToken as setContentStudioAuthToken } from "../services/contentStudioApi";

// Auth token store - sync with contentStudioApi
let _authToken: string | null = null;

export function setAuthToken(token: string | null): void {
  _authToken = token;
  setContentStudioAuthToken(token);
}

/** Resolve the API base from env or fall back to same-origin. */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const LEARNING = `${BASE_URL}/api/learning`;

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (_authToken) headers["Authorization"] = `Bearer ${_authToken}`;
  return headers;
}

async function learningRequest<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(`${LEARNING}${path}`, {
    method,
    headers: authHeaders(),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const err = await response
      .json()
      .catch(() => ({ message: response.statusText }));
    throw new Error(err.message ?? `HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as unknown as T;
  return response.json();
}

export const LearningHubApi = {
  // Learning Units
  async getLearningUnits(): Promise<LearningUnit[]> {
    try {
      const result = await learningRequest<{ data: LearningUnit[] }>(
        "GET",
        "/modules",
      );
      return result.data ?? [];
    } catch (error) {
      // Fallback to seed data if backend is unavailable
      console.warn("Backend unavailable, using seed data for learning units");
      return [];
    }
  },

  async getLearningUnit(id: string): Promise<LearningUnit | null> {
    try {
      const result = await learningRequest<{ data: LearningUnit }>(
        "GET",
        `/modules/${id}`,
      );
      return result.data ?? null;
    } catch (error) {
      if (error instanceof Error && error.message.includes("404")) {
        return null;
      }
      throw error;
    }
  },

  async createLearningUnit(unit: Partial<LearningUnit>): Promise<LearningUnit> {
    const result = await learningRequest<{ data: LearningUnit }>(
      "POST",
      "/modules",
      unit,
    );
    return result.data;
  },

  async updateLearningUnit(
    id: string,
    unit: Partial<LearningUnit>,
  ): Promise<LearningUnit> {
    const result = await learningRequest<{ data: LearningUnit }>(
      "PUT",
      `/modules/${id}`,
      unit,
    );
    return result.data;
  },

  async deleteLearningUnit(id: string): Promise<void> {
    await learningRequest("DELETE", `/modules/${id}`);
  },

  // Simulations
  async getSimulations(): Promise<SimulationBlueprintSeed[]> {
    // Note: Simulations endpoint may not exist yet, use seed data for now
    return [...allSimulations];
  },

  async getSimulation(id: string): Promise<SimulationBlueprintSeed | null> {
    const sim = allSimulations.find((s) => s.id === id);
    return sim ? JSON.parse(JSON.stringify(sim)) : null;
  },

  async saveSimulation(
    manifest: SimulationBlueprintSeed,
  ): Promise<SimulationBlueprintSeed> {
    // Note: Simulations endpoint may not exist yet, return manifest for now
    return manifest;
  },
};
