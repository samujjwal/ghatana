/**
 * Learner Profile gRPC Runtime
 *
 * Optional runtime wrapper for starting and stopping the learner-profile gRPC server.
 *
 * @doc.type module
 * @doc.purpose Manage learner-profile gRPC server lifecycle in platform startup
 * @doc.layer product
 * @doc.pattern Runtime Adapter
 */

import type { FastifyBaseLogger } from "fastify";
import type { Server } from "@grpc/grpc-js";
import {
  bindLearnerProfileGrpcServer,
  createLearnerProfileGrpcServer,
} from "./grpc-service.js";
import type { createLearnerProfileService } from "./learner-profile-service.js";

type LearnerProfileService = ReturnType<typeof createLearnerProfileService>;

export interface LearnerProfileGrpcRuntime {
  server: Server;
  address: string;
  port: number;
  stop: () => Promise<void>;
}

export async function startLearnerProfileGrpcRuntime(options: {
  learnerProfileService: LearnerProfileService;
  address: string;
  logger: FastifyBaseLogger;
}): Promise<LearnerProfileGrpcRuntime> {
  const server = createLearnerProfileGrpcServer(options.learnerProfileService);
  const port = await bindLearnerProfileGrpcServer(server, options.address);
  server.start();

  options.logger.info(
    { grpcAddress: options.address, grpcPort: port },
    "Learner profile gRPC server started",
  );

  return {
    server,
    address: options.address,
    port,
    stop: () =>
      new Promise((resolve, reject) => {
        server.tryShutdown((error) => {
          if (error) {
            reject(error);
            return;
          }
          options.logger.info(
            { grpcAddress: options.address, grpcPort: port },
            "Learner profile gRPC server stopped",
          );
          resolve();
        });
      }),
  };
}
