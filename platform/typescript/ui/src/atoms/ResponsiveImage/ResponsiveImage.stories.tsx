import type { Meta, StoryObj } from "@storybook/react";
import { ResponsiveImage } from "../../hooks/useImageOptimization";

const meta = {
  title: "Design System/Atoms/ResponsiveImage",
  component: ResponsiveImage,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
  argTypes: {
    src: { control: "text" },
    srcMd: { control: "text" },
    srcLg: { control: "text" },
    placeholder: { control: "text" },
    lazy: { control: "boolean" },
    alt: { control: "text" },
  },
} satisfies Meta<typeof ResponsiveImage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    src: "https://placehold.co/300x200?text=Mobile+Default",
    alt: "Responsive image example",
    className: "rounded-lg shadow-lg",
  },
};

export const WithBreakpoints: Story = {
  args: {
    src: "https://placehold.co/400x300/orange/white?text=Mobile+Small",
    srcMd: "https://placehold.co/600x400/blue/white?text=Tablet+Medium",
    srcLg: "https://placehold.co/800x600/green/white?text=Desktop+Large",
    alt: "Breakpoint example",
    className: "rounded-lg shadow-lg max-w-full",
  },
  parameters: {
    docs: {
      description: {
        story:
          "Resize the viewport to see different images load (Mobile < 768px, Tablet >= 768px, Desktop >= 1024px).",
      },
    },
  },
};

export const LazyLoaded: Story = {
  args: {
    src: "https://placehold.co/800x600?text=Lazy+Loaded",
    lazy: true,
    alt: "Lazy loaded image",
    className: "rounded-lg shadow-lg",
  },
  decorators: [
    (Story) => (
      <div className="h-[200vh] py-[100vh]">
        <p className="text-center mb-4">Scroll down to load image...</p>
        <Story />
      </div>
    ),
  ],
};

export const WithPlaceholder: Story = {
  args: {
    src: "https://images.unsplash.com/photo-1682687220742-aba13b6e50ba?auto=format&fit=crop&q=80&w=1000", // High res real image
    placeholder: "https://placehold.co/100x75?text=Loading...", // Tiny placeholder
    alt: "Placeholder example",
    className: "rounded-lg shadow-lg w-[300px]",
  },
};
