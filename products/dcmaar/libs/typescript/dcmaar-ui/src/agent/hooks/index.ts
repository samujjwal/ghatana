// Media query hooks
export * from './useMediaQuery';

// Local storage hooks
export * from './useLocalStorage';

// DOM interaction hooks
export * from './useClickOutside';

// Re-export types
export type { MediaQuery } from './useMediaQuery';
export type { SetValue } from './useLocalStorage';
export type { Event as ClickOutsideEvent } from './useClickOutside';

// Re-export utilities
export { breakpoints, useBreakpoints } from './useMediaQuery';
export { localStorageUtils } from './useLocalStorage';
