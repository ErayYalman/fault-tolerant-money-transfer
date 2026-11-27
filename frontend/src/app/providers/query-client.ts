import { QueryClient } from "@tanstack/react-query"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60 * 1_000,
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,

      retry: (failureCount, error) => {
        if (error instanceof ApiError) {
          return error.status >= 500 && failureCount < 1
        }

        if (
          error instanceof ApiNetworkError ||
          error instanceof ApiTimeoutError
        ) {
          return failureCount < 1
        }

        if (error instanceof ApiAuthenticationError) {
          return false
        }

        return false
      },
    },
  },
})
