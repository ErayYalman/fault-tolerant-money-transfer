import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query"

import {
  getReconciliationRun,
  runManualReconciliation,
  type ManualReconciliationRequest,
} from "@/features/reconciliation/api/reconciliation.api"

export const reconciliationQueryKeys = {
  all: ["reconciliation"] as const,

  run: (runId: string) =>
    ["reconciliation", "run", runId] as const,
}

/**
 * Executes a manual reconciliation.
 *
 * Automatic retries are disabled because the backend does not
 * currently provide an idempotency contract for manual runs.
 */
export function useRunManualReconciliation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ["reconciliation", "manual-run"],

    mutationFn: (request: ManualReconciliationRequest) =>
      runManualReconciliation(request),

    retry: false,

    onSuccess: (run) => {
      if (run.runId) {
        queryClient.setQueryData(
          reconciliationQueryKeys.run(run.runId),
          run,
        )
      }
    },
  })
}

/**
 * Retrieves persisted details for a known reconciliation run.
 */
export function useReconciliationRun(
  runId: string | undefined,
) {
  return useQuery({
    queryKey: reconciliationQueryKeys.run(runId ?? "missing"),

    queryFn: ({ signal }) => {
      if (!runId) {
        throw new Error("Mutabakat çalışma kimliği bulunamadı.")
      }

      return getReconciliationRun(runId, { signal })
    },

    enabled: Boolean(runId),

    staleTime: 30_000,
  })
}
