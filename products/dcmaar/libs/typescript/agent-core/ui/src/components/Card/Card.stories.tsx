import type { Meta, StoryObj } from '@storybook/react';
import { Button } from '../Button/Button';
import { Card, CardHeader, CardFooter, CardTitle, CardDescription, CardContent } from './Card';
import { ThemeProvider } from '../../theme/ThemeProvider';

const meta: Meta<typeof Card> = {
  title: 'Components/Card',
  component: Card,
  decorators: [
    (Story) => (
      <ThemeProvider>
        <div className="p-8">
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
  tags: ['autodocs'],
};

export default meta;

type Story = StoryObj<typeof Card>;

export const Default: Story = {
  render: () => (
    <Card className="w-[350px]">
      <CardHeader>
        <CardTitle>Card Title</CardTitle>
        <CardDescription>Card description goes here</CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">
          This is the card content. You can put any content here.
        </p>
      </CardContent>
      <CardFooter className="flex justify-between">
  <Button variant="outlined">Cancel</Button>
        <Button>Continue</Button>
      </CardFooter>
    </Card>
  ),
};

export const WithImage: Story = {
  render: () => (
    <Card className="w-[400px] overflow-hidden">
      <div className="h-48 bg-muted" />
      <CardHeader>
        <CardTitle>Featured Post</CardTitle>
        <CardDescription>Published on September 20, 2025</CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">
          This is a featured blog post with an image header. The card includes a title,
          publication date, and a brief excerpt of the post content.
        </p>
      </CardContent>
      <CardFooter>
        <Button variant="link" className="p-0 h-auto">
          Read more →
        </Button>
      </CardFooter>
    </Card>
  ),
};

export const HorizontalLayout: Story = {
  render: () => (
    <Card className="w-[600px] flex">
      <div className="w-1/3 bg-muted" />
      <div className="flex-1">
        <CardHeader>
          <CardTitle>Sidebar Content</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This card uses a horizontal layout with a sidebar. The sidebar can contain
            an image, icon, or any other content.
          </p>
        </CardContent>
      </div>
    </Card>
  ),
};

export const WithTabs: Story = {
  render: () => (
    <Card className="w-[500px]">
      <CardHeader className="border-b">
        <div className="flex space-x-4">
          <button className="border-b-2 border-primary pb-2 px-1 text-sm font-medium">
            Overview
          </button>
          <button className="border-b-2 border-transparent pb-2 px-1 text-sm font-medium text-muted-foreground hover:text-foreground">
            Settings
          </button>
        </div>
      </CardHeader>
      <CardContent className="pt-6">
        <p className="text-sm text-muted-foreground">
          This card includes a tabbed interface in the header. Tabs can be used to
          organize content within the same card.
        </p>
      </CardContent>
    </Card>
  ),
};

export const WithStatus: Story = {
  render: () => (
    <Card className="w-[350px]">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Task Item</CardTitle>
          <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
            Completed
          </span>
        </div>
        <CardDescription>Due: September 25, 2025</CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-sm">
          This is a task item with a status indicator. Status can be used to show
          the current state of an item.
        </p>
      </CardContent>
    </Card>
  ),
};
