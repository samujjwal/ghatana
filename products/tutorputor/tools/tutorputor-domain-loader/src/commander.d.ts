/**
 * Type declarations for commander package.
 * This is a minimal declaration to satisfy TypeScript until the actual package is installed.
 */
declare module "commander" {
  export class Command {
    name(name: string): this;
    description(desc: string): this;
    version(version: string): this;
    command(nameAndArgs: string): Command;
    option(flags: string, description?: string, defaultValue?: unknown): this;
    action(fn: (...args: unknown[]) => void | Promise<void>): this;
    parse(argv?: string[]): this;
  }
}
