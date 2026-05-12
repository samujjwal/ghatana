export interface RouteEntitlement {
  path: string;
  label: string;
  minimumRole: string;
  personas: string[];
  tiers: string[];
  actions: string[];
  cards: string[];
  capabilityKey?: string;
}

export interface ActionEntitlement {
  id: string;
  label: string;
  routePath: string;
}

export interface CardEntitlement {
  id: string;
  title: string;
  routePath: string;
  surface: string;
}

export interface ProductRouteEntitlement {
  product: string;
  principalId: string;
  tenantId: string;
  role: string;
  persona?: string;
  tier?: string;
  correlationId?: string;
  routes: RouteEntitlement[];
  actions: ActionEntitlement[];
  cards: CardEntitlement[];
}
