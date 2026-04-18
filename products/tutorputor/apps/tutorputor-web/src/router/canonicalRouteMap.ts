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
] as const;

export const compatibilityAliases = {
  "/": "/dashboard",
  "/modules": "/search",
  "/ai-tutor": "/dashboard",
} as const;