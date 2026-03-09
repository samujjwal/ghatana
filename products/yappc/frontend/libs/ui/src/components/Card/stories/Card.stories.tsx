import { StorybookProvider } from '@ghatana/yappc-ui';
import React from 'react';

import { Button } from '../../Button';
import { Card, CardContent, CardHeader, CardActions } from '../Card';

import type { Meta, StoryObj } from '@storybook/react-vite';


const meta: Meta<typeof Card> = {
  title: 'UI/Components/Card',
  component: Card,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <StorybookProvider>
        <div style={{ width: '300px' }}>
          <Story />
        </div>
      </StorybookProvider>
    ),
  ],
  argTypes: {
    variant: {
      control: 'select',
      options: ['elevation', 'outlined'],
      description: 'The variant of the card',
      defaultValue: 'elevation',
    },
    elevation: {
      control: { type: 'range', min: 0, max: 24, step: 1 },
      description: 'The elevation of the card (only applies to elevation variant)',
      defaultValue: 1,
    },
    shape: {
      control: 'select',
      options: ['rounded', 'square', 'soft'],
      description: 'The shape of the card',
      defaultValue: 'rounded',
    },
    hover: {
      control: 'boolean',
      description: 'Whether the card should have a hover effect',
      defaultValue: false,
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Card>;

export const Basic: Story = {
  args: {
    children: <CardContent>This is a basic card</CardContent>,
  },
};

export const WithHeader: Story = {
  args: {
    children: (
      <>
        <CardHeader title="Card Title" subheader="Card Subtitle" />
        <CardContent>
          This is a card with a header. The header includes a title and a subtitle.
        </CardContent>
      </>
    ),
  },
};

export const WithActions: Story = {
  args: {
    children: (
      <>
        <CardHeader title="Card with Actions" />
        <CardContent>
          This card has action buttons at the bottom.
        </CardContent>
        <CardActions>
          <Button size="small">Cancel</Button>
          <Button size="small" variant="contained" color="primary">Save</Button>
        </CardActions>
      </>
    ),
  },
};

export const Outlined: Story = {
  args: {
    variant: 'outlined',
    children: (
      <>
        <CardHeader title="Outlined Card" />
        <CardContent>
          This card uses the outlined variant instead of elevation.
        </CardContent>
      </>
    ),
  },
};

export const HighElevation: Story = {
  args: {
    elevation: 8,
    children: (
      <>
        <CardHeader title="High Elevation Card" />
        <CardContent>
          This card has a higher elevation (shadow).
        </CardContent>
      </>
    ),
  },
};

export const SquareShape: Story = {
  args: {
    shape: 'square',
    children: (
      <>
        <CardHeader title="Square Card" />
        <CardContent>
          This card has square corners instead of rounded ones.
        </CardContent>
      </>
    ),
  },
};

export const SoftShape: Story = {
  args: {
    shape: 'soft',
    children: (
      <>
        <CardHeader title="Soft Card" />
        <CardContent>
          This card has soft, more rounded corners.
        </CardContent>
      </>
    ),
  },
};

export const WithHoverEffect: Story = {
  args: {
    hover: true,
    children: (
      <>
        <CardHeader title="Hover Effect Card" />
        <CardContent>
          Hover over this card to see the effect. It will elevate slightly.
        </CardContent>
      </>
    ),
  },
};

export const Complex: Story = {
  args: {
    hover: true,
    children: (
      <>
        <CardHeader
          title="Complex Card Example"
          subheader="Last updated: September 29, 2025"
          avatar={<div style={{ width: 40, height: 40, borderRadius: '50%', backgroundColor: '#1976d2' }}></div>}
          action={
            <Button variant="text" size="small">
              More
            </Button>
          }
        />
        <CardContent>
          <p>This is a more complex card example with multiple components.</p>
          <p>It demonstrates how to combine various card parts to create a rich UI component.</p>
        </CardContent>
        <CardActions>
          <Button size="small">Share</Button>
          <Button size="small">Learn More</Button>
          <div style={{ flexGrow: 1 }}></div>
          <Button size="small" variant="contained" color="primary">
            Submit
          </Button>
        </CardActions>
      </>
    ),
  },
};

// Theme variants
export const DarkTheme: Story = {
  args: {
    children: (
      <>
        <CardHeader title="Dark Theme Card" />
        <CardContent>
          This card is displayed with the dark theme.
        </CardContent>
        <CardActions>
          <Button size="small" variant="contained">Action</Button>
        </CardActions>
      </>
    ),
  },
  decorators: [
    (Story) => (
      <StorybookProvider theme="dark">
        <div style={{ width: '300px' }}>
          <Story />
        </div>
      </StorybookProvider>
    ),
  ],
};
