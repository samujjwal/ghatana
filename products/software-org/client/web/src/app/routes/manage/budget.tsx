import { useState } from "react";
import {
  DollarSign,
  TrendingUp,
  TrendingDown,
  PieChart,
  AlertCircle,
  Plus,
  Filter,
  Download,
  Calendar
} from "lucide-react";
import { useBudgetPlan, useUpdateBudget } from "@/hooks/useManageApi";
import { useDepartments } from "@/hooks/useOrganizationApi";
import { MainLayout } from "@/app/Layout";

export default function Budget() {
  const currentYear = new Date().getFullYear();
  const { data: budgetPlan, isLoading: isBudgetLoading } = useBudgetPlan(currentYear);
  const { mutate: updateBudget } = useUpdateBudget();
  const { data: departments } = useDepartments();

  const [selectedCategory, setSelectedCategory] = useState<string>("all");
  const [selectedDepartment, setSelectedDepartment] = useState<string>("all");

  const categories = [
    { id: 'personnel', name: 'Personnel' },
    { id: 'software', name: 'Software' },
    { id: 'infrastructure', name: 'Infrastructure' },
    { id: 'training', name: 'Training' },
    { id: 'travel', name: 'Travel' },
    { id: 'other', name: 'Other' },
  ];

  if (isBudgetLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

  const totalBudget = budgetPlan?.totalBudget || 0;
  const spentBudget = budgetPlan?.totalSpent || 0;
  const remainingBudget = totalBudget - spentBudget;
  const burnRate = totalBudget > 0 ? (spentBudget / totalBudget) * 100 : 0;

  // Transform budgets into display items
  const allItems = (budgetPlan?.budgets || []).flatMap(deptBudget => {
    return Object.entries(deptBudget.categories).map(([catName, amount]) => ({
      id: `${deptBudget.id}-${catName}`,
      name: `${deptBudget.departmentName} - ${catName}`,
      departmentId: deptBudget.departmentId,
      category: catName,
      allocated: amount,
      spent: 0, // Not available in API yet
    }));
  });

  const filteredItems = allItems.filter(item => {
    if (selectedCategory !== "all" && item.category !== selectedCategory) return false;
    if (selectedDepartment !== "all" && item.departmentId !== selectedDepartment) return false;
    return true;
  });

  const handleUpdateAllocation = (itemId: string, amount: number) => {
    // updateBudget({ ... }); // Implementation depends on how we want to edit
    console.log("Update allocation", itemId, amount);
  };

  return (
    <MainLayout title="Budget & Allocation" subtitle="Manage financial resources across departments and initiatives">
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <div className="flex gap-2">
              <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2">
                <Download className="mr-2 h-4 w-4" />
                Export Report
              </button>
              <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2">
                <Plus className="mr-2 h-4 w-4" />
                New Allocation
              </button>
            </div>
          </div>
        </div>

        {/* Overview Cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-xl border bg-card text-card-foreground shadow-sm p-6">
            <div className="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 className="tracking-tight text-sm font-medium">Total Budget</h3>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="text-2xl font-bold">${totalBudget.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              FY {currentYear} Allocation
            </p>
          </div>
          <div className="rounded-xl border bg-card text-card-foreground shadow-sm p-6">
            <div className="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 className="tracking-tight text-sm font-medium">Spent</h3>
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="text-2xl font-bold">${spentBudget.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              {burnRate.toFixed(1)}% of total budget
            </p>
          </div>
          <div className="rounded-xl border bg-card text-card-foreground shadow-sm p-6">
            <div className="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 className="tracking-tight text-sm font-medium">Remaining</h3>
              <TrendingDown className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="text-2xl font-bold">${remainingBudget.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              Available for allocation
            </p>
          </div>
          <div className="rounded-xl border bg-card text-card-foreground shadow-sm p-6">
            <div className="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 className="tracking-tight text-sm font-medium">Projected</h3>
              <PieChart className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="text-2xl font-bold">${(spentBudget * 1.1).toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              Based on current burn rate
            </p>
          </div>
        </div>

        {/* Main Content */}
        <div className="grid gap-6 md:grid-cols-3">
          {/* Budget Breakdown */}
          <div className="md:col-span-2 space-y-6">
            <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
              <div className="p-6 flex flex-col space-y-1.5">
                <div className="flex items-center justify-between">
                  <h3 className="font-semibold leading-none tracking-tight">Budget Allocation</h3>
                  <div className="flex gap-2">
                    <select
                      className="h-9 w-[150px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                      value={selectedDepartment}
                      onChange={(e) => setSelectedDepartment(e.target.value)}
                    >
                      <option value="all">All Departments</option>
                      {departments?.map(dept => (
                        <option key={dept.id} value={dept.id}>{dept.name}</option>
                      ))}
                    </select>
                    <select
                      className="h-9 w-[150px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                      value={selectedCategory}
                      onChange={(e) => setSelectedCategory(e.target.value)}
                    >
                      <option value="all">All Categories</option>
                      {categories.map(cat => (
                        <option key={cat.id} value={cat.id}>{cat.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
              <div className="p-6 pt-0">
                <div className="space-y-4">
                  {filteredItems.map((item) => (
                    <div key={item.id} className="flex items-center justify-between p-4 border rounded-lg">
                      <div className="space-y-1">
                        <p className="font-medium leading-none">{item.name}</p>
                        <p className="text-sm text-muted-foreground capitalize">{item.category}</p>
                      </div>
                      <div className="flex items-center gap-4">
                        <div className="text-right">
                          <p className="font-medium">${item.allocated.toLocaleString()}</p>
                          <p className="text-sm text-muted-foreground">
                            {((item.spent / item.allocated) * 100).toFixed(0)}% used
                          </p>
                        </div>
                        <div className="w-24 h-2 bg-secondary rounded-full overflow-hidden">
                          <div
                            className={`h-full ${(item.spent / item.allocated) > 0.9 ? 'bg-destructive' :
                              (item.spent / item.allocated) > 0.7 ? 'bg-yellow-500' : 'bg-primary'
                              }`}
                            style={{ width: `${Math.min((item.spent / item.allocated) * 100, 100)}%` }}
                          />
                        </div>
                      </div>
                    </div>
                  ))}

                  {filteredItems.length === 0 && (
                    <div className="text-center py-8 text-muted-foreground">
                      No budget items found matching your filters.
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Alerts & Insights */}
          <div className="space-y-6">
            <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
              <div className="p-6 flex flex-col space-y-1.5">
                <h3 className="font-semibold leading-none tracking-tight">Budget Alerts</h3>
              </div>
              <div className="p-6 pt-0">
                <div className="space-y-4">
                  {/* Alerts removed as they are not in API yet */}
                  <div className="text-sm text-muted-foreground text-center py-4">
                    No active alerts
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
              <div className="p-6 flex flex-col space-y-1.5">
                <h3 className="font-semibold leading-none tracking-tight">Quick Actions</h3>
              </div>
              <div className="p-6 pt-0 space-y-2">
                <button className="w-full justify-start text-left font-normal h-auto py-2 px-4 inline-flex items-center rounded-md text-sm ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground">
                  <Plus className="mr-2 h-4 w-4" />
                  Request Budget Increase
                </button>
                <button className="w-full justify-start text-left font-normal h-auto py-2 px-4 inline-flex items-center rounded-md text-sm ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground">
                  <TrendingUp className="mr-2 h-4 w-4" />
                  View Forecast
                </button>
                <button className="w-full justify-start text-left font-normal h-auto py-2 px-4 inline-flex items-center rounded-md text-sm ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground">
                  <Calendar className="mr-2 h-4 w-4" />
                  Schedule Review
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}