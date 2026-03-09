/**
 * Learning Hub API
 *
 * Mock implementation of API calls for Learning Units and Simulations.
 * Uses local seed data for now.
 *
 * @doc.type api
 * @doc.purpose Data access for Learning Hub
 * @doc.layer product
 * @doc.pattern Repository
 */

import { LearningUnit } from '@ghatana/tutorputor-contracts/v1/learning-unit';
import { SimulationManifest } from '@ghatana/tutorputor-contracts/v1/simulation';
import { allLearningUnits } from '../data/learningUnitSeedData';
import { allSimulationManifests as allSimulations } from '../data/simulationManifestSeedData';

// Simulate network delay
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

export const LearningHubApi = {
    // Learning Units
    async getLearningUnits(): Promise<LearningUnit[]> {
        await delay(500);
        return [...allLearningUnits];
    },

    async getLearningUnit(id: string): Promise<LearningUnit | null> {
        await delay(300);
        const unit = allLearningUnits.find(u => u.id === id);
        return unit ? JSON.parse(JSON.stringify(unit)) : null;
    },

    async createLearningUnit(unit: LearningUnit): Promise<LearningUnit> {
        await delay(800);
        console.log('API: Creating Learning Unit', unit);
        // In a real app, we would add to the list
        return unit;
    },

    async updateLearningUnit(unit: LearningUnit): Promise<LearningUnit> {
        await delay(600);
        console.log('API: Updating Learning Unit', unit);
        return unit;
    },

    async deleteLearningUnit(id: string): Promise<void> {
        await delay(400);
        console.log('API: Deleting Learning Unit', id);
    },

    // Simulations
    async getSimulations(): Promise<SimulationManifest[]> {
        await delay(500);
        return [...allSimulations];
    },

    async getSimulation(id: string): Promise<SimulationManifest | null> {
        await delay(300);
        const sim = allSimulations.find(s => s.id === id);
        return sim ? JSON.parse(JSON.stringify(sim)) : null;
    },

    async saveSimulation(manifest: SimulationManifest): Promise<SimulationManifest> {
        await delay(800);
        console.log('API: Saving Simulation', manifest);
        return manifest;
    }
};
