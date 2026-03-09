import { FastifyInstance } from 'fastify';
import fp from 'fastify-plugin';
import { PaymentService } from './service';
import { paymentRoutes } from './routes';

export const paymentModule = fp(async (fastify: FastifyInstance) => {
    const stripeKey = process.env.STRIPE_SECRET_KEY || 'sk_test_mock';
    const service = new PaymentService(fastify.prisma, stripeKey);

    fastify.register(paymentRoutes, { service });

    fastify.log.info('Payments module registered');
});
