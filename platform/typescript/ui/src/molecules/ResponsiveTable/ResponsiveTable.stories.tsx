import type { Meta, StoryObj } from "@storybook/react";
import { ResponsiveTable } from "./ResponsiveTable";
import { Badge } from "../Badge"; // Assuming Badge exists or I might mock it.
import { Button } from "../Button/Button";
import { MoreHorizontal } from "lucide-react";

const meta = {
  title: "Design System/Molecules/ResponsiveTable",
  component: ResponsiveTable,
  parameters: {
    layout: "padded",
  },
  tags: ["autodocs"],
  argTypes: {
    isLoading: {
      control: "boolean",
      description: "Whether the table is in a loading state",
    },
    data: {
      control: "object",
      description: "Array of data objects to display",
    },
  },
} satisfies Meta<typeof ResponsiveTable>;

export default meta;
type Story = StoryObj<typeof meta>;

interface User {
  id: string;
  name: string;
  email: string;
  role: "Admin" | "User" | "Guest";
  status: "Active" | "Inactive";
  lastLogin: string;
}

const mockData: User[] = [
  {
    id: "1",
    name: "Alice Johnson",
    email: "alice@example.com",
    role: "Admin",
    status: "Active",
    lastLogin: "2023-10-25",
  },
  {
    id: "2",
    name: "Bob Smith",
    email: "bob@example.com",
    role: "User",
    status: "Inactive",
    lastLogin: "2023-10-20",
  },
  {
    id: "3",
    name: "Charlie Brown",
    email: "charlie@example.com",
    role: "User",
    status: "Active",
    lastLogin: "2023-10-24",
  },
  {
    id: "4",
    name: "Diana Prince",
    email: "diana@example.com",
    role: "Guest",
    status: "Active",
    lastLogin: "2023-10-22",
  },
];

const columns = [
  {
    header: "Name",
    accessor: (user: User) => <span className="font-medium">{user.name}</span>,
  },
  {
    header: "Email",
    accessor: "email",
    hideOnMobile: true,
  },
  {
    header: "Role",
    accessor: (user: User) => (
      <span
        className={`px-2 py-1 rounded-full text-xs font-semibold ${
          user.role === "Admin"
            ? "bg-purple-100 text-purple-700"
            : "bg-gray-100 text-gray-700"
        }`}
      >
        {user.role}
      </span>
    ),
  },
  {
    header: "Status",
    accessor: (user: User) => (
      <span
        className={`inline-flex items-center gap-1.5 text-sm ${
          user.status === "Active" ? "text-green-600" : "text-gray-500"
        }`}
      >
        <span
          className={`w-1.5 h-1.5 rounded-full ${user.status === "Active" ? "bg-green-500" : "bg-gray-400"}`}
        />
        {user.status}
      </span>
    ),
    hideOnMobile: true,
  },
  {
    header: "",
    accessor: () => (
      <Button variant="ghost" size="sm">
        <MoreHorizontal className="w-4 h-4" />
      </Button>
    ),
  },
];

const mobileCardRenderer = (user: User) => (
  <div className="space-y-3">
    <div className="flex justify-between items-start">
      <div>
        <h4 className="font-medium text-gray-900 dark:text-gray-100">
          {user.name}
        </h4>
        <p className="text-sm text-gray-500">{user.email}</p>
      </div>
      <span
        className={`px-2 py-1 rounded-full text-xs font-semibold ${
          user.role === "Admin"
            ? "bg-purple-100 text-purple-700"
            : "bg-gray-100 text-gray-700"
        }`}
      >
        {user.role}
      </span>
    </div>
    <div className="flex items-center justify-between text-sm">
      <span
        className={`inline-flex items-center gap-1.5 ${
          user.status === "Active" ? "text-green-600" : "text-gray-500"
        }`}
      >
        <span
          className={`w-1.5 h-1.5 rounded-full ${user.status === "Active" ? "bg-green-500" : "bg-gray-400"}`}
        />
        {user.status}
      </span>
      <span className="text-gray-400">Last login: {user.lastLogin}</span>
    </div>
  </div>
);

export const Default: Story = {
  args: {
    data: mockData,
    columns: columns as any, // Type cast for storybook simplicity
    getRowKey: (user) => user.id,
    mobileCardRenderer: mobileCardRenderer as any,
  },
};

export const Loading: Story = {
  args: {
    isLoading: true,
    data: [],
    columns: columns as any,
    getRowKey: (user) => user.id,
    mobileCardRenderer: mobileCardRenderer as any,
  },
};

export const Empty: Story = {
  args: {
    data: [],
    columns: columns as any,
    getRowKey: (user) => user.id,
    mobileCardRenderer: mobileCardRenderer as any,
  },
};
