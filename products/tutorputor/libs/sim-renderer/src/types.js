/**
 * Simulation Renderer Types
 *
 * @doc.type module
 * @doc.purpose Define core types for simulation rendering system
 * @doc.layer product
 * @doc.pattern Schema
 */
/**
 * Default theme configuration.
 */
export const defaultTheme = {
    primary: "#3b82f6",
    secondary: "#6366f1",
    success: "#22c55e",
    warning: "#f59e0b",
    danger: "#ef4444",
    neutral: "#64748b",
    background: "#f8fafc",
    foreground: "#0f172a",
    border: "#e2e8f0",
    highlight: "#fbbf24",
    fontFamily: "Inter, system-ui, sans-serif",
};
// =============================================================================
// Domain-Specific Entity Type Guards
// =============================================================================
export function isDiscreteNodeEntity(entity) {
    return entity.type === "node";
}
export function isDiscreteEdgeEntity(entity) {
    return entity.type === "edge";
}
export function isDiscretePointerEntity(entity) {
    return entity.type === "pointer";
}
export function isPhysicsBodyEntity(entity) {
    return entity.type === "rigidBody";
}
export function isPhysicsSpringEntity(entity) {
    return entity.type === "spring";
}
export function isPhysicsVectorEntity(entity) {
    return entity.type === "vector";
}
export function isPhysicsParticleEntity(entity) {
    return entity.type === "particle";
}
export function isChemAtomEntity(entity) {
    return entity.type === "atom";
}
export function isChemBondEntity(entity) {
    return entity.type === "bond";
}
export function isChemMoleculeEntity(entity) {
    return entity.type === "molecule";
}
export function isChemReactionArrowEntity(entity) {
    return entity.type === "reactionArrow";
}
export function isChemEnergyProfileEntity(entity) {
    return entity.type === "energyProfile";
}
export function isBioCellEntity(entity) {
    return entity.type === "cell";
}
export function isBioOrganelleEntity(entity) {
    return entity.type === "organelle";
}
export function isBioCompartmentEntity(entity) {
    return entity.type === "compartment";
}
export function isBioEnzymeEntity(entity) {
    return entity.type === "enzyme";
}
export function isBioSignalEntity(entity) {
    return entity.type === "signal";
}
export function isBioGeneEntity(entity) {
    return entity.type === "gene";
}
export function isMedCompartmentEntity(entity) {
    return entity.type === "pkCompartment";
}
export function isMedDoseEntity(entity) {
    return entity.type === "dose";
}
export function isMedInfectionAgentEntity(entity) {
    return entity.type === "infectionAgent";
}
//# sourceMappingURL=types.js.map