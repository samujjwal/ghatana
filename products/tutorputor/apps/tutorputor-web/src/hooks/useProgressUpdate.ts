import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

// Local type definition
type EnrollmentId = string;

export function useProgressUpdate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      enrollmentId,
      progressPercent,
      timeSpentSecondsDelta
    }: {
      enrollmentId: EnrollmentId;
      progressPercent: number;
      timeSpentSecondsDelta: number;
    }) =>
      apiClient.updateProgress(
        enrollmentId,
        progressPercent,
        timeSpentSecondsDelta
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["module"] });
    }
  });
}



