// Core UI components from @ghatana/yappc-ui
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  TextField,
  Typography,
  Box,
} from '@ghatana/ui';

import React from 'react';

import type {
  ComponentData,
  ButtonData,
  CardData,
  TextFieldData,
  TypographyData,
  BoxData,
} from './schemas';

const mapButtonVariant = (variant: ButtonData['variant']) => {
  switch (variant) {
    case 'contained':
      return 'solid';
    case 'outlined':
      return 'outline';
    case 'text':
      return 'ghost';
    default:
      return 'solid';
  }
};

const mapButtonTone = (color: ButtonData['color']) => {
  switch (color) {
    case 'error':
      return 'danger';
    default:
      return color;
  }
};

const mapButtonSize = (size: ButtonData['size']) => {
  switch (size) {
    case 'small':
      return 'sm';
    case 'large':
      return 'lg';
    case 'medium':
    default:
      return 'md';
  }
};

const mapTypographyColor = (color: TypographyData['color'] | undefined) => {
  switch (color) {
    case 'primary':
    case 'secondary':
      return color;
    case 'error':
      return 'danger';
    case 'textPrimary':
      return 'default';
    case 'textSecondary':
      return 'subtle';
    default:
      return undefined;
  }
};

/**
 *
 */
interface ComponentRendererProps {
  data: ComponentData;
  isSelected?: boolean;
  onClick?: () => void;
}

export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  data,
  isSelected = false,
  onClick,
}) => {
  const wrapperStyle = {
    outline: isSelected ? '2px solid #1976d2' : 'none',
    outlineOffset: '2px',
    cursor: 'pointer',
    position: 'relative' as const,
  };

  switch (data.type) {
    case 'button': {
      const buttonData = data as ButtonData;
      return (
        <div style={wrapperStyle} onClick={onClick} data-testid="page-button">
          <Button
            variant={mapButtonVariant(buttonData.variant)}
            tone={mapButtonTone(buttonData.color)}
            size={mapButtonSize(buttonData.size)}
            disabled={buttonData.disabled}
            fullWidth={buttonData.fullWidth}
          >
            {buttonData.text}
          </Button>
        </div>
      );
    }

    case 'card': {
      const cardData = data as CardData;
      return (
        <div style={wrapperStyle} onClick={onClick} data-testid="page-card">
          <Card elevation={cardData.elevation}>
            {cardData.title && (
              <CardHeader
                title={cardData.title}
                subheader={cardData.subtitle}
              />
            )}
            {cardData.content && (
              <CardContent>
                <Typography>{cardData.content}</Typography>
              </CardContent>
            )}
          </Card>
        </div>
      );
    }

    case 'textfield': {
      const textFieldData = data as TextFieldData;
      return (
        <div style={wrapperStyle} onClick={onClick} data-testid="page-textfield">
          <TextField
            label={textFieldData.label}
            placeholder={textFieldData.placeholder}
            size={textFieldData.size === 'small' ? 'sm' : 'md'}
            required={textFieldData.required}
            disabled={textFieldData.disabled}
            style={textFieldData.fullWidth ? { width: '100%' } : undefined}
          />
        </div>
      );
    }

    case 'typography': {
      const typographyData = data as TypographyData;
      return (
        <div style={wrapperStyle} onClick={onClick} data-testid="page-typography">
          <Typography
            variant={typographyData.variant}
            color={mapTypographyColor(typographyData.color)}
            align={typographyData.align}
          >
            {typographyData.text}
          </Typography>
        </div>
      );
    }

    case 'box': {
      const boxData = data as BoxData;
      return (
        <div style={wrapperStyle} onClick={onClick} data-testid="page-box">
          <Box
            p={boxData.padding}
            m={boxData.margin}
            backgroundColor={boxData.backgroundColor}
            borderRadius={boxData.borderRadius}
            style={{
              display: boxData.display,
              flexDirection: boxData.flexDirection,
              justifyContent: boxData.justifyContent,
              alignItems: boxData.alignItems,
              minHeight: 50,
              border: '1px dashed #ccc',
            }}
          >
            <Typography variant="caption" color="subtle">
              Container
            </Typography>
          </Box>
        </div>
      );
    }

    default:
      return (
        <div style={wrapperStyle} onClick={onClick}>
          <Typography color="danger">
            Unknown component type: {data.type}
          </Typography>
        </div>
      );
  }
};
