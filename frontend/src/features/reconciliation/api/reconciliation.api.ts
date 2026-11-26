import type {
  components as ReconciliationComponents,
  operations as ReconciliationOperations,
} from "@/types/generated/reconciliation-api"

import { apiGet, apiPost } from "@/lib/api/http-client"

type RunManualOperation = ReconciliationOperations["runManual"]
type GetRunOperation = ReconciliationOperations["getRun"]

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

/**
 * Execute manual reconciliation for a business date.
 */
export function runManualReconciliation(
  request: ManualReconciliationRequest,
): Promise<ReconciliationRunDetails> {
  return apiPost<ManualReconciliationRequest, ReconciliationRunDetails>(
    "/api/v1/reconciliation/run",
    request,
  )
}

/**
 * Retrieve persisted reconciliation run details.
 */
export function getReconciliationRun(
  runId: ReconciliationRunPath["id"],
): Promise<ReconciliationRunDetails> {
  return apiGet<ReconciliationRunDetails>(
    `/api/v1/reconciliation/runs/${encodeURIComponent(runId)}`,
  )
}
