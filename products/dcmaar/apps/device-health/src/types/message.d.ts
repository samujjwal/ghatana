// src/types/message.d.ts
interface IMessage<T = unknown> {
  id: string;
  type: string;
  payload: T;
  timestamp: number;
  [key: string]: unknown;
}

declare module '@communication/types/message' {
  export { IMessage };
}
