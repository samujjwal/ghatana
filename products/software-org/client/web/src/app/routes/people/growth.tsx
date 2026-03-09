import { useState } from "react";
import {
  Target,
  TrendingUp,
  Award,
  BookOpen,
  Plus,
  ChevronRight,
  Search,
  MoreHorizontal,
  CheckCircle2,
  Clock
} from "lucide-react";
import { useGrowthPlans, useCreateGrowthPlan } from "@/hooks/usePeopleApi";
import { format } from "date-fns";
import { MainLayout } from "@/app/Layout";

export default function Growth() {
  const { data: plans, isLoading } = useGrowthPlans();
  const { mutate: createPlan } = useCreateGrowthPlan();

  const [searchQuery, setSearchQuery] = useState("");

  const filteredPlans = plans?.filter(plan => {
    if (searchQuery && !plan.employeeName.toLowerCase().includes(searchQuery.toLowerCase())) return false;
    return true;
  }) || [];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

  return (
    <MainLayout title="Growth & Development" subtitle="Track career progression and skill development plans">
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2">
              <Plus className="mr-2 h-4 w-4" />
              New Growth Plan
            </button>
          </div>

          <div className="flex items-center gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
              <input
                placeholder="Search employees..."
                className="pl-8 h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </div>

        <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
          {filteredPlans.map((plan) => (
            <div
              key={plan.id}
              className="group flex flex-col rounded-xl border bg-card text-card-foreground shadow-sm transition-all hover:shadow-md overflow-hidden"
            >
              <div className="p-6 pb-4 border-b bg-muted/30">
                <div className="flex justify-between items-start mb-4">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center text-primary font-semibold">
                      {plan.employeeName.charAt(0)}
                    </div>
                    <div>
                      <h3 className="font-semibold">{plan.employeeName}</h3>
                      <p className="text-sm text-muted-foreground">{plan.role}</p>
                    </div>
                  </div>
                  <button className="opacity-0 group-hover:opacity-100 p-1 hover:bg-accent rounded transition-opacity">
                    <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                  </button>
                </div>

                <div className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-1.5 text-muted-foreground">
                    <Target className="h-4 w-4" />
                    <span>{plan.goals.length} Goals</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-muted-foreground">
                    <Clock className="h-4 w-4" />
                    <span>Due {format(new Date(plan.reviewDate), 'MMM yyyy')}</span>
                  </div>
                </div>
              </div>

              <div className="p-6 space-y-4 flex-1">
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Focus Areas</h4>
                  {plan.goals.slice(0, 3).map((goal) => (
                    <div key={goal.id} className="space-y-1.5">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium">{goal.title}</span>
                        <span className={`text-xs px-2 py-0.5 rounded-full ${goal.status === 'completed' ? 'bg-green-100 text-green-700' :
                          goal.status === 'in-progress' ? 'bg-blue-100 text-blue-700' :
                            'bg-gray-100 text-gray-700'
                          }`}>
                          {goal.progress}%
                        </span>
                      </div>
                      <div className="h-1.5 w-full bg-secondary rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${goal.status === 'completed' ? 'bg-green-500' : 'bg-primary'
                            }`}
                          style={{ width: `${goal.progress}%` }}
                        />
                      </div>
                    </div>
                  ))}
                  {plan.goals.length > 3 && (
                    <p className="text-xs text-center text-muted-foreground pt-2">
                      + {plan.goals.length - 3} more goals
                    </p>
                  )}
                </div>
              </div>

              <div className="p-4 border-t bg-muted/10 flex justify-between items-center">
                <div className="flex gap-2">
                  {plan.mentors?.map((mentor, i) => (
                    <div
                      key={i}
                      className="h-6 w-6 rounded-full bg-background border flex items-center justify-center text-[10px] font-medium"
                      title={`Mentor: ${mentor}`}
                    >
                      {mentor.charAt(0)}
                    </div>
                  ))}
                </div>
                <button className="text-sm font-medium text-primary hover:underline inline-flex items-center">
                  View Details
                  <ChevronRight className="h-4 w-4 ml-1" />
                </button>
              </div>
            </div>
          ))}

          {/* Add New Card */}
          <button className="flex flex-col items-center justify-center rounded-xl border border-dashed p-6 hover:bg-accent/50 transition-colors min-h-[300px] gap-4 group">
            <div className="h-12 w-12 rounded-full bg-secondary flex items-center justify-center group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
              <Plus className="h-6 w-6" />
            </div>
            <div className="text-center">
              <h3 className="font-semibold">Create Growth Plan</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Set goals and track development
              </p>
            </div>
          </button>
        </div>
      </div>
    </MainLayout>
  );
}