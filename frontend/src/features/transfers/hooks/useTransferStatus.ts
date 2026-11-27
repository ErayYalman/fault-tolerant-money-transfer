import { useEffect } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"
import { accountQueryKeys } from "@/features/accounts/hooks/useAccounts"
import {
  getTransfer,
  type TransferResponse,
} from "@/features/transfers/api/transfers.api"

const TRANSFER_POLL_INTERVAL_MS = 2_000

function isTerminalTransferStatus(
  status: TransferResponse["status"],
): boolean {
  return (
    status === "COMPLETED" ||
    status === "DEBIT_FAILED" ||
    status === "COMPENSATED"
  )
}

export const transferQueryKeys = {
  all: ["transfers"] as const,

  detail: (subject: string, transferId: string) =>
    ["transfers", "detail", subject, transferId] as const,
}

export function useTransferStatus(
  transferId: string | undefined,
) {
  const { user, isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: transferQueryKeys.detail(
      user?.id ?? "anonymous",
      transferId ?? "missing",
    ),

    queryFn: ({ signal }) => {
      if (!transferId) {
        throw new Error("Transfer ID bulunamadı.")
      }

      return getTransfer(transferId, { signal })
    },

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      Boolean(transferId),

    staleTime: 0,

    refetchInterval: (currentQuery) =>
      isTerminalTransferStatus(
        currentQuery.state.data?.status,
      )
        ? false
        : TRANSFER_POLL_INTERVAL_MS,

    // Sekme arka plandayken gereksiz istek gönderme.
    // Kullanıcı geri döndüğünde stale veri yeniden sorgulanır.
    refetchIntervalInBackground: false,
  })

  useEffect(() => {
    if (isTerminalTransferStatus(query.data?.status)) {
      // Debit veya compensation tamamlandığında bakiye listesini yenile.
      void queryClient.invalidateQueries({
        queryKey: accountQueryKeys.all,
      })
    }
  }, [query.data?.status, queryClient])

  return query
}
