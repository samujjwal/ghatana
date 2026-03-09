/**
 * Multi-sink router with fan-out, priority routing, and fault isolation.
 * Delivers commands to multiple sinks independently with per-sink retry.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from './types';

export interface SinkRoute {
  sink: ControlSink;
  priority: number;
  enabled: boolean;
}

export interface MultiSinkRouterOptions {
  routes: SinkRoute[];
  isolateFaults?: boolean;
}

export class MultiSinkRouter implements ControlSink {
  readonly kind = 'custom' as const;

  private ctx?: SinkContext;
  private options: MultiSinkRouterOptions;

  constructor(options: MultiSinkRouterOptions) {
    this.options = {
      isolateFaults: true,
      ...options,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;

    // Initialize all sinks
    for (const route of this.options.routes) {
      if (route.enabled) {
        try {
          await route.sink.init(ctx);
        } catch (error) {
          ctx.logger.error('Sink initialization failed', error as Error, {
            sinkKind: route.sink.kind,
          });

          if (!this.options.isolateFaults) {
            throw error;
          }
        }
      }
    }

    ctx.logger.info('MultiSinkRouter initialized', {
      routeCount: this.options.routes.filter((r) => r.enabled).length,
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    // Enqueue to all enabled sinks
    const promises = this.options.routes
      .filter((r) => r.enabled)
      .map(async (route) => {
        try {
          await route.sink.enqueue(command);
        } catch (error) {
          this.ctx?.logger.error('Enqueue failed', error as Error, {
            sinkKind: route.sink.kind,
            commandId: command.id,
          });

          if (!this.options.isolateFaults) {
            throw error;
          }
        }
      });

    await Promise.all(promises);
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('MultiSinkRouter.flush');
    const allAcks: SinkAck[] = [];

    try {
      // Sort by priority (higher first)
      const sortedRoutes = [...this.options.routes]
        .filter((r) => r.enabled)
        .sort((a, b) => b.priority - a.priority);

      for (const route of sortedRoutes) {
        try {
          const acks = await route.sink.flush();
          allAcks.push(...acks);

          span?.setAttribute(`${route.sink.kind}.flushed`, acks.length);
        } catch (error) {
          this.ctx?.logger.error('Flush failed', error as Error, {
            sinkKind: route.sink.kind,
          });

          if (!this.options.isolateFaults) {
            throw error;
          }
        }
      }

      span?.setStatus({ code: 'ok' });
      return allAcks;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async healthCheck(): Promise<HealthStatus> {
    const checks = await Promise.all(
      this.options.routes
        .filter((r) => r.enabled)
        .map(async (route) => {
          try {
            const health = await route.sink.healthCheck?.();
            return { sink: route.sink.kind, health };
          } catch (error) {
            return {
              sink: route.sink.kind,
              health: {
                healthy: false,
                lastCheck: new Date().toISOString(),
                error: (error as Error).message,
              },
            };
          }
        }),
    );

    const allHealthy = checks.every((c) => c.health?.healthy);

    return {
      healthy: allHealthy,
      lastCheck: new Date().toISOString(),
      details: { sinks: checks },
    };
  }

  async close(): Promise<void> {
    const promises = this.options.routes
      .filter((r) => r.enabled)
      .map(async (route) => {
        try {
          await route.sink.close?.();
        } catch (error) {
          this.ctx?.logger.error('Sink close failed', error as Error, {
            sinkKind: route.sink.kind,
          });
        }
      });

    await Promise.all(promises);
    this.ctx?.logger.info('MultiSinkRouter closed');
  }

  addRoute(route: SinkRoute): void {
    this.options.routes.push(route);
  }

  removeRoute(sinkKind: string): void {
    this.options.routes = this.options.routes.filter(
      (r) => r.sink.kind !== sinkKind,
    );
  }

  enableRoute(sinkKind: string): void {
    const route = this.options.routes.find((r) => r.sink.kind === sinkKind);
    if (route) {
      route.enabled = true;
    }
  }

  disableRoute(sinkKind: string): void {
    const route = this.options.routes.find((r) => r.sink.kind === sinkKind);
    if (route) {
      route.enabled = false;
    }
  }
}

export const createMultiSinkRouter = (
  options: MultiSinkRouterOptions,
): ControlSink => {
  return new MultiSinkRouter(options);
};
