import { Card, CardHeader, CardContent, CardActions, CardMedia } from './Card.tailwind';
import { Button } from '../Button';
import { Typography } from '../Typography';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Card (Tailwind)',
  component: Card,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Card>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Card className="max-w-sm">
      <CardContent>
        <Typography variant="h5" gutterBottom>
          Simple Card
        </Typography>
        <Typography variant="body2" color="text">
          This is a basic card with just content.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const WithHeader: Story = {
  render: () => (
    <Card className="max-w-sm">
      <CardHeader
        title="Card Title"
        subheader="Card Subtitle"
      />
      <CardContent>
        <Typography variant="body2">
          This card includes a header with title and subtitle.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const WithActions: Story = {
  render: () => (
    <Card className="max-w-sm">
      <CardContent>
        <Typography variant="h5" gutterBottom>
          Card with Actions
        </Typography>
        <Typography variant="body2" color="text">
          This card includes action buttons at the bottom.
        </Typography>
      </CardContent>
      <CardActions>
        <Button variant="outline" size="sm">Cancel</Button>
        <Button size="sm">Confirm</Button>
      </CardActions>
    </Card>
  ),
};

export const WithMedia: Story = {
  render: () => (
    <Card className="max-w-sm">
      <CardMedia
        image="https://picsum.photos/400/200"
        alt="Sample image"
        height="h-48"
      />
      <CardContent>
        <Typography variant="h5" gutterBottom>
          Card with Image
        </Typography>
        <Typography variant="body2" color="text">
          This card includes a media section with an image.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const CompleteCard: Story = {
  render: () => (
    <Card className="max-w-sm">
      <CardHeader
        avatar={
          <div className="w-10 h-10 rounded-full bg-primary-500 flex items-center justify-center text-white font-semibold">
            AB
          </div>
        }
        title="Alice Brown"
        subheader="October 25, 2025"
        action={
          <button className="text-grey-600 hover:text-grey-900">
            •••
          </button>
        }
      />
      <CardMedia
        image="https://picsum.photos/400/250"
        alt="Card media"
        height="h-56"
      />
      <CardContent>
        <Typography variant="body1" gutterBottom>
          Beautiful Landscape
        </Typography>
        <Typography variant="body2" color="text">
          A stunning view captured during the golden hour. The mountains in the 
          background create a perfect silhouette against the setting sun.
        </Typography>
      </CardContent>
      <CardActions>
        <Button variant="ghost" size="sm">Like</Button>
        <Button variant="ghost" size="sm">Share</Button>
        <Button variant="ghost" size="sm">Comment</Button>
      </CardActions>
    </Card>
  ),
};

export const Outlined: Story = {
  render: () => (
    <Card variant="outlined" className="max-w-sm">
      <CardHeader title="Outlined Card" subheader="With border instead of shadow" />
      <CardContent>
        <Typography variant="body2">
          This card uses the outlined variant, which shows a border instead of a shadow.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const Elevations: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-4">
      <Card elevation={0} className="max-w-xs">
        <CardContent>
          <Typography variant="caption" className="block mb-1">Elevation 0</Typography>
          <Typography variant="body2">No shadow</Typography>
        </CardContent>
      </Card>
      <Card elevation={2} className="max-w-xs">
        <CardContent>
          <Typography variant="caption" className="block mb-1">Elevation 2</Typography>
          <Typography variant="body2">Light shadow</Typography>
        </CardContent>
      </Card>
      <Card elevation={8} className="max-w-xs">
        <CardContent>
          <Typography variant="caption" className="block mb-1">Elevation 8</Typography>
          <Typography variant="body2">Strong shadow</Typography>
        </CardContent>
      </Card>
    </div>
  ),
};

export const ProductCard: Story = {
  render: () => (
    <Card className="max-w-xs">
      <CardMedia
        image="https://picsum.photos/300/300"
        alt="Product"
        height="h-64"
      />
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Premium Headphones
        </Typography>
        <Typography variant="body2" color="text" className="mb-2">
          High-quality wireless headphones with noise cancellation.
        </Typography>
        <Typography variant="h5" color="primary">
          $299.99
        </Typography>
      </CardContent>
      <CardActions>
        <Button variant="outline" size="sm" fullWidth>
          Add to Cart
        </Button>
      </CardActions>
    </Card>
  ),
};

export const DashboardCard: Story = {
  render: () => (
    <Card className="max-w-xs">
      <CardHeader
        title="Revenue"
        subheader="Last 30 days"
        action={
          <Typography variant="caption" color="success">
            +12%
          </Typography>
        }
      />
      <CardContent>
        <Typography variant="h3" color="primary" gutterBottom>
          $45,231
        </Typography>
        <Typography variant="body2" color="text">
          Compared to $40,399 last month
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const UserProfileCard: Story = {
  render: () => (
    <Card className="max-w-sm">
      <div className="h-32 bg-gradient-to-r from-primary-500 to-secondary-500" />
      <CardContent className="text-center -mt-12">
        <div className="w-24 h-24 mx-auto mb-4 rounded-full border-4 border-white bg-grey-300 flex items-center justify-center text-2xl font-bold text-white">
          JD
        </div>
        <Typography variant="h5" gutterBottom>
          John Doe
        </Typography>
        <Typography variant="body2" color="text" gutterBottom>
          Senior Software Engineer
        </Typography>
        <Typography variant="caption" color="text">
          San Francisco, CA
        </Typography>
      </CardContent>
      <CardActions className="justify-center pb-6">
        <Button variant="outline" size="sm">Message</Button>
        <Button size="sm">Follow</Button>
      </CardActions>
    </Card>
  ),
};

export const Playground: Story = {
  args: {
    variant: 'elevation',
    elevation: 2,
    className: 'max-w-sm',
    children: (
      <>
        <CardHeader title="Playground Card" subheader="Customize using controls" />
        <CardContent>
          <Typography variant="body2">
            Use the controls below to customize this card
          </Typography>
        </CardContent>
      </>
    ),
  },
};
