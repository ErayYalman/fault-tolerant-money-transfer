import { useQuery } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"
import {
  verifyRecipient,
} from "@/features/transfers/api/transfers.api"

export const recipientLookupQueryKeys = {
  all: ["recipient-lookup"] as const,

  detail: (subject: string, accountNumber: string) =>
    ["recipient-lookup", subject, accountNumber] as const,
}

export function useRecipientLookup(
  accountNumber: string | undefined,
  enabled: boolean,
) {
  const { user, isAuthenticated } = useAuth()

  const normalizedAccountNumber = accountNumber?.trim() ?? ""

  const isValidAccountNumber =
    normalizedAccountNumber.length > 0 &&
    normalizedAccountNumber.length <= 50

  return useQuery({
    queryKey: recipientLookupQueryKeys.detail(
      user?.id ?? "anonymous",
      normalizedAccountNumber || "missing",
    ),

    queryFn: ({ signal }) =>
      verifyRecipient(normalizedAccountNumber, { signal }),

    enabled:
      isAuthenticated &&
      Boolean(user?.id) &&
      enabled &&
      isValidAccountNumber,

    staleTime: 30_000,
  })
}
