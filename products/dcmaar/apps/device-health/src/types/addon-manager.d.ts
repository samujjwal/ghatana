// Type definitions for addon-manager

declare module '../../src/app/background/addon-manager' {
  export function verifySignature(
    data: string, 
    signature: string, 
    publicKey: string
  ): Promise<boolean>;
}
