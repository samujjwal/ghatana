import { useState } from "react";
import {
  Star,
  Calendar,
  User,
  FileText,
  CheckCircle2,
  Clock,
  ChevronRight,
  Filter,
  Search,
  Plus
} from "lucide-react";
import { usePerformanceReviews, useSubmitReview } from "@/hooks/usePeopleApi";
import { format } from "date-fns";
import { MainLayout } from "@/app/Layout";

export default function Reviews() {
  const { data: reviews, isLoading } = usePerformanceReviews();
  const { mutate: submitReview } = useSubmitReview();

  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredReviews = reviews?.filter(review => {
    if (filter === "pending" && review.status === "completed") return false;
    if (filter === "completed" && review.status !== "completed") return false;

    if (searchQuery && review.employeeName && !review.employeeName.toLowerCase().includes(searchQuery.toLowerCase())) return false;

    return true;
  }) || [];

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'completed': return 'bg-green-100 text-green-700 border-green-200';
      case 'in-progress': return 'bg-blue-100 text-blue-700 border-blue-200';
      case 'scheduled': return 'bg-yellow-100 text-yellow-700 border-yellow-200';
      default: return 'bg-gray-100 text-gray-700 border-gray-200';
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
    <MainLayout title="Performance Reviews" subtitle="Manage employee evaluations and feedback cycles">
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2">
              <Plus className="mr-2 h-4 w-4" />
              Schedule Review
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
                onClick={() => setFilter("completed")}
                className={`inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border ${filter === "completed" ? "bg-secondary text-secondary-foreground" : "bg-background hover:bg-accent"
                  }`}
              >
                Completed
              </button>
            </div>
          </div>
        </div>

        <div className="grid gap-4">
          {filteredReviews.map((review) => (
            <div
              key={review.id}
              className="group flex flex-col md:flex-row md:items-center gap-4 p-6 rounded-xl border bg-card text-card-foreground shadow-sm transition-all hover:shadow-md"
            >
              <div className="flex items-center gap-4 min-w-[200px]">
                <div className="h-12 w-12 rounded-full bg-secondary flex items-center justify-center text-lg font-semibold">
                  {review.employeeName?.charAt(0) || '?'}
                </div>
                <div>
                  <h3 className="font-semibold">{review.employeeName || 'Unknown'}</h3>
                  <p className="text-sm text-muted-foreground">{review.role || 'N/A'}</p>
                </div>
              </div>

              <div className="flex-1 grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground uppercase tracking-wider">Cycle</p>
                  <p className="text-sm font-medium">{review.cycle}</p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground uppercase tracking-wider">Date</p>
                  <div className="flex items-center gap-1.5 text-sm font-medium">
                    <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                    {review.date ? format(new Date(review.date), 'MMM d, yyyy') : 'N/A'}
                  </div>
                </div>
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground uppercase tracking-wider">Reviewer</p>
                  <div className="flex items-center gap-1.5 text-sm font-medium">
                    <User className="h-3.5 w-3.5 text-muted-foreground" />
                    {review.reviewerName}
                  </div>
                </div>
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground uppercase tracking-wider">Status</p>
                  <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 ${getStatusColor(review.status)}`}>
                    {review.status}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-4 md:border-l md:pl-6">
                {review.status === 'completed' ? (
                  <div className="text-center min-w-[60px]">
                    <p className="text-2xl font-bold text-primary">{review.overallRating}</p>
                    <div className="flex text-yellow-400 text-xs">
                      {[...Array(5)].map((_, i) => (
                        <Star key={i} className={`h-3 w-3 ${i < Math.round(review.overallRating || 0) ? 'fill-current' : 'text-gray-300'}`} />
                      ))}
                    </div>
                  </div>
                ) : (
                  <button className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-9 px-4 py-2">
                    Start Review
                  </button>
                )}
                <button className="p-2 hover:bg-accent rounded-full transition-colors">
                  <ChevronRight className="h-5 w-5 text-muted-foreground" />
                </button>
              </div>
            </div>
          ))}

          {filteredReviews.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-center border rounded-xl border-dashed">
              <FileText className="h-12 w-12 text-muted-foreground/50 mb-4" />
              <h3 className="text-lg font-semibold">No reviews found</h3>
              <p className="text-muted-foreground">
                Adjust filters or schedule a new review cycle
              </p>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}