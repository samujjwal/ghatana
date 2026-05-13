export interface ProductEnvironment {
  readonly name: string;
  readonly target?: string;
  readonly variables?: Readonly<Record<string, string>>;
}
