/**
 * Run Phase — Execute pipelines and deployments
 *
 * Pipeline execution, deployment management, and agent workflow runs.
 * Admin tools (prompt versions, A/B testing) surface as context-sensitive
 * panels within this phase via the header actions menu.
 *
 * @doc.type route
 * @doc.purpose Run phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

export { default, ErrorBoundary } from './deploy';
