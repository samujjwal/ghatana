import { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { optionalAuthenticate, AuthRequest } from '../middleware/auth.middleware';
import { guardianEventSchema, GuardianEvent } from '../types/guardian-events';
import { logger } from '../utils/logger';
import { storeGuardianEvents } from '../services/events-store.service';

interface EventsBody {
    events?: GuardianEvent[];
}

const eventsRoutes: FastifyPluginAsync = async (fastify) => {
    fastify.addHook('preHandler', optionalAuthenticate);

    fastify.post('/', async (request: AuthRequest & FastifyRequest, reply: FastifyReply) => {
        try {
            const body = request.body as unknown as GuardianEvent | GuardianEvent[] | EventsBody;

            const eventsArray: GuardianEvent[] = Array.isArray((body as any).events)
                ? (body as EventsBody).events || []
                : Array.isArray(body)
                    ? (body as GuardianEvent[])
                    : body
                        ? [body as GuardianEvent]
                        : [];

            if (eventsArray.length === 0) {
                return reply.status(400).send({ error: 'No events provided' });
            }

            const validatedEvents: GuardianEvent[] = [];

            for (const event of eventsArray) {
                const result = guardianEventSchema.safeParse(event);
                if (!result.success) {
                    return reply.status(400).send({
                        error: 'Invalid event payload',
                        details: result.error.issues,
                    });
                }
                validatedEvents.push(result.data);
            }

            await storeGuardianEvents(validatedEvents);

            for (const event of validatedEvents) {
                logger.info('GuardianEvent received', {
                    kind: event.kind,
                    subtype: event.subtype,
                    source: event.source,
                });
            }

            return reply.status(202).send({ accepted: validatedEvents.length });
        } catch (error) {
            logger.error('Error processing events', { error });
            return reply.status(500).send({ error: 'Failed to process events' });
        }
    });
};

export default eventsRoutes;
