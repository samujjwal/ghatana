import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  TabWorkspace,
  type TabItem,
} from "../../components/common/TabWorkspace";

function renderWorkspace(onTabClose: (tabId: string) => void) {
  const tabs: TabItem[] = [
    { id: "tab-1", title: "Overview", closable: true },
    { id: "tab-2", title: "Metrics", closable: true },
  ];

  return render(
    <TabWorkspace
      tabs={tabs}
      activeTabId="tab-1"
      onTabChange={vi.fn()}
      onTabClose={onTabClose}
    >
      {() => <div>Tab Content</div>}
    </TabWorkspace>,
  );
}

describe("TabWorkspace", () => {
  it("exposes close controls with explicit labels", () => {
    renderWorkspace(vi.fn());

    expect(
      screen.getByRole("button", { name: /Close tab Overview/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Close tab Metrics/i }),
    ).toBeInTheDocument();
  });

  it("supports keyboard close action on tab controls", async () => {
    const user = userEvent.setup();
    const onTabClose = vi.fn();

    renderWorkspace(onTabClose);

    const closeMetricsButton = screen.getByRole("button", {
      name: /Close tab Metrics/i,
    });
    closeMetricsButton.focus();
    await user.keyboard("{Enter}");

    expect(onTabClose).toHaveBeenCalledWith("tab-2");
  });
});
