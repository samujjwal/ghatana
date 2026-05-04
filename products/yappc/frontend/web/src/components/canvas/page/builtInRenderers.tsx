/**
 * Built-in Renderer Manifests
 *
 * Registers the default component renderers (Button, Card, TextField, Typography, Box)
 * with the renderer manifest registry.
 *
 * @doc.type module
 * @doc.purpose Built-in renderer manifests for standard components
 * @doc.layer product
 * @doc.pattern Renderer Registration
 */

import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  TextField,
  Typography,
} from '@ghatana/design-system';
import type { ComponentInstance } from '@ghatana/ui-builder';
import type { BuilderRendererManifest, SlotBag } from './rendererManifest';

/**
 * Button renderer manifest.
 */
export const buttonRenderer: BuilderRendererManifest = {
  contractName: 'Button',
  render: (instance: ComponentInstance) => (
    <Button
      variant={instance.props.variant as 'solid' | 'outline' | 'ghost' | undefined}
      tone={instance.props.color as
        | 'primary'
        | 'secondary'
        | 'success'
        | 'warning'
        | 'danger'
        | 'info'
        | undefined}
      size={instance.props.size as 'sm' | 'md' | 'lg' | undefined}
      disabled={Boolean(instance.props.disabled)}
      fullWidth={Boolean(instance.props.fullWidth)}
    >
      {(instance.props.children as React.ReactNode) ?? instance.metadata.name ?? 'Button'}
    </Button>
  ),
  previewPolicy: {
    allowInteractive: true,
    allowDataBinding: false,
  },
};

/**
 * Card renderer manifest.
 */
export const cardRenderer: BuilderRendererManifest = {
  contractName: 'Card',
  render: (instance: ComponentInstance, slots: SlotBag) => (
    <Card elevation={typeof instance.props.elevation === 'number' ? instance.props.elevation : 2}>
      {instance.props.title || instance.props.subtitle ? (
        <CardHeader
          title={instance.props.title as string | undefined}
          subheader={instance.props.subtitle as string | undefined}
        />
      ) : null}
      <CardContent>
        {instance.props.content ? <Typography>{instance.props.content as string}</Typography> : null}
        {slots.default}
      </CardContent>
      {slots.actions ? <Box className="flex gap-2 px-4 pb-4">{slots.actions}</Box> : null}
    </Card>
  ),
  previewPolicy: {
    allowInteractive: false,
    allowDataBinding: false,
  },
};

/**
 * TextField renderer manifest.
 */
export const textFieldRenderer: BuilderRendererManifest = {
  contractName: 'TextField',
  render: (instance: ComponentInstance) => (
    <TextField
      label={instance.props.label as string | undefined}
      placeholder={instance.props.placeholder as string | undefined}
      size={instance.props.size as 'small' | 'medium' | undefined}
      required={Boolean(instance.props.required)}
      disabled={Boolean(instance.props.disabled)}
      multiline={Boolean(instance.props.multiline)}
      style={Boolean(instance.props.fullWidth) ? { width: '100%' } : undefined}
    />
  ),
  previewPolicy: {
    allowInteractive: true,
    allowDataBinding: true,
  },
};

/**
 * Typography renderer manifest.
 */
export const typographyRenderer: BuilderRendererManifest = {
  contractName: 'Typography',
  render: (instance: ComponentInstance, slots: SlotBag) => (
    <Typography
      variant={instance.props.variant as never}
      color={instance.props.color as never}
      align={instance.props.align as React.ComponentProps<typeof Typography>['align']}
    >
      {(instance.props.children as React.ReactNode) ?? slots.default ?? instance.metadata.name}
    </Typography>
  ),
  previewPolicy: {
    allowInteractive: false,
    allowDataBinding: false,
  },
};

/**
 * Box renderer manifest.
 */
export const boxRenderer: BuilderRendererManifest = {
  contractName: 'Box',
  render: (instance: ComponentInstance, slots: SlotBag) => (
    <Box
      p={typeof instance.props.padding === 'number' ? instance.props.padding : 2}
      m={typeof instance.props.margin === 'number' ? instance.props.margin : 0}
      backgroundColor={instance.props.backgroundColor as string | undefined}
      borderRadius={typeof instance.props.borderRadius === 'number' ? instance.props.borderRadius : 0}
      style={{
        display: (instance.props.display as string | undefined) ?? 'block',
        flexDirection: instance.props.flexDirection as React.CSSProperties['flexDirection'],
        justifyContent: instance.props.justifyContent as React.CSSProperties['justifyContent'],
        alignItems: instance.props.alignItems as React.CSSProperties['alignItems'],
        minHeight: 64,
        border: '1px dashed #d1d5db',
      }}
    >
      {slots.default}
    </Box>
  ),
  previewPolicy: {
    allowInteractive: false,
    allowDataBinding: false,
  },
};

/**
 * Registers all built-in renderers with the registry.
 * Call this during application initialization.
 */
export function registerBuiltInRenderers(): void {
  const { rendererManifestRegistry } = require('./rendererManifest');
  
  rendererManifestRegistry.register(buttonRenderer);
  rendererManifestRegistry.register(cardRenderer);
  rendererManifestRegistry.register(textFieldRenderer);
  rendererManifestRegistry.register(typographyRenderer);
  rendererManifestRegistry.register(boxRenderer);
}
