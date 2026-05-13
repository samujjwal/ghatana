export type ProductSurfaceType =
  | 'backend-api'
  | 'web'
  | 'worker'
  | 'operator'
  | 'mobile-ios'
  | 'mobile-android'
  | 'sdk'
  | 'domain-pack';

export interface ProductSurface {
  readonly type: ProductSurfaceType;
  readonly adapter: string;
  readonly path: string;
  readonly implementationStatus?: 'implemented' | 'planned' | 'backend-only';
  readonly packagePath?: string;
  readonly [key: string]: unknown;
}
