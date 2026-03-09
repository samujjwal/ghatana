declare module 'pako' {
  export function gzip(input: string | Uint8Array): Uint8Array;
  export function ungzip(input: Uint8Array): Uint8Array;
  const pako: { gzip: typeof gzip; ungzip: typeof ungzip } & any;
  export default pako;
}
