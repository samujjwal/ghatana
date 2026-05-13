export type RouteLifecycle = 'stable' | 'preview' | 'boundary' | 'deprecated';

export interface RouteCapabilityContract {
  readonly path: string;
  readonly label: string;
  readonly description?: string;
  readonly iconName?: string;
  readonly group?: string;
  readonly minimumRole?: string;
  readonly lifecycle?: RouteLifecycle;
  readonly discoverable?: boolean;
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  readonly actions?: readonly string[];
  readonly cards?: readonly string[];
}

export interface EntitledActionContract {
  readonly id: string;
  readonly label: string;
  readonly routePath?: string;
  readonly requiresConfirmation?: boolean;
}

export interface EntitledCardContract {
  readonly id: string;
  readonly title: string;
  readonly routePath?: string;
  readonly surface?: 'dashboard' | 'detail' | 'sidebar' | 'modal';
}

export interface ProductRouteEntitlementContract {
  readonly product: string;
  readonly principalId: string;
  readonly tenantId: string;
  readonly role: string;
  readonly persona?: string;
  readonly tier?: string;
  readonly correlationId?: string;
  readonly routes: readonly RouteCapabilityContract[];
  readonly actions?: readonly EntitledActionContract[];
  readonly cards?: readonly EntitledCardContract[];
}

export interface RouteEntitlementValidationOptions {
  readonly expectedProduct: string;
  readonly requireTenant?: boolean;
  readonly requirePrincipal?: boolean;
}

export interface RouteEntitlementValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly entitlement?: ProductRouteEntitlementContract;
}

const allowedLifecycleValues = new Set<RouteLifecycle>(['stable', 'preview', 'boundary', 'deprecated']);
const allowedCardSurfaces = new Set<NonNullable<EntitledCardContract['surface']>>([
  'dashboard',
  'detail',
  'sidebar',
  'modal',
]);

export function validateRouteEntitlementPayload(
  payload: unknown,
  options: RouteEntitlementValidationOptions,
): RouteEntitlementValidationResult {
  const errors: string[] = [];
  const record = toRecord(payload);

  if (!record) {
    return { valid: false, errors: ['entitlement payload must be an object'] };
  }

  requireExactString(record, 'product', options.expectedProduct, errors);
  requireNonBlankString(record, 'role', errors);
  validateOptionalString(record, 'persona', errors);
  validateOptionalString(record, 'tier', errors);
  validateOptionalString(record, 'correlationId', errors);

  if (options.requirePrincipal !== false) {
    requireNonBlankString(record, 'principalId', errors);
  } else {
    validateOptionalString(record, 'principalId', errors);
  }

  if (options.requireTenant !== false) {
    requireNonBlankString(record, 'tenantId', errors);
  } else {
    validateOptionalString(record, 'tenantId', errors);
  }

  const routes = readArray(record.routes);
  const parsedRoutes: RouteCapabilityContract[] = [];
  if (!routes) {
    errors.push('entitlement.routes must be an array');
  } else {
    for (const [index, route] of routes.entries()) {
      const parsed = parseRouteCapability(route, index, errors);
      if (parsed) {
        parsedRoutes.push(parsed);
      }
    }
  }

  const parsedActions = parseOptionalActions(record.actions, errors);
  const parsedCards = parseOptionalCards(record.cards, errors);

  if (errors.length > 0) {
    return { valid: false, errors };
  }

  return {
    valid: true,
    errors: [],
    entitlement: {
      product: String(record.product),
      principalId: String(record.principalId ?? ''),
      tenantId: String(record.tenantId ?? ''),
      role: String(record.role),
      ...(typeof record.persona === 'string' ? { persona: record.persona } : {}),
      ...(typeof record.tier === 'string' ? { tier: record.tier } : {}),
      ...(typeof record.correlationId === 'string' ? { correlationId: record.correlationId } : {}),
      routes: parsedRoutes,
      ...(parsedActions ? { actions: parsedActions } : {}),
      ...(parsedCards ? { cards: parsedCards } : {}),
    },
  };
}

