export const APP_ROLES = [
  "CUSTOMER",
  "OPERATIONS",
  "ADMIN",
] as const

export type AppRole = (typeof APP_ROLES)[number]

export interface AuthUser {
  id: string
  username?: string
  name?: string
  email?: string
}

export interface AuthSnapshot {
  isAuthenticated: boolean
  user: AuthUser | null
  roles: AppRole[]
  tokenRefreshError: boolean
}
