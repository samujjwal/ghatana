import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: () => apiClient.getDashboard()
  });
}



