import { ArrowLeftRight } from "lucide-react"
import { Outlet } from "react-router"

import { AppSidebar } from "@/components/layout/app-sidebar"
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar"

export function AppShell() {
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

          <div className="ml-auto hidden items-center gap-2 rounded-full border border-border bg-background px-3 py-1.5 text-xs text-muted-foreground md:flex">
            <ArrowLeftRight className="size-3.5 text-primary" />
            Saga tabanlı işlem altyapısı
          </div>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 sm:py-8 lg:px-8">
          <div className="mx-auto w-full max-w-7xl">
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