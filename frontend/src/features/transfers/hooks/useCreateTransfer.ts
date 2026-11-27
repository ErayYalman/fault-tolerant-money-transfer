import { useMutation, useQueryClient } from "@tanstack/react-query"

import { accountQueryKeys } from "@/features/accounts/hooks/useAccounts"
import {
  createTransfer,
  type CreateTransferRequest,
} from "@/features/transfers/api/transfers.api"
import { transferQueryKeys } from "@/features/transfers/hooks/transfer-query-keys"

export function useCreateTransfer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ["transfers", "create"],

    mutationFn: (request: CreateTransferRequest) =>
      createTransfer(request),

    // Global QueryClient ayarları değişse bile finansal POST isteğini otomatik tekrarlama.
    retry: false,

    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: accountQueryKeys.all,
          refetchType: "none",
        }),

        queryClient.invalidateQueries({
          queryKey: transferQueryKeys.lists(),
          refetchType: "none",
        }),
      ])
    },
  })
}