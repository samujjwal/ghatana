/**
 * Intent-first Canvas Generator
 *
 * Generates canvas node previews from natural language descriptions.
 *
 * @doc.type service
 * @doc.purpose Generate canvas nodes from natural language intent
 * @doc.layer product
 * @doc.pattern Service
 */

export interface IntentCanvasNode {
  id: string;
  type: 'page' | 'api' | 'component' | 'data' | 'service' | 'event' | 'integration' | 'user' | 'cache' | 'queue';
  label: string;
  description?: string;
  x: number;
  y: number;
  meta?: Record<string, unknown>;
}

export interface IntentCanvasConnection {
  id: string;
  from: string;
  to: string;
  label?: string;
  type?: 'sync' | 'async' | 'data' | 'auth';
}

export interface IntentCanvasPreview {
  nodes: IntentCanvasNode[];
  connections: IntentCanvasConnection[];
  confidence: number;
  detectedFeatures: string[];
  detectedTechStack: string[];
  estimatedComplexity: 'low' | 'medium' | 'high';
  rationale: string[];
}

export interface IntentParseResult {
  projectType: 'FULL_STACK' | 'BACKEND' | 'MOBILE' | 'UI' | 'DESKTOP';
  features: string[];
  techStack: string[];
  projectName?: string;
}

const FEATURE_PATTERNS: Array<{ pattern: RegExp; feature: string }> = [
  { pattern: /auth|login|sign.?up|user|identity|sso|oauth/i, feature: 'User Authentication' },
  { pattern: /dashboard|admin|panel|analytics|metric|chart/i, feature: 'Dashboard' },
  { pattern: /payment|stripe|checkout|billing|invoice|subscription/i, feature: 'Payment Integration' },
  { pattern: /search|filter|query|find/i, feature: 'Search & Filtering' },
  { pattern: /crud|create|edit|delete|update.*record|manage.*record/i, feature: 'CRUD Operations' },
  { pattern: /api|rest|graphql|endpoint|grpc|webhook/i, feature: 'API Layer' },
  { pattern: /database|db|storage|postgres|mongo|sql|redis|cache/i, feature: 'Database' },
  { pattern: /upload|image|file|asset|media/i, feature: 'File Uploads' },
  { pattern: /notification|email|alert|push|sms/i, feature: 'Notifications' },
  { pattern: /chat|message|real.?time|websocket|socket/i, feature: 'Real-time Features' },
  { pattern: /todo|task|list|kanban|project|workflow/i, feature: 'Task Management' },
  { pattern: /blog|post|article|content|cms|page/i, feature: 'CMS Features' },
  { pattern: /shop|store|commerce|cart|product|e.?commerce/i, feature: 'E-commerce' },
  { pattern: /analytics|metric|track|monitor|telemetry|observability/i, feature: 'Analytics' },
  { pattern: /cache|redis|memcached|caching/i, feature: 'Caching' },
  { pattern: /queue|message.?queue|kafka|rabbitmq|sqs|event.?stream/i, feature: 'Messaging' },
  { pattern: /ai|ml|model|predict|classify|embedding|llm/i, feature: 'AI/ML' },
];

const TECH_STACK_PATTERNS: Array<{ pattern: RegExp; tech: string }> = [
  { pattern: /react|next\.?js/i, tech: 'React' },
  { pattern: /vue|nuxt/i, tech: 'Vue' },
  { pattern: /angular/i, tech: 'Angular' },
  { pattern: /svelte/i, tech: 'Svelte' },
  { pattern: /node\.?js|express|nest/i, tech: 'Node.js' },
  { pattern: /python|django|flask|fastapi/i, tech: 'Python' },
  { pattern: /go|golang/i, tech: 'Go' },
  { pattern: /java|spring/i, tech: 'Java' },
  { pattern: /rust/i, tech: 'Rust' },
  { pattern: /postgres|psql/i, tech: 'PostgreSQL' },
  { pattern: /mongo/i, tech: 'MongoDB' },
  { pattern: /redis/i, tech: 'Redis' },
  { pattern: /graphql/i, tech: 'GraphQL' },
  { pattern: /docker|kubernetes|k8s/i, tech: 'Container' },
  { pattern: /aws|gcp|azure/i, tech: 'Cloud' },
];

