declare module 'web-vitals' {
  export function onCLS(cb: (metric: unknown) => void): void;
  export function onFID(cb: (metric: unknown) => void): void;
  export function onFCP(cb: (metric: unknown) => void): void;
  export function onLCP(cb: (metric: unknown) => void): void;
  export function onTTFB(cb: (metric: unknown) => void): void;
}
