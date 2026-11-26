import { Navigate, Outlet } from "react-router"

import { useAuth } from "@/app/providers/AuthProvider"
import type { AppRole } from "@/lib/auth/auth.types"

interface RequireRoleProps {
  allowedRoles: readonly AppRole[]
}

export function RequireRole({ allowedRoles }: RequireRoleProps) {
  const { hasAnyRole } = useAuth()

  if (!hasAnyRole(allowedRoles)) {
    return <Navigate to="/forbidden" replace />
  }

  return <Outlet />
}
