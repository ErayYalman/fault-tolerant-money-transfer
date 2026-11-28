import { useMutation, useQueryClient } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"

import {
  createInternalTransfer,
  type CreateInternalTransferRequest,
} from "@/features/accounts/api/accounts.api"

import { accountQueryKeys } from "@/features/accounts/hooks/useAccounts"

import {
  accountLedgerQueryKeys,
} from "@/features/accounts/hooks/useAccountLedger"

export function useCreateInternalTransfer() {
  const queryClient = useQueryClient()
  const { user } = useAuth()

  const subject = user?.id ?? "anonymous"

  return useMutation({
    mutationKey: [
      "accounts",
      "internal-transfer",
      subject,
    ],

    mutationFn: (request: CreateInternalTransferRequest) =>
      createInternalTransfer(request),

    retry: false,

    onSuccess: (_result, request) => {
      void queryClient.invalidateQueries({
        queryKey: accountQueryKeys.all,
      })

      if (request.fromAccountId) {
        void queryClient.invalidateQueries({
          queryKey: [
            ...accountLedgerQueryKeys.all,
            subject,
            request.fromAccountId,
          ],
        })
      }

      if (request.toAccountId) {
        void queryClient.invalidateQueries({
          queryKey: [
            ...accountLedgerQueryKeys.all,
            subject,
            request.toAccountId,
          ],
        })
      }
    },
  })
}
