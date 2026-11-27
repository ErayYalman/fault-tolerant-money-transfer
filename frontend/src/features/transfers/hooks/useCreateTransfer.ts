import { useMutation, useQueryClient } from "@tanstack/react-query"

import { accountQueryKeys } from "@/features/accounts/hooks/useAccounts"
import {
  createTransfer,
  type CreateTransferRequest,
} from "@/features/transfers/api/transfers.api"

export function useCreateTransfer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ["transfers", "create"],

    mutationFn: (request: CreateTransferRequest) =>
      createTransfer(request),

    // Global QueryClient ayarları değişse bile finansal POST isteğini otomatik tekrarlama.
    retry: false,

    onSuccess: async () => {
      // Saga Kafka üzerinden asenkron ilerlediği için hemen GET atma;
      // önbelleği bayat işaretle, ilgili ekran açıldığında güncel bakiye çekilsin.
      await queryClient.invalidateQueries({
        queryKey: accountQueryKeys.all,
        refetchType: "none",
      })
    },
  })
}