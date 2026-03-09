import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

export function useModuleBySlug(slug: string) {
  return useQuery({
    queryKey: ["module", slug],
    queryFn: () => apiClient.getModuleBySlug(slug),
    enabled: !!slug
  });
}



