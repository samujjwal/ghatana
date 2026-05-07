import { FastifyInstance } from "fastify";
import fp from "fastify-plugin";
import { CredentialService } from "./service";
import { credentialRoutes } from "./routes";

export const credentialModule = fp(async (fastify: FastifyInstance) => {
    const service = new CredentialService(fastify.prisma);

    fastify.register(credentialRoutes, { service });

    fastify.log.info("Credentials module registered");
});
