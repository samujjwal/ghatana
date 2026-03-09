import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

// Local type definition
interface ModuleSummary {
  id: string;
  title: string;
  slug: string;
  description: string;
  thumbnailUrl?: string;
  estimatedMinutes: number;
  difficulty: "beginner" | "intermediate" | "advanced";
  tags?: string[];
}

export function useModules(domain?: string) {
  return useQuery<{ items: ModuleSummary[]; nextCursor?: string | null }>({
    queryKey: ["modules", domain],
    queryFn: () => apiClient.listModules(domain)
  });
}



