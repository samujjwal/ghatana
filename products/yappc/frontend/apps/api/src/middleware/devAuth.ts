/**
 * Development Auth Bypass Middleware
 * 
 * Bypasses authentication in development mode.
 * Uses the first admin user from the database for dev context.
 * DO NOT USE IN PRODUCTION.
 */
import { FastifyInstance } from 'fastify';
import prisma from '../db';

let devUserId: string | null = null;

async function getDevUser() {
    if (!devUserId) {
        // Try database first; if DB/table missing, fall back to ENV or an in-memory dev id
        try {
            // Get the first admin user from database
            const adminUser = await prisma.user.findFirst({
                where: { role: 'ADMIN' },
                select: { id: true, email: true }
            });

            if (adminUser) {
                devUserId = adminUser.id;
                console.log(`[DevAuth] Using admin user: ${adminUser.email} (${adminUser.id})`);
            } else {
                // Fallback: create a dev user if none exists
                const newUser = await prisma.user.create({
                    data: {
                        email: process.env.DEV_USER_EMAIL || 'dev@yappc.com',
                        name: 'Dev User',
                        role: 'ADMIN',
                    }
                });
                devUserId = newUser.id;
                console.log(`[DevAuth] Created new admin user: ${newUser.email} (${newUser.id})`);
            }
        } catch (err: unknown) {
            // If Prisma reports missing table or DB not available, gracefully fallback
            const envId = process.env.DEV_USER_ID;
            if (envId) {
                devUserId = envId;
                console.warn('[DevAuth] Prisma error; using DEV_USER_ID from env as fallback. Error:', err?.message || err);
            } else {
                // Use a deterministic synthetic id so downstream code can run during dev
                devUserId = `dev-${Date.now().toString(36)}`;
                console.warn('[DevAuth] Prisma error; no DEV_USER_ID set. Using generated in-memory dev id:', devUserId);
            }
        }
    }
    return devUserId;
}

export async function devAuthBypass(fastify: FastifyInstance) {
    // Skip auth for all /api/* routes UNLESS explicitly in production
    const isProduction = process.env.NODE_ENV === 'production';

    console.log('[DevAuth] Plugin loaded. NODE_ENV:', process.env.NODE_ENV, 'isProduction:', isProduction);

    if (!isProduction) {
        // Initialize dev user on startup
        const userId = await getDevUser();
        console.log('[DevAuth] Initialized with userId:', userId);
        console.log('[DevAuth] Registering onRequest hook...');

        fastify.addHook('onRequest', async (request, reply) => {
            // Inject admin user for ALL requests in dev mode
            const userId = await getDevUser();
            request.user = {
                userId: userId!,
                email: 'admin@yappc.com',
                role: 'ADMIN'
            };
            // Log first few chars of URL to avoid spam
            const shortUrl = request.url.substring(0, 30);
            console.log(`[DevAuth] ✓ Set user for ${request.method} ${shortUrl}... → userId: ${request.user.userId}`);
        });

        console.log('[DevAuth] Hook registered successfully');
    } else {
        console.log('[DevAuth] Skipped - running in production mode');
    }
}

// Extend Fastify Request type
declare module 'fastify' {
    interface FastifyRequest {
        user?: {
            userId: string;
            email: string;
            role: string;
        };
    }
}
