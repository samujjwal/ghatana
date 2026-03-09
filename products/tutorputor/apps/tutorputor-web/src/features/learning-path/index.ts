/**
 * Learning Path Feature - Index
 *
 * @doc.type module
 * @doc.purpose Export all learning path feature components and hooks
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Hooks
export * from "./hooks/useSimulationSteps";

// Components
export { SimulationStepTile } from "./components/SimulationStepTile";
export type { SimulationStepTileProps } from "./components/SimulationStepTile";
