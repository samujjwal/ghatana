/**
 * GraphQL Subscription Resolver - Real-Time Cost Updates
 *
 * <p><b>Purpose</b><br>
 * Implements GraphQL subscription resolvers for real-time cost updates,
 * alerts, and recommendation notifications via WebSocket.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const resolver = new CostSubscriptionResolver(pubsub);
 * 
 * // Client subscribes to cost updates
 * subscription {
 *   costUpdated {
 *     totalCost
 *     anomalies { date actualCost }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Subscriptions Implemented</b><br>
 * - costUpdated: Real-time cost analysis updates
 * - alertTriggered: New cost alerts
 * - recommendationGenerated: New recommendations
 *
 * <p><b>Publishing Events</b><br>
 * Services publish events via PubSub for real-time delivery:
 * - After CostAnalysisService completes analysis
 * - After CostNotificationService generates alerts
 * - After CostOptimizationService generates recommendations
 *
 * @doc.type class
 * @doc.purpose GraphQL subscription resolver for real-time updates
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { CostAnalysis } from '../../models/cost/CostAnalysis.dto';
import { CostAlert } from '../../services/cost/CostNotificationService';
import { CostRecommendation } from '../../models/cost/CostRecommendation.entity';

/**
 * PubSub interface for event publishing
 * Can be replaced with Apollo PubSub, GraphQL Subscriptions PubSub, etc.
 */
export interface PubSub {
  /**
   * Publish event to subscribers
   */
  publish(channel: string, payload: unknown): Promise<void>;

  /**
   * Subscribe to event channel
   */
  subscribe(channel: string): AsyncIterable<unknown>;

  /**
   * Unsubscribe from channel
   */
  unsubscribe(channel: string): void;
}

/**
 * Simple in-memory PubSub implementation for testing
 */
export class InMemoryPubSub implements PubSub {
  private subscribers: Map<string, Set<(payload: unknown) => void>> = new Map();

  async publish(channel: string, payload: unknown): Promise<void> {
    const channelSubscribers = this.subscribers.get(channel);
    if (channelSubscribers) {
      for (const subscriber of channelSubscribers) {
        try {
          subscriber(payload);
        } catch (error) {
          console.error(`Error in subscriber for ${channel}:`, error);
        }
      }
    }
  }

  subscribe(channel: string): AsyncIterable<unknown> {
    const self = this;
    let subscribers = this.subscribers.get(channel);

    if (!subscribers) {
      subscribers = new Set();
      this.subscribers.set(channel, subscribers);
    }

    return {
      async *[Symbol.asyncIterator]() {
        const queue: unknown[] = [];
        let resolve: ((value?: unknown) => void) | null = null;

        const subscriber = (payload: unknown) => {
          queue.push(payload);
          if (resolve) {
            resolve(undefined);
            resolve = null;
          }
        };

        subscribers!.add(subscriber);

        try {
          while (true) {
            if (queue.length > 0) {
              yield queue.shift();
            } else {
              await new Promise(r => {
                resolve = r as (value?: unknown) => void;
              });
            }
          }
        } finally {
          subscribers!.delete(subscriber);
        }
      },
    };
  }

  unsubscribe(channel: string): void {
    this.subscribers.delete(channel);
  }
}

/**
 * Subscription channel names
 */
const SUBSCRIPTION_CHANNELS = {
  COST_UPDATED: 'COST_UPDATED',
  ALERT_TRIGGERED: 'ALERT_TRIGGERED',
  RECOMMENDATION_GENERATED: 'RECOMMENDATION_GENERATED',
} as const;

/**
 * CostSubscriptionResolver implementation
 */
export class CostSubscriptionResolver {
  /**
   * Initialize resolver with PubSub
   *
   * @param pubsub Event publication system
   */
  constructor(private readonly pubsub: PubSub) {}

  /**
   * Subscribe to cost updates
   * Emits real-time cost analysis updates
   *
   * @returns Async iterable of cost analysis updates
   */
  costUpdated(): AsyncIterable<CostAnalysis> {
    return this.pubsub.subscribe(
      SUBSCRIPTION_CHANNELS.COST_UPDATED
    ) as AsyncIterable<CostAnalysis>;
  }

  /**
   * Subscribe to alerts
   * Emits real-time cost alerts
   *
   * @returns Async iterable of cost alerts
   */
  alertTriggered(): AsyncIterable<CostAlert> {
    return this.pubsub.subscribe(
      SUBSCRIPTION_CHANNELS.ALERT_TRIGGERED
    ) as AsyncIterable<CostAlert>;
  }

  /**
   * Subscribe to recommendations
   * Emits real-time recommendations
   *
   * @returns Async iterable of recommendations
   */
  recommendationGenerated(): AsyncIterable<CostRecommendation> {
    return this.pubsub.subscribe(
      SUBSCRIPTION_CHANNELS.RECOMMENDATION_GENERATED
    ) as AsyncIterable<CostRecommendation>;
  }

  /**
   * Publish cost update event
   * Called by CostAnalysisService after analysis completion
   *
   * @param analysis Cost analysis to publish
   */
  async publishCostUpdate(analysis: CostAnalysis): Promise<void> {
    await this.pubsub.publish(SUBSCRIPTION_CHANNELS.COST_UPDATED, analysis);
  }

  /**
   * Publish alert event
   * Called by CostNotificationService when alert is triggered
   *
   * @param alert Alert to publish
   */
  async publishAlert(alert: CostAlert): Promise<void> {
    await this.pubsub.publish(SUBSCRIPTION_CHANNELS.ALERT_TRIGGERED, alert);
  }

  /**
   * Publish recommendation event
   * Called by CostOptimizationService when recommendations are generated
   *
   * @param recommendation Recommendation to publish
   */
  async publishRecommendation(
    recommendation: CostRecommendation
  ): Promise<void> {
    await this.pubsub.publish(
      SUBSCRIPTION_CHANNELS.RECOMMENDATION_GENERATED,
      recommendation
    );
  }
}

/**
 * Event publisher utility for service integration
 * Services use this to notify subscribers
 */
export class CostEventPublisher {
  /**
   * Initialize event publisher
   *
   * @param subscriptionResolver Subscription resolver instance
   */
  constructor(
    private readonly subscriptionResolver: CostSubscriptionResolver
  ) {}

  /**
   * Publish cost analysis update
   *
   * @param analysis Cost analysis to publish
   */
  async publishCostAnalysis(analysis: CostAnalysis): Promise<void> {
    await this.subscriptionResolver.publishCostUpdate(analysis);
  }

  /**
   * Publish cost alert
   *
   * @param alert Alert to publish
   */
  async publishAlert(alert: CostAlert): Promise<void> {
    await this.subscriptionResolver.publishAlert(alert);
  }

  /**
   * Publish cost recommendation
   *
   * @param recommendation Recommendation to publish
   */
  async publishRecommendation(
    recommendation: CostRecommendation
  ): Promise<void> {
    await this.subscriptionResolver.publishRecommendation(recommendation);
  }

  /**
   * Publish multiple alerts at once
   *
   * @param alerts Alerts to publish
   */
  async publishAlerts(alerts: ReadonlyArray<CostAlert>): Promise<void> {
    for (const alert of alerts) {
      await this.publishAlert(alert);
    }
  }
}
