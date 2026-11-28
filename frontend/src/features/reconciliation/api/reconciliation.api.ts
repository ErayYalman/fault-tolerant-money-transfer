import type {
  components as ReconciliationComponents,
  operations as ReconciliationOperations,
} from "@/types/generated/reconciliation-api"

import type { ApiRequestOptions } from "@/lib/api/http-client"
import { apiGet, apiPost } from "@/lib/api/http-client"
import { buildQueryString } from "@/lib/api/query-params"

type RunManualOperation = ReconciliationOperations["runManual"]
type GetRunOperation = ReconciliationOperations["getRun"]
type ListRunsOperation = ReconciliationOperations["listRuns"]
type DailySummaryOperation = ReconciliationOperations["getDailySummary"]
type ListAnomaliesOperation = ReconciliationOperations["listAnomalies"]

export type ManualReconciliationRequest =
  RunManualOperation["requestBody"]["content"]["application/json"]

export type ReconciliationRunDetails =
  RunManualOperation["responses"][200]["content"]["application/json"]

export type ReconciliationDiscrepancy =
  ReconciliationComponents["schemas"]["ReconciliationDiscrepancyResponse"]

/**
 * Statistical anomaly score. This is not a calibrated probability.
 */
export type AnomalyScore =
  ReconciliationComponents["schemas"]["AnomalyScoreResponse"]

export type AnomalySummary =
  ReconciliationComponents["schemas"]["AnomalySummaryResponse"]

export type ReconciliationRunPath =
  GetRunOperation["parameters"]["path"]

export type ReconciliationRunsQuery =
  NonNullable<ListRunsOperation["parameters"]["query"]>

export type ReconciliationRunsPage =
  ReconciliationComponents["schemas"]["ReconciliationRunsPageResponse"]

export type ReconciliationDailySummaryQuery =
  NonNullable<DailySummaryOperation["parameters"]["query"]>

export type ReconciliationDailySummary =
  ReconciliationComponents["schemas"]["ReconciliationDailySummaryResponse"]

export type ReconciliationAnomaliesQuery =
  NonNullable<ListAnomaliesOperation["parameters"]["query"]>

export type ReconciliationAnomaliesPage =
  ReconciliationComponents["schemas"]["ReconciliationAnomaliesPageResponse"]

export type ReconciliationRequestOptions = Omit<
  ApiRequestOptions,
  "method" | "body"
>

/**
 * Execute manual reconciliation for a business date.
 */
export function runManualReconciliation(
  request: ManualReconciliationRequest,
  options: ReconciliationRequestOptions = {},
): Promise<ReconciliationRunDetails> {
  return apiPost<ManualReconciliationRequest, ReconciliationRunDetails>(
    "/api/v1/reconciliation/run",
    request,
    options,
  )
}

/**
 * Retrieve persisted reconciliation run details.
 */
export function getReconciliationRun(
  runId: ReconciliationRunPath["id"],
  options: ReconciliationRequestOptions = {},
): Promise<ReconciliationRunDetails> {
  return apiGet<ReconciliationRunDetails>(
    `/api/v1/reconciliation/runs/${encodeURIComponent(runId)}`,
    options,
  )
}

/**
 * List all reconciliation runs within an inclusive business-date range.
 */
export function listReconciliationRuns(
  query: ReconciliationRunsQuery,
  options: ReconciliationRequestOptions = {},
): Promise<ReconciliationRunsPage> {
  return apiGet<ReconciliationRunsPage>(
    `/api/v1/reconciliation/runs${buildQueryString(query)}`,
    options,
  )
}

/**
 * Retrieve daily reconciliation aggregates.
 */
export function getReconciliationDailySummary(
  query: ReconciliationDailySummaryQuery,
  options: ReconciliationRequestOptions = {},
): Promise<ReconciliationDailySummary> {
  return apiGet<ReconciliationDailySummary>(
    `/api/v1/reconciliation/reports/summary${buildQueryString(query)}`,
    options,
  )
}

/**
 * List anomaly scores from the latest completed run for each business date.
 */
export function listReconciliationAnomalies(
  query: ReconciliationAnomaliesQuery,
  options: ReconciliationRequestOptions = {},
): Promise<ReconciliationAnomaliesPage> {
  return apiGet<ReconciliationAnomaliesPage>(
    `/api/v1/reconciliation/reports/anomalies${buildQueryString(query)}`,
    options,
  )
}