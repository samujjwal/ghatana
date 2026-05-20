export interface StudioLogMeta {
  readonly [key: string]: unknown;
}

export interface StudioLogger {
  error: (message: string, meta: StudioLogMeta) => void;
}

export const studioLogger: StudioLogger = {
  error(message: string, meta: StudioLogMeta): void {
    const payload = {
      level: 'error',
      message,
      scope: 'ghatana-studio',
      ...meta,
    };
    const proc = (globalThis as {
      process?: { stderr?: { write?: (message: string) => void } };
    }).process;

    if (typeof proc?.stderr?.write === 'function') {
      proc.stderr.write(`${JSON.stringify(payload)}\n`);
      return;
    }

    if (
      typeof globalThis.dispatchEvent === 'function' &&
      typeof CustomEvent !== 'undefined'
    ) {
      globalThis.dispatchEvent(
        new CustomEvent('ghatana-studio-diagnostic', { detail: payload }),
      );
    }
  },
};
