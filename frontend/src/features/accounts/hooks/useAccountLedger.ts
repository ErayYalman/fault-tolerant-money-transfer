import { useQuery } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"
import { getAccountLedger } from "@/features/accounts/api/accounts.api"

export const accountLedgerQueryKeys = {
  all: ["account-ledger"] as const,

  detail: (
    subject: string,
    accountId: string,
    page: number,
    size: number,
  ) =>
    ["account-ledger", subject, accountId, page, size] as const,
}

export function useAccountLedger(
  accountId: string | undefined,
  page: number,
  size: number,
) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: accountLedgerQueryKeys.detail(
      user?.id ?? "anonymous",
      accountId ?? "missing",
      page,
      size,
    ),

    queryFn: ({ signal }) => {
      if (!accountId) {
        throw new Error("Hesap kimliği bulunamadı.")
      }

      return getAccountLedger(
        accountId,
        { page, size },
        { signal },
      )
    },

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      Boolean(accountId),
  })
}
