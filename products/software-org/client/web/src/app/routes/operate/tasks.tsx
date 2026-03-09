import { useState } from "react";
import {
  CheckSquare,
  Clock,
  AlertCircle,
  MoreVertical,
  Plus,
  Filter,
  Search,
  Calendar,
  User
} from "lucide-react";
import { useTasks } from "@/hooks/useOperateApi";
import { format } from "date-fns";
import { MainLayout } from "@/app/Layout";

export default function Tasks() {
  const { data: tasks, isLoading } = useTasks();
  // const { mutate: updateTask } = useTaskUpdate();
  // const { mutate: completeTask } = useTaskComplete();

  const updateTask = (data: any) => console.log("Update task", data);
  const completeTask = (id: string) => console.log("Complete task", id);

  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredTasks = tasks?.filter(task => {
    if (filter === "pending" && task.status === "completed") return false;
    if (filter === "completed" && task.status !== "completed") return false;
    if (filter === "high-priority" && task.priority !== "high") return false;

    if (searchQuery && !task.title.toLowerCase().includes(searchQuery.toLowerCase())) return false;

    return true;
  }) || [];

  const handleStatusChange = (taskId: string, currentStatus: string) => {
    if (currentStatus === 'completed') {
      updateTask({ taskId, updates: { status: 'in-progress' } });
    } else {
      completeTask(taskId);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

  return (
    <MainLayout title="Tasks & Operations" subtitle="Manage operational tasks and agent assignments">
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2">
              <Plus className="mr-2 h-4 w-4" />
              New Task
            </button>
          </div>

          <div className="flex items-center gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
              <input
                placeholder="Search tasks..."
                className="pl-8 h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setFilter("all")}
                className={`inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border ${filter === "all" ? "bg-secondary text-secondary-foreground" : "bg-background hover:bg-accent"
                  }`}
              >
                All
              </button>
              <button
                onClick={() => setFilter("pending")}
                className={`inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border ${filter === "pending" ? "bg-secondary text-secondary-foreground" : "bg-background hover:bg-accent"
                  }`}
              >
                Pending
              </button>
              <button
                onClick={() => setFilter("high-priority")}
                className={`inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border ${filter === "high-priority" ? "bg-secondary text-secondary-foreground" : "bg-background hover:bg-accent"
                  }`}
              >
                High Priority
              </button>
            </div>
          </div>
        </div>

        <div className="grid gap-4">
          {filteredTasks.map((task) => (
            <div
              key={task.id}
              className={`group flex items-start gap-4 p-4 rounded-xl border bg-card text-card-foreground shadow-sm transition-all hover:shadow-md ${task.status === 'completed' ? 'opacity-60' : ''
                }`}
            >
              <button
                onClick={() => handleStatusChange(task.id, task.status)}
                className={`mt-1 h-5 w-5 rounded border flex items-center justify-center transition-colors ${task.status === 'completed'
                  ? 'bg-primary border-primary text-primary-foreground'
                  : 'border-muted-foreground hover:border-primary'
                  }`}
              >
                {task.status === 'completed' && <CheckSquare className="h-3.5 w-3.5" />}
              </button>

              <div className="flex-1 space-y-1">
                <div className="flex items-center justify-between">
                  <h3 className={`font-semibold ${task.status === 'completed' ? 'line-through text-muted-foreground' : ''}`}>
                    {task.title}
                  </h3>
                  <div className="flex items-center gap-2">
                    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 ${task.priority === 'high'
                      ? 'border-transparent bg-destructive text-destructive-foreground hover:bg-destructive/80'
                      : task.priority === 'medium'
                        ? 'border-transparent bg-yellow-500 text-white hover:bg-yellow-600'
                        : 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80'
                      }`}>
                      {task.priority}
                    </span>
                    <button className="opacity-0 group-hover:opacity-100 p-1 hover:bg-accent rounded transition-opacity">
                      <MoreVertical className="h-4 w-4 text-muted-foreground" />
                    </button>
                  </div>
                </div>

                <p className="text-sm text-muted-foreground line-clamp-2">
                  {task.description}
                </p>

                <div className="flex items-center gap-4 pt-2 text-xs text-muted-foreground">
                  <div className="flex items-center gap-1">
                    <User className="h-3 w-3" />
                    <span>{task.assignee?.name || 'Unassigned'}</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <Calendar className="h-3 w-3" />
                    <span>Due {format(new Date(task.dueDate), 'MMM d, yyyy')}</span>
                  </div>
                  {task.tags && (
                    <div className="flex gap-1">
                      {task.tags.map(tag => (
                        <span key={tag} className="bg-accent px-1.5 py-0.5 rounded text-[10px]">
                          #{tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}

          {filteredTasks.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-center border rounded-xl border-dashed">
              <CheckSquare className="h-12 w-12 text-muted-foreground/50 mb-4" />
              <h3 className="text-lg font-semibold">No tasks found</h3>
              <p className="text-muted-foreground">
                Create a new task to get started
              </p>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}