import { LogOut, UserRound } from "lucide-react"
import { Outlet } from "react-router"

import { useAuth } from "@/app/providers/AuthProvider"
import { AppSidebar } from "@/components/layout/app-sidebar"
import { Button } from "@/components/ui/button"
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar"

const roleLabels = {
  CUSTOMER: "Müşteri",
  OPERATIONS: "Operasyon",
  ADMIN: "Yönetici",
} as const

export function AppShell() {
  const { user, roles, logout, login, tokenRefreshError } = useAuth()

  const displayName =
    user?.name ?? user?.username ?? "Kullanıcı"

  const initials = displayName
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join("")
    .toUpperCase()

  const roleText =
    roles.map((role) => roleLabels[role]).join(" · ") ||
    "Tanımlı rol bulunamadı"

  function handleLogout() {
    void logout().catch((error) => {
      console.error("Logout failed:", error)
    })
  }

  function handleReauthentication() {
    void login().catch((error) => {
      console.error("Reauthentication failed:", error)
    })
  }

  return (
    <SidebarProvider defaultOpen>
      <AppSidebar />

      <SidebarInset className="min-h-svh bg-muted/40">
        <header className="sticky top-0 z-20 flex h-16 shrink-0 items-center border-b border-border/80 bg-background/90 px-4 backdrop-blur sm:px-6">
          <SidebarTrigger className="-ml-1" />

          <div className="mx-3 h-6 w-px shrink-0 bg-border" />

          <div className="min-w-0">
            <p className="truncate text-sm font-semibold tracking-tight">
              Money Transfer Platform
            </p>
            <p className="hidden text-xs text-muted-foreground sm:block">
              Dağıtık para transferi ve otomatik mutabakat
            </p>
          </div>

          <div className="ml-auto flex items-center gap-3">
            <div className="hidden items-center gap-2 lg:flex">
              <div className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                {initials || <UserRound className="size-4" />}
              </div>

              <div className="max-w-40">
                <p className="truncate text-xs font-medium">
                  {displayName}
                </p>
                <p className="truncate text-[11px] text-muted-foreground">
                  {roleText}
                </p>
              </div>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              aria-label="Oturumu kapat"
            >
              <LogOut className="size-4" />
              <span className="hidden sm:inline">Çıkış Yap</span>
            </Button>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 sm:py-8 lg:px-8">
          <div className="mx-auto w-full max-w-7xl space-y-4">
            {tokenRefreshError && (
              <div
                role="alert"
                className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div>
                  <p className="text-sm font-medium">
                    Oturum yenilenemedi
                  </p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    Kimlik doğrulama oturumunuzu yenilemek için tekrar giriş yapın.
                  </p>
                </div>

                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleReauthentication}
                >
                  Tekrar Giriş Yap
                </Button>
              </div>
            )}

            <Outlet />
          </div>
        </main>

        <footer className="border-t border-border/70 bg-background/70 px-4 py-4 text-center text-xs text-muted-foreground sm:px-6">
          Money Transfer Platform · Dağıtık para transferi ve otomatik mutabakat
        </footer>
      </SidebarInset>
    </SidebarProvider>
  )
}