const TYPE_PATTERNS: Array<{ pattern: RegExp; type: IntentParseResult['projectType'] }> = [
  { pattern: /\b(mobile|ios|android|react\s*native|flutter)\b/i, type: 'MOBILE' },
  { pattern: /\b(desktop|electron|tauri|windows|macos|linux)\b/i, type: 'DESKTOP' },
  { pattern: /\b(backend|server|api|microservice)\b/i, type: 'BACKEND' },
  { pattern: /\b(frontend|ui|component|widget|library)\b/i, type: 'UI' },
];

export function parseIntent(input: string): IntentParseResult {
  const lower = input.toLowerCase();
  let projectType: IntentParseResult['projectType'] = 'FULL_STACK';
  for (const { pattern, type } of TYPE_PATTERNS) {
    if (pattern.test(lower)) { projectType = type; break; }
  }
  const features: string[] = [];
  for (const { pattern, feature } of FEATURE_PATTERNS) {
    if (pattern.test(lower) && !features.includes(feature)) features.push(feature);
  }
  const techStack: string[] = [];
  for (const { pattern, tech } of TECH_STACK_PATTERNS) {
    if (pattern.test(lower) && !techStack.includes(tech)) techStack.push(tech);
  }
  let projectName: string | undefined;
  const quoted = input.match(/["']([^"']+)["']/);
  if (quoted) projectName = quoted[1];
  else {
    const called = input.match(/called\s+(\w+)/i);
    if (called) projectName = called[1];
    else {
      const words = input.replace(/[^a-zA-Z\s]/g, '').split(/\s+/)
        .filter(w => w.length > 3 && !['want','need','build','create','make','with','that','have','this','from','using'].includes(w.toLowerCase()))
        .slice(0, 2);
      if (words.length) projectName = words.map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('');
    }
  }
  return { projectType, features, techStack, projectName };
}

const COL_WIDTH = 280;
const ROW_HEIGHT = 160;
const BASE_X = 80;
const BASE_Y = 60;

function makeId(prefix: string, index: number): string {
  return `${prefix}-${Date.now()}-${index}`;
}

export function generateNodes(parsed: IntentParseResult): IntentCanvasNode[] {
  const nodes: IntentCanvasNode[] = [];
  let row = 0, col = 0;
  const maxCols = 3;
  function add(type: IntentCanvasNode['type'], label: string, description?: string) {
    nodes.push({ id: makeId(type, nodes.length), type, label, description, x: BASE_X + col * COL_WIDTH, y: BASE_Y + row * ROW_HEIGHT });
    col++; if (col >= maxCols) { col = 0; row++; }
  }

  if (parsed.projectType === 'UI' || parsed.projectType === 'FULL_STACK' || parsed.projectType === 'MOBILE')
    add('page', parsed.projectType === 'MOBILE' ? 'Home Screen' : 'Home Page', 'Primary entry');

  if (parsed.features.includes('User Authentication')) { add('page', parsed.projectType === 'MOBILE' ? 'Login Screen' : 'Login Page'); add('api', 'Auth Service', 'Identity, tokens, SSO'); }
  if (parsed.features.includes('Dashboard')) { add('page', 'Dashboard'); add('component', 'Stats Widget'); add('component', 'Charts'); }
  if (parsed.features.includes('CRUD Operations')) { add('page', 'List View'); add('page', 'Detail View'); add('api', 'CRUD API'); }
  if (parsed.features.includes('Payment Integration')) { add('page', 'Checkout'); add('api', 'Payment Service'); add('integration', 'Payment Gateway'); }
  if (parsed.features.includes('Search & Filtering')) { add('component', 'Search Bar'); add('component', 'Filter Panel'); add('component', 'Results Grid'); }
  if (parsed.features.includes('E-commerce')) { add('page', 'Product Catalog'); add('page', 'Cart'); add('service', 'Order Service'); }
  if (parsed.features.includes('CMS Features')) { add('page', 'Content List'); add('page', 'Content Editor'); add('service', 'Content Service'); }
  if (parsed.features.includes('Task Management')) { add('page', 'Board View'); add('page', 'Task Detail'); add('service', 'Workflow Engine'); }
  if (parsed.features.includes('Real-time Features')) { add('event', 'WebSocket Hub'); add('service', 'Presence Service'); }
  if (parsed.features.includes('Notifications')) { add('service', 'Notification Service'); add('queue', 'Notification Queue'); }
  if (parsed.features.includes('File Uploads')) { add('component', 'Uploader'); add('service', 'Asset Service'); add('data', 'Blob Storage'); }
  if (parsed.features.includes('Analytics')) { add('component', 'Analytics Panel'); add('service', 'Telemetry Ingest'); add('data', 'Time-series Store'); }
  if (parsed.features.includes('Caching')) add('cache', 'Cache Layer', 'Redis / in-memory');
  if (parsed.features.includes('Messaging')) { add('queue', 'Message Queue'); add('event', 'Event Bus'); }
  if (parsed.features.includes('AI/ML')) { add('service', 'Inference Service'); add('data', 'Feature Store'); }

  const hasBackend = parsed.projectType === 'BACKEND' || parsed.projectType === 'FULL_STACK' || parsed.features.includes('API Layer');
  if (hasBackend && !nodes.some(n => n.label.includes('API'))) add('api', 'REST API', 'Primary API surface');

  const hasDb = parsed.features.includes('Database') || parsed.features.includes('CRUD Operations') || parsed.features.includes('E-commerce') || parsed.features.includes('CMS Features') || parsed.features.includes('Task Management') || parsed.projectType === 'BACKEND' || parsed.projectType === 'FULL_STACK';
  if (hasDb && !nodes.some(n => n.label.includes('Database') || n.type === 'data')) add('data', 'Database', 'Primary store');

  if (parsed.projectType === 'MOBILE' && nodes.length < 3) { add('page', 'Main Navigation'); add('page', 'Profile Screen'); }
  if (parsed.projectType === 'DESKTOP' && nodes.length < 3) { add('page', 'Main Window'); add('component', 'Menu Bar'); add('component', 'Status Bar'); }
  if (nodes.length === 0) { add('page', 'Main View'); add('component', 'Core Component'); }
  return nodes;
}

