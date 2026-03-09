declare namespace Vi {
  // Allow extending matchers for vitest
}

declare namespace jest {
  /**
   *
   */
  interface Matchers<R> {
    toHaveNoViolations(): R;
  }
}

export {};

declare namespace Vi {
  // allow extending matchers for vitest if needed
}
