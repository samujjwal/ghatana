import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import path from "path";
import { fileURLToPath } from "url";
import CircuitBreaker from "opossum";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Proto paths - adjusting for probable location relative to this file
// Project: products/tutorputor/services/tutorputor-platform/src/clients
// Protos: products/tutorputor/contracts/proto
const AI_LEARNING_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/ai_learning.proto",
);
const CONTENT_GENERATION_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/content_generation.proto",
);

const GRPC_HOST = process.env.AI_SERVICE_URL || "localhost:50051";

// Load AiLearning package definition
const learningPackageDef = protoLoader.loadSync(AI_LEARNING_PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const learningProtoDescriptor = grpc.loadPackageDefinition(
  learningPackageDef,
) as any;
const aiLearningService =
  learningProtoDescriptor.tutorputor.ai_learning.AiLearningService;

// Load ContentGeneration package definition
const contentPackageDef = protoLoader.loadSync(CONTENT_GENERATION_PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const contentProtoDescriptor = grpc.loadPackageDefinition(
  contentPackageDef,
) as any;
// The package name in content_generation.proto is 'tutorputor.content_generation'
const contentGenService =
  contentProtoDescriptor.tutorputor.content_generation.ContentGenerationService;

export class AiClient {
  private learningClient: any;
  private contentClient: any;
  private breaker: CircuitBreaker;

  constructor() {
    // In a real scenario, we might want to use different credentials or secure channels
    // using grpc.credentials.createInsecure() for internal mesh
    this.learningClient = new aiLearningService(
      GRPC_HOST,
      grpc.credentials.createInsecure(),
    );
    this.contentClient = new contentGenService(
      GRPC_HOST,
      grpc.credentials.createInsecure(),
    );

    // Initialize Circuit Breaker
    const breakerOptions = {
      timeout: 3000, // If function takes longer than 3 seconds, trigger failure
      errorThresholdPercentage: 50, // When 50% of requests fail, trip breaker
      resetTimeout: 30000, // After 30 seconds, try again
    };

    // We use a generic breaker execution wrapper
    this.breaker = new CircuitBreaker(
      this.executeGrpc.bind(this),
      breakerOptions,
    );
    this.breaker.fallback(() => {
      console.warn(
        "AI Service Circuit Breaker Open or Timeout - Returning null/fallback",
      );
      return null; // Return null to signal fallback to caller logic
    });

    console.log(`AiClient initialized, connecting to ${GRPC_HOST}`);
  }

  /**
   * Generic wrapper to promisify gRPC calls
   */
  private executeGrpc(client: any, method: string, request: any): Promise<any> {
    return new Promise((resolve, reject) => {
      if (!client[method]) {
        reject(new Error(`Method ${method} not found on gRPC client`));
        return;
      }
      client[method](request, (error: any, response: any) => {
        if (error) reject(error);
        else resolve(response);
      });
    });
  }

  // --- Learning Path Methods ---

  async generateLearningPath(request: any): Promise<any> {
    return this.breaker.fire(
      this.learningClient,
      "GenerateLearningPath",
      request,
    );
  }

  async gradeAssessment(request: any): Promise<any> {
    return this.breaker.fire(this.learningClient, "GradeAssessment", request);
  }

  async generateAssessmentItems(request: any): Promise<any> {
    return this.breaker.fire(
      this.learningClient,
      "GenerateAssessmentItems",
      request,
    );
  }

  async suggestRemediation(request: any): Promise<any> {
    return this.breaker.fire(
      this.learningClient,
      "SuggestRemediation",
      request,
    );
  }

  // --- Content Generation Methods ---

  async generateClaims(request: any): Promise<any> {
    return this.breaker.fire(this.contentClient, "GenerateClaims", request);
  }

  async validateContent(request: any): Promise<any> {
    return this.breaker.fire(this.contentClient, "ValidateContent", request);
  }

  async analyzeContentNeeds(request: any): Promise<any> {
    return this.breaker.fire(
      this.contentClient,
      "AnalyzeContentNeeds",
      request,
    );
  }

  async generateExamples(request: any): Promise<any> {
    return this.breaker.fire(this.contentClient, "GenerateExamples", request);
  }

  async generateSimulation(request: any): Promise<any> {
    return this.breaker.fire(this.contentClient, "GenerateSimulation", request);
  }
}

// Singleton instance
export const aiClient = new AiClient();
