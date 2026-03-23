/**
 * ActiveJ Integration Module
 *
 * Provides seamless integration between @ghatana/realtime WebSocket infrastructure
 * and ActiveJ backend services. Enables real-time streaming with topic-based
 * subscriptions, multi-tenancy, and request-response patterns.
 *
 * @doc.type module
 * @doc.purpose ActiveJ backend streaming integration
 * @doc.layer platform
 * @doc.pattern Adapter
 */

// Client exports
export {
    ActiveJStreamClient,
    createAsyncStream,
    type ActiveJStreamConfig,
    type ActiveJStreamMessage,
    type StreamSubscription,
} from './ActiveJStreamClient';

// React hook exports
export {
    useActiveJStream,
    useActiveJSubscription,
    type ActiveJConnectionState,
    type UseActiveJStreamOptions,
    type UseActiveJStreamReturn,
} from './useActiveJStream';
