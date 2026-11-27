import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react"
import { useQueryClient } from "@tanstack/react-query"

import type { AppRole, AuthSnapshot } from "@/lib/auth/auth.types"
import {
  getAuthSnapshot,
  getKeycloak,
  loginWithKeycloak,
  logoutFromKeycloak,
  refreshAccessToken,
  subscribeAuthState,
} from "@/lib/auth/keycloak"

interface AuthContextValue extends AuthSnapshot {
  hasRole: (role: AppRole) => boolean
  hasAnyRole: (roles: readonly AppRole[]) => boolean
  login: () => Promise<void>
  logout: () => Promise<void>
  refreshToken: (minValidity?: number) => Promise<string>
}

const AuthContext = createContext<AuthContextValue | null>(null)

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const snapshot = useSyncExternalStore(
    subscribeAuthState,
    getAuthSnapshot,
    getAuthSnapshot,
  )

  const queryClient = useQueryClient()

  const hasRole = useCallback(
    (role: AppRole) => snapshot.roles.includes(role),
    [snapshot.roles],
  )

  const hasAnyRole = useCallback(
    (roles: readonly AppRole[]) =>
      roles.some((role) => snapshot.roles.includes(role)),
    [snapshot.roles],
  )

  const login = useCallback(() => loginWithKeycloak(), [])

  const logout = useCallback(() => logoutFromKeycloak(), [])

  const refreshToken = useCallback(
    (minValidity?: number) => refreshAccessToken(minValidity),
    [],
  )

  useEffect(() => {
    if (!snapshot.isAuthenticated) {
      queryClient.clear()
    }
  }, [snapshot.isAuthenticated, queryClient])

  // Token'ı bitmeden yenilemeyi kontrol et.
  useEffect(() => {
    const intervalId = window.setInterval(() => {
      const client = getKeycloak()

      if (client.authenticated && client.isTokenExpired(30)) {
        void refreshAccessToken(30).catch(() => undefined)
      }
    }, 15_000)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      ...snapshot,
      hasRole,
      hasAnyRole,
      login,
      logout,
      refreshToken,
    }),
    [snapshot, hasRole, hasAnyRole, login, logout, refreshToken],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error("useAuth, AuthProvider içerisinde kullanılmalıdır.")
  }

  return context
}
