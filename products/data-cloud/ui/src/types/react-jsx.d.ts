// Provide JSX namespace typings to match React 18 JSX factory
// This file helps TypeScript locate JSX types when multiple @types/react versions exist
import type * as React from 'react';

declare global {
  namespace JSX {
    // Allow React's types to be used for JSX
    type Element = React.ReactElement<any, any>;
    type ElementClass = React.Component<unknown>;
    interface ElementAttributesProperty {
      props: unknown;
    }
    interface IntrinsicElements {
      [elemName: string]: unknown;
    }
  }
}

export {};

