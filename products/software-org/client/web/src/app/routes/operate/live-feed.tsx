import { useState, useEffect, useRef } from "react";
import {
  Activity,
  Filter,
  Search,
  MessageSquare,
  GitCommit,
  AlertTriangle,
  CheckCircle2,
  Clock,
  MoreHorizontal
} from "lucide-react";
import { useLiveFeed } from "@/hooks/useOperateApi";
import { formatDistanceToNow } from "date-fns";
import { MainLayout } from "@/app/Layout";

export default function LiveFeed() {
  const { data: feedItems, isLoading } = useLiveFeed();
  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to top when new items arrive
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [feedItems]);

  const filteredItems = feedItems?.filter(item => {
    if (filter !== "all" && item.type !== filter) return false;
    if (searchQuery && !item.content.toLowerCase().includes(searchQuery.toLowerCase()) &&
      !item.actor.name.toLowerCase().includes(searchQuery.toLowerCase())) return false;
    return true;
  }) || [];

  const getIconForType = (type: string) => {
    switch (type) {
      case 'commit': return <GitCommit className="h-4 w-4 text-blue-500" />;
      case 'alert': return <AlertTriangle className="h-4 w-4 text-red-500" />;
      case 'task': return <CheckCircle2 className="h-4 w-4 text-green-500" />;
      case 'discussion': return <MessageSquare className="h-4 w-4 text-purple-500" />;
      default: return <Activity className="h-4 w-4 text-gray-500" />;
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
    <MainLayout title="Live Feed" subtitle="Real-time updates across your organization">
      <div className="h-[calc(100vh-4rem)] flex flex-col space-y-4">
        <div className="flex justify-between items-center">
          <div>
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <input
                  placeholder="Search feed..."
                  className="pl-8 h-9 w-[200px] rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <select
                className="h-9 w-[150px] rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              >
                <option value="all">All Activity</option>
                <option value="commit">Code Commits</option>
                <option value="alert">Alerts</option>
                <option value="task">Tasks</option>
                <option value="discussion">Discussions</option>
              </select>
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-hidden rounded-xl border bg-card text-card-foreground shadow-sm">
          <div className="h-full overflow-y-auto p-6" ref={scrollRef}>
            <div className="space-y-8">
              {filteredItems.map((item) => (
                <div key={item.id} className="flex gap-4 group">
                  <div className="flex flex-col items-center">
                    <div className="relative flex h-10 w-10 items-center justify-center rounded-full border bg-background shadow-sm">
                      <img
                        src={item.actor.avatar}
                        alt={item.actor.name}
                        className="h-10 w-10 rounded-full object-cover"
                      />
                      <div className="absolute -bottom-1 -right-1 rounded-full bg-background p-0.5 shadow-sm border">
                        {getIconForType(item.type)}
                      </div>
                    </div>
                    <div className="h-full w-px bg-border my-2 group-last:hidden" />
                  </div>
                  <div className="flex-1 space-y-1 pb-8 group-last:pb-0">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm">{item.actor.name}</span>
                        <span className="text-muted-foreground text-sm">
                          {item.action}
                        </span>
                        <span className="font-medium text-sm text-primary">
                          {item.target}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        {formatDistanceToNow(new Date(item.timestamp), { addSuffix: true })}
                        <button className="opacity-0 group-hover:opacity-100 transition-opacity p-1 hover:bg-accent rounded">
                          <MoreHorizontal className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                    <div className="text-sm text-muted-foreground bg-accent/30 p-3 rounded-md border">
                      {item.content}
                    </div>
                    {item.metadata && (
                      <div className="flex gap-2 mt-2">
                        {Object.entries(item.metadata).map(([key, value]) => (
                          <span key={key} className="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80">
                            {key}: {value}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {filteredItems.length === 0 && (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <Activity className="h-12 w-12 text-muted-foreground/50 mb-4" />
                  <h3 className="text-lg font-semibold">No activity found</h3>
                  <p className="text-muted-foreground">
                    Try adjusting your filters or search query
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}