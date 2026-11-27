import type {
  components as PaymentAComponents,
  operations as PaymentAOperations,
} from "@/types/generated/payment-a-api"

import type { ApiRequestOptions } from "@/lib/api/http-client"
import { apiGet } from "@/lib/api/http-client"
import { buildQueryString } from "@/lib/api/query-params"

type AccountListOperation = PaymentAOperations["findOwnAccounts"]
type AccountLedgerOperation = PaymentAOperations["findOwnAccountLedger"]

export type AccountListQuery =
  NonNullable<AccountListOperation["parameters"]["query"]>

export type AccountLedgerQuery =
  NonNullable<AccountLedgerOperation["parameters"]["query"]>

export type AccountLedgerPath =
  AccountLedgerOperation["parameters"]["path"]

export type AccountSummary =
  PaymentAComponents["schemas"]["AccountSummaryResponse"]

export type AccountPageResponse =
  AccountListOperation["responses"][200]["content"]["*/*"]

export type AccountRequestOptions = Omit<
  ApiRequestOptions,
  "method" | "body"
>

export type AccountLedgerRequestOptions = Omit<
  ApiRequestOptions,
  "method" | "body"
>

export type LedgerEntry =
  PaymentAComponents["schemas"]["LedgerEntryResponse"]

export type LedgerEntryPageResponse =
  AccountLedgerOperation["responses"][200]["content"]["*/*"]

/**
 * Authenticated customer's Bank A accounts.
 */
export function listAccounts(
  query: AccountListQuery = {},
  options: AccountRequestOptions = {},
): Promise<AccountPageResponse> {
  return apiGet<AccountPageResponse>(
    `/api/v1/accounts${buildQueryString(query)}`,
    options,
  )
}

/**
 * Paginated ledger entries for an owned Bank A account.
 */
export function getAccountLedger(
  accountId: AccountLedgerPath["accountId"],
  query: AccountLedgerQuery = {},
  options: AccountLedgerRequestOptions = {},
): Promise<LedgerEntryPageResponse> {
  return apiGet<LedgerEntryPageResponse>(
    `/api/v1/accounts/${encodeURIComponent(accountId)}/ledger${buildQueryString(query)}`,
    options,
  )
}
