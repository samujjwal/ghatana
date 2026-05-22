/**
 * TypeScript fixture library for Kernel lifecycle validation.
 */

export interface Message {
  readonly text: string;
}

export class Greeter {
  greet(name: string): string {
    return `Hello, ${name}!`;
  }
}

export function createGreeter(): Greeter {
  return new Greeter();
}
