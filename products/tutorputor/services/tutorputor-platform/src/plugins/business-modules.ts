import type { FastifyPluginAsync } from "fastify";
import { authModule } from "../modules/auth/index.js";
import { userModule } from "../modules/user/index.js";
import { learningModule } from "../modules/learning/index.js";
import { aiModule } from "../modules/ai/index.js";
import { collaborationModule } from "../modules/collaboration/index.js";
import { integrationModule } from "../modules/integration/index.js";
import { engagementModule } from "../modules/engagement/index.js";
import { credentialModule } from "../modules/credentials/index.js";
import { capabilityRegistryModule } from "../modules/capabilities/index.js";
import { abacModule } from "../modules/abac/index.js";
import { contentEvaluationModule } from "../modules/content-evaluation/index.js";
import { goldenDatasetModule } from "../modules/golden-dataset/index.js";
import { contentQualityMonitoringModule } from "../modules/content-quality-monitoring/index.js";
import { analyticsConsolidationModule } from "../modules/analytics-consolidation/index.js";
import { contentGenerationBenchmarkModule } from "../modules/content-generation-benchmark/index.js";
import { complianceEvidenceModule } from "../modules/compliance-evidence/index.js";
import { dataResidencyModule } from "../modules/data-residency/index.js";
import { pluginMarketplaceModule } from "../modules/plugin-marketplace/index.js";

interface BusinessModulesOptions {
  startLearnerProfileGrpcServer?: boolean;
  learnerProfileGrpcAddress?: string;
}

/**
 * Business-domain module plugin.
 * Registers learning, user, auth, AI, collaboration, integration, engagement, credential, capability registry, ABAC, content evaluation, golden dataset, content quality monitoring, analytics consolidation, content generation benchmark, compliance evidence, data residency, and plugin marketplace modules.
 *
 * @doc.type module
 * @doc.purpose Register business-domain modules (learning, user, auth, AI, collaboration, integration, engagement, capabilities, ABAC, content evaluation, golden dataset, content quality monitoring, analytics consolidation, content generation benchmark, compliance evidence, data residency, plugin marketplace)
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export const setupBusinessModules: FastifyPluginAsync<BusinessModulesOptions> = async (
  app,
  options,
) => {
  // Register auth module (SSO, token refresh, logout)
  await app.register(authModule, { prefix: "/api/v1/auth" });

  // Register user module
  await app.register(userModule, { prefix: "/api/v1/users" });

  // Register learning module (pathways, assessment, analytics, learner profile)
  await app.register(learningModule, {
    prefix: "/api/v1/learning",
  });

  // Register AI module (tutor query, content generation AI)
  await app.register(aiModule, { prefix: "/api/v1/ai" });

  // Register collaboration module (threads, posts)
  await app.register(collaborationModule, { prefix: "/api/v1/collaboration" });

  // Register integration module (billing, LTI, marketplace)
  await app.register(integrationModule, { prefix: "/api/v1/integration" });

  // Register engagement module (gamification, social)
  await app.register(engagementModule, { prefix: "/api/v1/engagement" });

  // Register credential module
  await app.register(credentialModule, { prefix: "/api/v1/credentials" });

  // Register capability registry module (feature flags)
  await app.register(capabilityRegistryModule, { prefix: "/api/v1" });

  // Register ABAC module (attribute-based access control)
  await app.register(abacModule, { prefix: "/api/v1" });

  // Register content evaluation module
  await app.register(contentEvaluationModule, { prefix: "/api/v1" });

  // Register golden dataset module
  await app.register(goldenDatasetModule, { prefix: "/api/v1" });

  // Register content quality monitoring module
  await app.register(contentQualityMonitoringModule, { prefix: "/api/v1" });

  // Register analytics consolidation module
  await app.register(analyticsConsolidationModule, { prefix: "/api/v1" });

  // Register content generation benchmark module
  await app.register(contentGenerationBenchmarkModule, { prefix: "/api/v1" });

  // Register compliance evidence module
  await app.register(complianceEvidenceModule, { prefix: "/api/v1" });

  // Register data residency module
  await app.register(dataResidencyModule, { prefix: "/api/v1" });

  // Register plugin marketplace module
  await app.register(pluginMarketplaceModule, { prefix: "/api/v1" });

  app.log.info("✅ Business-domain modules registered");
};
