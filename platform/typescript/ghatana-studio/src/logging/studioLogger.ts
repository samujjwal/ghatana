export interface StudioLogMeta {
  readonly [key: string]: unknown;
}

export interface StudioLogger {
  error: (message: string, meta: StudioLogMeta) => void;
}

export const studioLogger: StudioLogger = {
  error(message: string, meta: StudioLogMeta): void {
    console.error(message, {
      scope: 'ghatana-studio',
      ...meta,
    });
  },
};
