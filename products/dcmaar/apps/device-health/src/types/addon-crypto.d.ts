// Type definitions for addon-crypto

declare module '../../src/app/background/addon-crypto' {
  export function verifySignatureFromSpki(
    data: string, 
    signature: string, 
    publicKey: string
  ): Promise<boolean>;
  
  export function pemToArrayBuffer(pem: string): ArrayBuffer;
  export function derToRawSignature(derSignature: ArrayBuffer): ArrayBuffer;
}
