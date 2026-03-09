import { WebSocket } from 'ws';

declare module 'ws' {
  interface WebSocket {
    isAlive: boolean;
    lastActivity: number;
    remoteAddress: string;
  }
}

export {};
