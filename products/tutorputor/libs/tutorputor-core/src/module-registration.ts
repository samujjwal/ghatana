/**
 * Standardized Module Registration Utility
 * 
 * Provides consistent pattern for registering Fastify modules
 * with proper error handling, health checks, and lifecycle management.
 * 
 * @doc.type utility
 * @doc.purpose Standardized module registration pattern
 * @doc.layer platform
 */

import type { FastifyInstance, FastifyPluginOptions } from 'fastify';
import { createStandaloneLogger } from './logger';

const logger = createStandaloneLogger({ component: 'ModuleRegistration' });

export interface ModuleConfig {
  name: string;
  version: string;
  prefix?: string;
  healthCheck?: () => Promise<boolean>;
  onRegister?: (fastify: FastifyInstance) => Promise<void>;
  onReady?: (fastify: FastifyInstance) => Promise<void>;
  onClose?: (fastify: FastifyInstance) => Promise<void>;
}

export interface ModuleMetadata {
  name: string;
  version: string;
  registeredAt: Date;
  status: 'registered' | 'ready' | 'error' | 'closed';
  error?: string;
}

/**
 * Registry of all registered modules
 */
const moduleRegistry = new Map<string, ModuleMetadata>();

/**
 * Creates a standardized Fastify plugin with consistent lifecycle management
 */
export function createModule(config: ModuleConfig) {
  return async function modulePlugin(
    fastify: FastifyInstance,
    opts: FastifyPluginOptions,
  ): Promise<void> {
    const { name, version, prefix, healthCheck, onRegister, onReady, onClose } = config;

    logger.info({
      message: 'Registering module',
      module: name,
      version,
      prefix,
    });

    // Register module metadata
    const metadata: ModuleMetadata = {
      name,
      version,
      registeredAt: new Date(),
      status: 'registered',
    };
    moduleRegistry.set(name, metadata);

    try {
      // Execute custom registration logic
      if (onRegister) {
        await onRegister(fastify);
      }

      // Register health check endpoint if provided
      if (healthCheck) {
        const healthPath = prefix ? `${prefix}/health` : `/health/${name}`;
        fastify.get(healthPath, async (request, reply) => {
          try {
            const healthy = await healthCheck();
            reply.code(healthy ? 200 : 503).send({
              module: name,
              version,
              status: healthy ? 'healthy' : 'unhealthy',
              timestamp: new Date().toISOString(),
            });
          } catch (error) {
            logger.error({
              message: 'Health check failed',
              module: name,
              error: error instanceof Error ? error.message : String(error),
            });
            reply.code(503).send({
              module: name,
              version,
              status: 'error',
              error: error instanceof Error ? error.message : 'Unknown error',
              timestamp: new Date().toISOString(),
            });
          }
        });
      }

      // Register ready hook
      if (onReady) {
        fastify.addHook('onReady', async () => {
          try {
            await onReady(fastify);
            metadata.status = 'ready';
            logger.info({
              message: 'Module ready',
              module: name,
              version,
            });
          } catch (error) {
            metadata.status = 'error';
            metadata.error = error instanceof Error ? error.message : String(error);
            logger.error({
              message: 'Module ready hook failed',
              module: name,
              error: metadata.error,
            });
            throw error;
          }
        });
      }

      // Register close hook
      if (onClose) {
        fastify.addHook('onClose', async () => {
          try {
            await onClose(fastify);
            metadata.status = 'closed';
            logger.info({
              message: 'Module closed',
              module: name,
              version,
            });
          } catch (error) {
            logger.error({
              message: 'Module close hook failed',
              module: name,
              error: error instanceof Error ? error.message : String(error),
            });
          }
        });
      }

      logger.info({
        message: 'Module registered successfully',
        module: name,
        version,
      });
    } catch (error) {
      metadata.status = 'error';
      metadata.error = error instanceof Error ? error.message : String(error);
      
      logger.error({
        message: 'Module registration failed',
        module: name,
        error: metadata.error,
      });
      
      throw error;
    }
  };
}

/**
 * Gets metadata for a registered module
 */
export function getModuleMetadata(name: string): ModuleMetadata | undefined {
  return moduleRegistry.get(name);
}

/**
 * Gets all registered modules
 */
export function getAllModules(): ModuleMetadata[] {
  return Array.from(moduleRegistry.values());
}

/**
 * Gets modules by status
 */
export function getModulesByStatus(
  status: ModuleMetadata['status'],
): ModuleMetadata[] {
  return Array.from(moduleRegistry.values()).filter(m => m.status === status);
}

/**
 * Checks if all modules are ready
 */
export function areAllModulesReady(): boolean {
  const modules = Array.from(moduleRegistry.values());
  return modules.length > 0 && modules.every(m => m.status === 'ready');
}

/**
 * Creates a service module with database and dependencies
 */
export function createServiceModule<T>(config: {
  name: string;
  version: string;
  prefix?: string;
  createService: (fastify: FastifyInstance) => Promise<T>;
  registerRoutes?: (fastify: FastifyInstance, service: T) => Promise<void>;
  healthCheck?: (service: T) => Promise<boolean>;
}) {
  let service: T;

  return createModule({
    name: config.name,
    version: config.version,
    prefix: config.prefix,
    
    onRegister: async (fastify) => {
      // Create service instance
      service = await config.createService(fastify);
      
      // Decorate Fastify with service
      fastify.decorate(config.name, service as unknown as object);
      
      // Register routes if provided
      if (config.registerRoutes) {
        await config.registerRoutes(fastify, service);
      }
    },
    
    healthCheck: config.healthCheck
      ? async () => config.healthCheck!(service)
      : undefined,
  });
}

/**
 * Helper to create a simple health check that tests database connectivity
 */
export function createDatabaseHealthCheck(
  prisma: any,
): () => Promise<boolean> {
  return async () => {
    try {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    } catch (error) {
      logger.error({
        message: 'Database health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
      return false;
    }
  };
}

/**
 * Helper to create a composite health check from multiple checks
 */
export function createCompositeHealthCheck(
  checks: Array<() => Promise<boolean>>,
): () => Promise<boolean> {
  return async () => {
    try {
      const results = await Promise.all(checks.map(check => check()));
      return results.every(result => result === true);
    } catch (error) {
      logger.error({
        message: 'Composite health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
      return false;
    }
  };
}
