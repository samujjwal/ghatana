import { FastifyInstance } from "fastify";
import fp from "fastify-plugin";
import { CredentialService } from "./service";
import { credentialRoutes } from "./routes";

export const credentialModule = fp(async (fastify: any) => {
    const service = new CredentialService(fastify.prisma);

    fastify.register(credentialRoutes as any, { service } as any);

    fastify.log.info("Credentials module registered");
});
