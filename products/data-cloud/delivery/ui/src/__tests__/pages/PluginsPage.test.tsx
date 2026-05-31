import {
  PLUGINS_CATALOG_BOUNDARY_DETAIL,
  PLUGINS_INVENTORY_HEADER_DETAIL,
} from "@/lib/runtime-boundaries";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PluginsPage } from "../../pages/PluginsPage";
import { TestWrapper } from "../test-utils/wrapper";

describe("PluginsPage", () => {
  it("renders the plugin-management shell with stats, tabs, and search controls", async () => {
    const user = userEvent.setup();

    render(<PluginsPage />, { wrapper: TestWrapper });

    expect(
      screen.getByRole("heading", { name: "Plugins" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(PLUGINS_INVENTORY_HEADER_DETAIL),
    ).toBeInTheDocument();
    expect(screen.getByText("Installed")).toBeInTheDocument();
    expect(screen.getByText("Catalog Boundary")).toBeInTheDocument();
    expect(screen.getByText("Deployment")).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search plugins/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Catalog Boundary/i }));

    expect(
      screen.getByText(PLUGINS_CATALOG_BOUNDARY_DETAIL),
    ).toBeInTheDocument();
  });
});
