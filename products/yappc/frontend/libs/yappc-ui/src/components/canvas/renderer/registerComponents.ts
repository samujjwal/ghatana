/**
 * Component Registration
 *
 * Registers all UI components with the Node Renderer registry.
 * Import this file to automatically register all available components.
 *
 * @module canvas/renderer/registerComponents
 */

import { RendererComponentRegistry } from './ComponentRegistry';

// Import UI components
import { Accordion } from '../../components/Accordion/Accordion';
import { Alert } from '../../components/Alert/Alert';
import { Avatar } from '../../components/Avatar/Avatar';
import { Badge } from '../../components/Badge/Badge';
import { Button } from '../../components/Button/Button';
import { Card } from '../../components/Card/Card';
import { Container } from '../../components/Container/Container';
import { Dialog } from '../../components/Dialog/Dialog';
import { Form } from '../../components/Form/Form';
import { Grid } from '../../components/Grid/Grid';
import { Menu } from '../../components/Menu/Menu';
import { Stack } from '../../components/Stack/Stack';
import { Tabs } from '../../components/Tabs/Tabs';
import { TextField } from '../../components/TextField/TextField';

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
