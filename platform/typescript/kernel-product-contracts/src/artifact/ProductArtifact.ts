export interface ProductArtifact {
  readonly type: string;
  readonly packaging?: string;
  readonly required?: boolean;
  readonly paths?: readonly string[];
}
