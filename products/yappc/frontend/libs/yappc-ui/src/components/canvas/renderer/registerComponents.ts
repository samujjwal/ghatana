/**
 * Component Registration
 *
 * Registers all UI components with the Node Renderer registry.
 * Import this file to automatically register all available components.
 *
 * @module canvas/renderer/registerComponents
 */

import React from 'react';

import { RendererComponentRegistry } from './ComponentRegistry';

import type { ComponentType } from './ComponentRegistry';

const createFallbackElement =
  (tagName: keyof React.JSX.IntrinsicElements): ComponentType =>
  (props: unknown) => {
    const { children, ...rest } =
      props !== null && typeof props === 'object'
        ? (props as Record<string, unknown>)
        : {};
    return React.createElement(tagName, rest, children as React.ReactNode);
  };

const Accordion = createFallbackElement('details');
const Alert = createFallbackElement('div');
const Avatar = createFallbackElement('div');
const Badge = createFallbackElement('span');
const Button = createFallbackElement('button');
const Card = createFallbackElement('div');
const Container = createFallbackElement('div');
const Dialog = createFallbackElement('dialog');
const Form = createFallbackElement('form');
const Grid = createFallbackElement('div');
const Menu = createFallbackElement('menu');
const Stack = createFallbackElement('div');
const Tabs = createFallbackElement('div');
const TextField = createFallbackElement('input');

// ============================================================================
// Register All Components
// ============================================================================

/**
 * Register all available UI components
 */
export function registerAllComponents(): void {
  RendererComponentRegistry.registerMany([
    // Atoms
    {
      type: 'Button',
      component: Button,
      displayName: 'Button',
    },
    {
      type: 'TextField',
      component: TextField,
      displayName: 'Text Field',
    },
    {
      type: 'Badge',
      component: Badge,
      displayName: 'Badge',
    },
    {
      type: 'Avatar',
      component: Avatar,
      displayName: 'Avatar',
    },
    {
      type: 'Alert',
      component: Alert,
      displayName: 'Alert',
    },

    // Molecules
    {
      type: 'Card',
      component: Card,
      displayName: 'Card',
    },
    {
      type: 'Stack',
      component: Stack,
      displayName: 'Stack',
    },
    {
      type: 'Accordion',
      component: Accordion,
      displayName: 'Accordion',
    },
    {
      type: 'Menu',
      component: Menu,
      displayName: 'Menu',
    },
    {
      type: 'Tabs',
      component: Tabs,
      displayName: 'Tabs',
    },

    // Organisms
    {
      type: 'Form',
      component: Form,
      displayName: 'Form',
    },
    {
      type: 'Dialog',
      component: Dialog,
      displayName: 'Dialog',
    },

    // Layout
    {
      type: 'Container',
      component: Container,
      displayName: 'Container',
    },
    {
      type: 'Grid',
      component: Grid,
      displayName: 'Grid',
    },
  ]);

  console.log(
    `Registered ${RendererComponentRegistry.size} components for canvas rendering`
  );
}

/**
 * Auto-register on import (optional - can be called manually)
 */
if (typeof window !== 'undefined') {
  // Only auto-register in browser environment
  registerAllComponents();
}
