import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"

import {
  getReconciliationDailySummary,
  getReconciliationRun,
  listReconciliationAnomalies,
  listReconciliationRuns,
  runManualReconciliation,
  type ManualReconciliationRequest,
  type ReconciliationAnomaliesQuery,
  type ReconciliationDailySummaryQuery,
  type ReconciliationRunsQuery,
} from "@/features/reconciliation/api/reconciliation.api"

export const reconciliationQueryKeys = {
  all: ["reconciliation"] as const,

  run: (runId: string) =>
    ["reconciliation", "run", runId] as const,

  runs: (userId: string, query: ReconciliationRunsQuery) =>
    ["reconciliation", "runs", userId, query] as const,

  reports: ["reconciliation", "reports"] as const,

  dailySummary: (
    userId: string,
    query: ReconciliationDailySummaryQuery,
  ) =>
    ["reconciliation", "reports", "summary", userId, query] as const,

  anomalies: (
    userId: string,
    query: ReconciliationAnomaliesQuery,
  ) =>
    ["reconciliation", "reports", "anomalies", userId, query] as const,
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

      void queryClient.invalidateQueries({
        queryKey: ["reconciliation", "runs"],
      })

      void queryClient.invalidateQueries({
        queryKey: reconciliationQueryKeys.reports,
      })
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

/**
 * Retrieves paginated reconciliation run history.
 */
export function useReconciliationRuns(
  query: ReconciliationRunsQuery,
) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: reconciliationQueryKeys.runs(
      user?.id ?? "anonymous",
      query,
    ),

    queryFn: ({ signal }) =>
      listReconciliationRuns(query, { signal }),

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      Boolean(query.fromDate && query.toDate),

    staleTime: 30_000,
  })
}

/**
 * Retrieves daily reconciliation metrics.
 */
export function useReconciliationDailySummary(
  query: ReconciliationDailySummaryQuery,
) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: reconciliationQueryKeys.dailySummary(
      user?.id ?? "anonymous",
      query,
    ),

    queryFn: ({ signal }) =>
      getReconciliationDailySummary(query, { signal }),

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      Boolean(query.fromDate && query.toDate),

    staleTime: 30_000,
  })
}

/**
 * Retrieves paginated anomaly scores.
 */
export function useReconciliationAnomalies(
  query: ReconciliationAnomaliesQuery,
) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: reconciliationQueryKeys.anomalies(
      user?.id ?? "anonymous",
      query,
    ),

    queryFn: ({ signal }) =>
      listReconciliationAnomalies(query, { signal }),

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      Boolean(query.fromDate && query.toDate),

    staleTime: 30_000,
  })
}