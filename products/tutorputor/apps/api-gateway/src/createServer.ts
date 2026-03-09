import fastify, { type FastifyInstance } from "fastify";
import { setupPlatform } from "@ghatana/tutorputor-platform";

export async function createServer(): Promise<FastifyInstance> {
  const app = fastify({
    logger: true
  });

  // Tries to load environment variables for testing if not present
  if (!process.env.TUTORPUTOR_DATABASE_URL && process.env.DATABASE_URL) {
    process.env.TUTORPUTOR_DATABASE_URL = process.env.DATABASE_URL;
  }

  // Initialize the Consolidated Platform
  // This sets up Database, Redis, Auth, Observability, and registers all modules
  // (Content, Learning, User, Engagement, Integration, Tenant)
  await setupPlatform(app);

  // Gateway-specific root route
  app.get("/", async () => {
    return {
      service: "TutorPutor API Gateway",
      architecture: "Consolidated Platform (v2)",
      status: "Operational"
    };
  });

  return app;
}
