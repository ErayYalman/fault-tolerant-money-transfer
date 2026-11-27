import { useQuery } from "@tanstack/react-query"

import { useAuth } from "@/app/providers/AuthProvider"
import { listAccounts } from "@/features/accounts/api/accounts.api"

export const accountQueryKeys = {
  all: ["accounts"] as const,

  list: (subject: string, page: number, size: number) =>
    ["accounts", "list", subject, page, size] as const,
}

export function useAccounts(page: number, size: number) {
  const { user, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: accountQueryKeys.list(
      user?.id ?? "anonymous",
      page,
      size,
    ),

    queryFn: ({ signal }) =>
      listAccounts(
        { page, size },
        { signal },
      ),

    enabled: isAuthenticated && Boolean(user?.id),
  })
}
