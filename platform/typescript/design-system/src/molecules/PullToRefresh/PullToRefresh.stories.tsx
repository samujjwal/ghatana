import type { Meta, StoryObj } from "@storybook/react";
import { PullToRefresh } from "./PullToRefresh";
import { useState } from "react";

const meta = {
  title: "Design System/Interactives/PullToRefresh",
  component: PullToRefresh,
  parameters: {
    layout: "fullscreen", // Important for pull to refresh
    viewport: {
      defaultViewport: "mobile1",
    },
  },
  tags: ["autodocs"],
  argTypes: {
    onRefresh: { action: "refreshed" },
    enabled: { control: "boolean" },
    pullThreshold: { control: "number" },
    maxPullDistance: { control: "number" },
    pullText: { control: "text" },
    releaseText: { control: "text" },
    refreshingText: { control: "text" },
  },
  decorators: [
    (Story) => (
      <div className="h-[500px] w-full bg-gray-50 border border-gray-200 overflow-hidden relative">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof PullToRefresh>;

export default meta;
type Story = StoryObj<typeof meta>;

const PullToRefreshDemo = (args: any) => {
  const [items, setItems] = useState([1, 2, 3, 4, 5]);

  const handleRefresh = async () => {
    // Simulate network request
    await new Promise((resolve) => setTimeout(resolve, 2000));
    // Add new items
    setItems((prev) => [Math.max(...prev) + 1, ...prev]);
    args.onRefresh?.();
  };

  return (
    <PullToRefresh {...args} onRefresh={handleRefresh}>
      <div className="p-4 space-y-4 min-h-[500px] bg-white">
        <h2 className="text-lg font-bold">Latest Updates</h2>
        <p className="text-sm text-gray-500">Pull down to refresh...</p>

        {items.map((item) => (
          <div
            key={item}
            className="p-4 rounded-lg border border-gray-100 shadow-sm bg-white"
          >
            <div className="font-medium text-gray-900">New Item {item}</div>
            <div className="text-sm text-gray-500">Received just now</div>
          </div>
        ))}
      </div>
    </PullToRefresh>
  );
};

export const Default: Story = {
  render: PullToRefreshDemo,
  args: {
    pullText: "Pull to refresh",
    releaseText: "Release to refresh",
    refreshingText: "Refreshing...",
  },
};

export const CustomThreshold: Story = {
  render: PullToRefreshDemo,
  args: {
    pullText: "Pull harder!",
    pullThreshold: 150,
    releaseText: "Let go!",
  },
};

export const Disabled: Story = {
  render: PullToRefreshDemo,
  args: {
    enabled: false,
    pullText: "Disabled (wont work)",
  },
};
