import { useQuery } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"
import {
  listTransfers,
  type TransferResponse,
} from "@/features/transfers/api/transfers.api"
import { transferQueryKeys } from "@/features/transfers/hooks/transfer-query-keys"

const TRANSFER_LIST_POLL_INTERVAL_MS = 5_000

function isTerminalTransferStatus(
  status: TransferResponse["status"],
): boolean {
  return (
    status === "COMPLETED" ||
    status === "DEBIT_FAILED" ||
    status === "COMPENSATED"
  )
}

export function useTransfers(page: number, size: number) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: transferQueryKeys.list(
      user?.id ?? "anonymous",
      page,
      size,
    ),

    queryFn: ({ signal }) =>
      listTransfers(
        { page, size },
        { signal },
      ),

    enabled: isAuthenticated && Boolean(user?.id),

    staleTime: 5_000,

    // Listede devam eden Saga varsa durumları periyodik yenile.
    refetchInterval: (query) => {
      const transfers = query.state.data?.content ?? []

      const hasInProgressTransfer = transfers.some(
        (transfer) => !isTerminalTransferStatus(transfer.status),
      )

      return hasInProgressTransfer
        ? TRANSFER_LIST_POLL_INTERVAL_MS
        : false
    },

    refetchIntervalInBackground: false,
  })
}
