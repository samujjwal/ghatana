declare module 'jspdf' {
  export default class jsPDF {
    constructor(options: unknown);
    addImage(...args: unknown[]): void;
    setProperties(props: Record<string, unknown>): void;
    save(fileName: string): void;
  }
}

declare module 'html2canvas' {
  export default function html2canvas(
    element: HTMLElement,
    options: Record<string, unknown>,
  ): Promise<HTMLCanvasElement>;
}
