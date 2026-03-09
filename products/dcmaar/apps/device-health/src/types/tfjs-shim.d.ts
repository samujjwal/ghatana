// Minimal TFJS types used by intent classifier to satisfy type-check without full @types
declare module '@tensorflow/tfjs' {
  export interface LayersModel {
    predict(x: Tensor): Tensor | Tensor[];
    dispose(): void;
    readonly layers: unknown[];
  }
  export interface Tensor {
    data(): Promise<Float32Array | number[] | unknown[]>;
    dispose(): void;
  }
  export function loadLayersModel(url: string): Promise<LayersModel>;
  export function tensor2d(values: Array<number[]>, shape: [number, number]): Tensor;
}
