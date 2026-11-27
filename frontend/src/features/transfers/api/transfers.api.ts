import type {
  components as TransferComponents,
  operations as TransferOperations,
} from "@/types/generated/transfer-api"

import { apiGet, apiPost } from "@/lib/api/http-client"
import type { ApiRequestOptions } from "@/lib/api/http-client"
import { buildQueryString } from "@/lib/api/query-params"

type TransferListOperation = TransferOperations["listTransfers"]
type CreateTransferOperation = TransferOperations["createTransfer"]
type GetTransferOperation = TransferOperations["getTransfer"]
type FindRecipientOperation = TransferOperations["findRecipient"]

export type TransferListQuery =
  NonNullable<TransferListOperation["parameters"]["query"]>

export type CreateTransferRequest =
  CreateTransferOperation["requestBody"]["content"]["application/json"]

export type TransferResponse =
  TransferComponents["schemas"]["TransferResponse"]

export type TransferStatus =
  NonNullable<TransferResponse["status"]>

export type TransferPageResponse =
  TransferListOperation["responses"][200]["content"]["*/*"]

export type RecipientLookupResponse =
  FindRecipientOperation["responses"][200]["content"]["*/*"]

export type TransferRequestOptions = Omit<
  ApiRequestOptions,
  "method" | "body"
>

export type RecipientLookupRequestOptions =
  TransferRequestOptions

export type TransferPath =
  GetTransferOperation["parameters"]["path"]

/**
 * Create a transfer using the backend's idempotency contract.
 *
 * The caller is responsible for retaining and reusing the same
 * idempotency key and request payload after an ambiguous result.
 */
export function createTransfer(
  request: CreateTransferRequest,
): Promise<TransferResponse> {
  return apiPost<CreateTransferRequest, TransferResponse>(
    "/api/v1/transfers",
    request,
  )
}

/**
 * Get a single transfer owned by the authenticated customer.
 */
export function getTransfer(
  transferId: TransferPath["id"],
  options: TransferRequestOptions = {},
): Promise<TransferResponse> {
  return apiGet<TransferResponse>(
    `/api/v1/transfers/${encodeURIComponent(transferId)}`,
    options,
  )
}

/**
 * List transfers owned by the authenticated customer.
 */
export function listTransfers(
  query: TransferListQuery = {},
): Promise<TransferPageResponse> {
  return apiGet<TransferPageResponse>(
    `/api/v1/transfers${buildQueryString(query)}`,
  )
}

/**
 * Verify a Bank B recipient account before initiating a transfer.
 */
export function verifyRecipient(
  accountNumber: string,
  options: RecipientLookupRequestOptions = {},
): Promise<RecipientLookupResponse> {
  const normalizedAccountNumber = accountNumber.trim()

  return apiGet<RecipientLookupResponse>(
    `/api/v1/recipients/${encodeURIComponent(normalizedAccountNumber)}`,
    options,
  )
}
