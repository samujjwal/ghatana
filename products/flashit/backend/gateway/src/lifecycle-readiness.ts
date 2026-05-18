export const flashItGatewayLifecycleReadiness = {
  productId: 'flashit',
  surface: 'backend-api',
  adapter: 'pnpm-node-api',
  requiredCommands: ['type-check', 'test'] as const,
  lifecycleExecutionAllowed: false,
} as const;

export type FlashItGatewayLifecycleReadiness = typeof flashItGatewayLifecycleReadiness;
