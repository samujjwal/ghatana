export const canonicalLearnerRoutes = [
  "/login",
  "/",
  "/dashboard",
  "/pathways",
  "/search",
  "/modules/:slug",
  "/assessments",
  "/assessments/:assessmentId",
  "/marketplace",
  "/collaboration",
  "/analytics",
  "/teacher",
  "/settings",
  "/simulations",
  "/simulations/studio/:id?",
  "/learn/:simulationId",
] as const;

export const compatibilityAliases = {
  "/": "/dashboard",
  "/modules": "/search",
  "/ai-tutor": "/dashboard",
  "/home": "/dashboard",
  "/content-explore": "/search",
  "/content-explorer": "/search",
  "/assessment-list": "/assessments",
  "/learning-paths": "/pathways",
} as const;