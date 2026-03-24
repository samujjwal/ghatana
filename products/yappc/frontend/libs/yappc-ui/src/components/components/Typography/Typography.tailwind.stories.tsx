import { Typography } from './Typography.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Typography (Tailwind)',
  component: Typography,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Typography>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: 'The quick brown fox jumps over the lazy dog',
  },
};

export const Headings: Story = {
  render: () => (
    <div className="space-y-4">
      <Typography variant="h1">Heading 1</Typography>
      <Typography variant="h2">Heading 2</Typography>
      <Typography variant="h3">Heading 3</Typography>
      <Typography variant="h4">Heading 4</Typography>
      <Typography variant="h5">Heading 5</Typography>
      <Typography variant="h6">Heading 6</Typography>
    </div>
  ),
};

export const BodyText: Story = {
  render: () => (
    <div className="space-y-4">
      <Typography variant="subtitle1">
        Subtitle 1 - A larger subtitle for section headings
      </Typography>
      <Typography variant="subtitle2">
        Subtitle 2 - A smaller subtitle for subsections
      </Typography>
      <Typography variant="body1">
        Body 1 - The default text style for most content. Lorem ipsum dolor sit amet, 
        consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et 
        dolore magna aliqua.
      </Typography>
      <Typography variant="body2">
        Body 2 - Slightly smaller text for secondary content. Ut enim ad minim veniam, 
        quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
      </Typography>
    </div>
  ),
};

export const SmallText: Story = {
  render: () => (
    <div className="space-y-4">
      <Typography variant="caption">
        Caption text - Used for image captions, footnotes, or helper text
      </Typography>
      <Typography variant="overline">
        Overline text
      </Typography>
      <Typography variant="button">
        Button text
      </Typography>
    </div>
  ),
};

export const Colors: Story = {
  render: () => (
    <div className="space-y-2">
      <Typography color="primary">Primary color text</Typography>
      <Typography color="secondary">Secondary color text</Typography>
      <Typography color="error">Error color text</Typography>
      <Typography color="warning">Warning color text</Typography>
      <Typography color="info">Info color text</Typography>
      <Typography color="success">Success color text</Typography>
      <Typography color="text">Default text color</Typography>
    </div>
  ),
};

export const Alignment: Story = {
  render: () => (
    <div className="space-y-4">
      <Typography align="left">Left aligned text (default)</Typography>
      <Typography align="center">Center aligned text</Typography>
      <Typography align="right">Right aligned text</Typography>
      <Typography align="justify">
        Justified text. Lorem ipsum dolor sit amet, consectetur adipiscing elit, 
        sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim 
        ad minim veniam, quis nostrud exercitation ullamco laboris.
      </Typography>
    </div>
  ),
};

export const NoWrap: Story = {
  render: () => (
    <div className="max-w-xs border border-grey-300 p-4">
      <Typography variant="body1" noWrap>
        This is a very long text that will not wrap and will be truncated with an ellipsis 
        when it exceeds the container width
      </Typography>
    </div>
  ),
};

export const GutterBottom: Story = {
  render: () => (
    <div>
      <Typography variant="h3" gutterBottom>
        Heading with Bottom Margin
      </Typography>
      <Typography variant="body1">
        The heading above has gutterBottom prop which adds margin-bottom spacing.
      </Typography>
    </div>
  ),
};

export const CustomComponent: Story = {
  render: () => (
    <div className="space-y-4">
      <Typography variant="h6" component="h2">
        h6 variant rendered as h2 element
      </Typography>
      <Typography variant="body1" component="div">
        body1 variant rendered as div element
      </Typography>
      <Typography variant="caption" component="p">
        caption variant rendered as p element
      </Typography>
    </div>
  ),
};

export const ArticleExample: Story = {
  render: () => (
    <article className="max-w-2xl">
      <Typography variant="h1" gutterBottom>
        The Future of Web Development
      </Typography>
      
      <Typography variant="subtitle1" color="text" gutterBottom>
        Exploring modern technologies and design patterns
      </Typography>
      
      <Typography variant="caption" className="block mb-6">
        Published on October 25, 2025 • 5 min read
      </Typography>
      
      <Typography variant="body1" gutterBottom>
        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod 
        tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, 
        quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
      </Typography>
      
      <Typography variant="h3" gutterBottom>
        Modern CSS Frameworks
      </Typography>
      
      <Typography variant="body1" gutterBottom>
        Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore 
        eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt 
        in culpa qui officia deserunt mollit anim id est laborum.
      </Typography>
      
      <Typography variant="body2" color="text">
        Note: This example demonstrates how Typography components can be used together 
        to create a well-structured article layout.
      </Typography>
    </article>
  ),
};

export const Playground: Story = {
  args: {
    variant: 'body1',
    children: 'Customize this text using the controls below',
    align: 'left',
    color: 'text',
    noWrap: false,
    gutterBottom: false,
  },
};
