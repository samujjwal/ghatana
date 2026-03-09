/**
 * Skeleton Loading Components
 * 
 * Provides accessible skeleton loaders for better perceived performance.
 * Shows placeholder content while data is loading.
 * 
 * @doc.type component
 * @doc.purpose Display loading placeholders
 * @doc.layer product
 * @doc.pattern Component
 */

export function MomentCardSkeleton() {
  return (
    <div 
      className="bg-white rounded-lg shadow p-6 animate-pulse"
      role="status"
      aria-label="Loading moment"
      aria-busy="true"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="h-6 bg-gray-200 rounded w-24"></div>
        <div className="h-4 bg-gray-200 rounded w-32"></div>
      </div>

      {/* Content */}
      <div className="space-y-3 mb-4">
        <div className="h-4 bg-gray-200 rounded w-full"></div>
        <div className="h-4 bg-gray-200 rounded w-5/6"></div>
        <div className="h-4 bg-gray-200 rounded w-4/6"></div>
      </div>

      {/* Tags */}
      <div className="flex gap-2 mb-4">
        <div className="h-6 bg-gray-200 rounded-full w-16"></div>
        <div className="h-6 bg-gray-200 rounded-full w-20"></div>
        <div className="h-6 bg-gray-200 rounded-full w-16"></div>
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between">
        <div className="h-4 bg-gray-200 rounded w-24"></div>
        <div className="h-8 bg-gray-200 rounded w-16"></div>
      </div>
      <span className="sr-only">Loading moment data, please wait</span>
    </div>
  );
}

export function MomentsListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div 
      className="space-y-4"
      role="status"
      aria-label={`Loading ${count} moments`}
      aria-busy="true"
    >
      {Array.from({ length: count }).map((_, i) => (
        <MomentCardSkeleton key={i} />
      ))}
      <span className="sr-only">Loading moments list, please wait</span>
    </div>
  );
}

export function StatsCardSkeleton() {
  return (
    <div 
      className="bg-white rounded-lg shadow p-6 animate-pulse"
      role="status"
      aria-label="Loading statistics"
      aria-busy="true"
    >
      <div className="h-8 bg-gray-200 rounded w-16 mb-2"></div>
      <div className="h-4 bg-gray-200 rounded w-24"></div>
      <span className="sr-only">Loading statistics, please wait</span>
    </div>
  );
}

export function StatsDashboardSkeleton() {
  return (
    <div 
      className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4"
      role="status"
      aria-label="Loading dashboard statistics"
      aria-busy="true"
    >
      {Array.from({ length: 4 }).map((_, i) => (
        <StatsCardSkeleton key={i} />
      ))}
      <span className="sr-only">Loading dashboard data, please wait</span>
    </div>
  );
}

export function SphereCardSkeleton() {
  return (
    <div 
      className="bg-white rounded-lg shadow p-4 animate-pulse"
      role="status"
      aria-label="Loading sphere"
      aria-busy="true"
    >
      <div className="flex items-center justify-between mb-3">
        <div className="h-6 bg-gray-200 rounded w-32"></div>
        <div className="h-8 bg-gray-200 rounded-full w-8"></div>
      </div>
      <div className="h-4 bg-gray-200 rounded w-full mb-2"></div>
      <div className="h-4 bg-gray-200 rounded w-3/4"></div>
      <span className="sr-only">Loading sphere data, please wait</span>
    </div>
  );
}

export function SpheresGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div 
      className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
      role="status"
      aria-label={`Loading ${count} spheres`}
      aria-busy="true"
    >
      {Array.from({ length: count }).map((_, i) => (
        <SphereCardSkeleton key={i} />
      ))}
      <span className="sr-only">Loading spheres list, please wait</span>
    </div>
  );
}

export function InputSkeleton() {
  return (
    <div 
      className="h-10 bg-gray-200 rounded animate-pulse"
      role="status"
      aria-label="Loading input field"
      aria-busy="true"
    >
      <span className="sr-only">Loading, please wait</span>
    </div>
  );
}

export function ButtonSkeleton() {
  return (
    <div 
      className="h-10 bg-gray-200 rounded animate-pulse"
      role="status"
      aria-label="Loading button"
      aria-busy="true"
    >
      <span className="sr-only">Loading, please wait</span>
    </div>
  );
}

export function PageSkeleton() {
  return (
    <div 
      className="space-y-6"
      role="status"
      aria-label="Loading page content"
      aria-busy="true"
    >
      {/* Header */}
      <div className="h-8 bg-gray-200 rounded w-48 animate-pulse"></div>

      {/* Stats */}
      <StatsDashboardSkeleton />

      {/* Content */}
      <div className="space-y-4">
        <div className="h-6 bg-gray-200 rounded w-32 animate-pulse"></div>
        <MomentsListSkeleton />
      </div>
      <span className="sr-only">Loading page, please wait</span>
    </div>
  );
}