export function generateConnections(nodes: IntentCanvasNode[]): IntentCanvasConnection[] {
  const conns: IntentCanvasConnection[] = [];
  let i = 0;
  const connect = (fromLabel: string, toLabel: string, label?: string, type?: IntentCanvasConnection['type']) => {
    const from = nodes.find(n => n.label === fromLabel)?.id;
    const to = nodes.find(n => n.label === toLabel)?.id;
    if (from && to && from !== to) conns.push({ id: makeId('conn', i++), from, to, label, type });
  };
  const pages = nodes.filter(n => n.type === 'page' || n.type === 'component');
  const apis = nodes.filter(n => n.type === 'api' || n.type === 'service');
  for (const p of pages.slice(0, 2)) for (const a of apis.slice(0, 2)) connect(p.label, a.label, 'calls', 'sync');
  const dbs = nodes.filter(n => n.type === 'data');
  for (const a of apis.slice(0, 2)) for (const d of dbs.slice(0, 2)) connect(a.label, d.label, 'persists', 'data');
  const authPage = nodes.find(n => n.label.includes('Login'));
  const authSvc = nodes.find(n => n.label.includes('Auth Service'));
  if (authPage && authSvc) connect(authPage.label, authSvc.label, 'authenticates', 'auth');
  const cache = nodes.find(n => n.label.includes('Cache'));
  if (cache && apis.length) connect(apis[0].label, cache.label, 'reads', 'sync');
  const queue = nodes.find(n => n.label.includes('Queue'));
  if (queue && apis.length) connect(apis[0].label, queue.label, 'publishes', 'async');
  const ws = nodes.find(n => n.label.includes('WebSocket'));
  if (ws && pages.length) connect(pages[0].label, ws.label, 'subscribes', 'async');
  return conns;
}

export function generatePreview(intentText: string): IntentCanvasPreview {
  const parsed = parseIntent(intentText);
  const nodes = generateNodes(parsed);
  const connections = generateConnections(nodes);
  const featureCount = parsed.features.length;
  const complexity: IntentCanvasPreview['estimatedComplexity'] = featureCount <= 3 ? 'low' : featureCount <= 6 ? 'medium' : 'high';
  const confidence = Math.min(0.95, 0.5 + featureCount * 0.05 + (parsed.techStack.length ? 0.1 : 0));
  const rationale: string[] = [
    `Detected project type: ${parsed.projectType}`,
    `Identified ${featureCount} feature${featureCount === 1 ? '' : 's'}: ${parsed.features.join(', ') || 'none'}`,
    `Suggested stack: ${parsed.techStack.join(', ') || 'generic'}`,
    `Estimated complexity: ${complexity}`,
  ];
  return { nodes, connections, confidence, detectedFeatures: parsed.features, detectedTechStack: parsed.techStack, estimatedComplexity: complexity, rationale };
}
