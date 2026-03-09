// Lightweight compat shim: avoid re-exporting the scoped @reactflow packages
// which would pull their d.ts (and cause @types/react version conflicts).
declare module 'reactflow' {
  const _default: unknown;
  export default _default;
}

declare module 'reactflow/dist/esm' {
  const _default: unknown;
  export default _default;
}