function parseRouteCapability(
  value: unknown,
  index: number,
  errors: string[],
): RouteCapabilityContract | null {
  const record = toRecord(value);
  const owner = `entitlement.routes[${index}]`;
  if (!record) {
    errors.push(`${owner} must be an object`);
    return null;
  }

  requireNonBlankString(record, 'path', errors, owner);
  requireNonBlankString(record, 'label', errors, owner);
  validateOptionalString(record, 'description', errors, owner);
  validateOptionalString(record, 'iconName', errors, owner);
  validateOptionalString(record, 'group', errors, owner);
  validateOptionalString(record, 'minimumRole', errors, owner);

  if (record.lifecycle !== undefined && (!isRouteLifecycle(record.lifecycle))) {
    errors.push(`${owner}.lifecycle must be one of stable, preview, boundary, deprecated`);
  }
  if (record.discoverable !== undefined && typeof record.discoverable !== 'boolean') {
    errors.push(`${owner}.discoverable must be boolean when present`);
  }
  validateOptionalStringArray(record, 'personas', errors, owner);
  validateOptionalStringArray(record, 'tiers', errors, owner);
  validateOptionalStringArray(record, 'actions', errors, owner);
  validateOptionalStringArray(record, 'cards', errors, owner);

  if (!isNonBlankString(record.path) || !isNonBlankString(record.label)) {
    return null;
  }

  return {
    path: record.path,
    label: record.label,
    ...(typeof record.description === 'string' ? { description: record.description } : {}),
    ...(typeof record.iconName === 'string' ? { iconName: record.iconName } : {}),
    ...(typeof record.group === 'string' ? { group: record.group } : {}),
    ...(typeof record.minimumRole === 'string' ? { minimumRole: record.minimumRole } : {}),
    ...(isRouteLifecycle(record.lifecycle) ? { lifecycle: record.lifecycle } : {}),
    ...(typeof record.discoverable === 'boolean' ? { discoverable: record.discoverable } : {}),
    ...(isStringArray(record.personas) ? { personas: record.personas } : {}),
    ...(isStringArray(record.tiers) ? { tiers: record.tiers } : {}),
    ...(isStringArray(record.actions) ? { actions: record.actions } : {}),
    ...(isStringArray(record.cards) ? { cards: record.cards } : {}),
  };
}

function parseOptionalActions(value: unknown, errors: string[]): readonly EntitledActionContract[] | null {
  if (value === undefined) {
    return null;
  }
  const actions = readArray(value);
  if (!actions) {
    errors.push('entitlement.actions must be an array when present');
    return null;
  }
  return actions.flatMap((action, index) => {
    const record = toRecord(action);
    const owner = `entitlement.actions[${index}]`;
    if (!record) {
      errors.push(`${owner} must be an object`);
      return [];
    }
    requireNonBlankString(record, 'id', errors, owner);
    requireNonBlankString(record, 'label', errors, owner);
    validateOptionalString(record, 'routePath', errors, owner);
    if (record.requiresConfirmation !== undefined && typeof record.requiresConfirmation !== 'boolean') {
      errors.push(`${owner}.requiresConfirmation must be boolean when present`);
    }
    if (!isNonBlankString(record.id) || !isNonBlankString(record.label)) {
      return [];
    }
    return [{
      id: record.id,
      label: record.label,
      ...(typeof record.routePath === 'string' ? { routePath: record.routePath } : {}),
      ...(typeof record.requiresConfirmation === 'boolean'
        ? { requiresConfirmation: record.requiresConfirmation }
        : {}),
    }];
  });
}

function parseOptionalCards(value: unknown, errors: string[]): readonly EntitledCardContract[] | null {
  if (value === undefined) {
    return null;
  }
  const cards = readArray(value);
  if (!cards) {
    errors.push('entitlement.cards must be an array when present');
    return null;
  }
  return cards.flatMap((card, index) => {
    const record = toRecord(card);
    const owner = `entitlement.cards[${index}]`;
    if (!record) {
      errors.push(`${owner} must be an object`);
      return [];
    }
    requireNonBlankString(record, 'id', errors, owner);
    requireNonBlankString(record, 'title', errors, owner);
    validateOptionalString(record, 'routePath', errors, owner);
    if (record.surface !== undefined && !isCardSurface(record.surface)) {
      errors.push(`${owner}.surface must be one of dashboard, detail, sidebar, modal`);
    }
    if (!isNonBlankString(record.id) || !isNonBlankString(record.title)) {
      return [];
    }
    return [{
      id: record.id,
      title: record.title,
      ...(typeof record.routePath === 'string' ? { routePath: record.routePath } : {}),
      ...(isCardSurface(record.surface) ? { surface: record.surface } : {}),
    }];
  });
}

function requireExactString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  expected: string,
  errors: string[],
): void {
  if (record[key] !== expected) {
    errors.push(`entitlement.${key} must be ${expected}`);
  }
}

function requireNonBlankString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
  owner = 'entitlement',
): void {
  if (!isNonBlankString(record[key])) {
    errors.push(`${owner}.${key} must be a non-empty string`);
  }
}

function validateOptionalString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
  owner = 'entitlement',
): void {
  if (record[key] !== undefined && typeof record[key] !== 'string') {
    errors.push(`${owner}.${key} must be a string when present`);
  }
}

function validateOptionalStringArray(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
  owner: string,
): void {
  if (record[key] !== undefined && !isStringArray(record[key])) {
    errors.push(`${owner}.${key} must be a string array when present`);
  }
}

function toRecord(value: unknown): Readonly<Record<string, unknown>> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Readonly<Record<string, unknown>>;
}

function readArray(value: unknown): readonly unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isStringArray(value: unknown): value is readonly string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

function isRouteLifecycle(value: unknown): value is RouteLifecycle {
  return typeof value === 'string' && allowedLifecycleValues.has(value as RouteLifecycle);
}

function isCardSurface(value: unknown): value is NonNullable<EntitledCardContract['surface']> {
  return typeof value === 'string' && allowedCardSurfaces.has(value as NonNullable<EntitledCardContract['surface']>);
}
