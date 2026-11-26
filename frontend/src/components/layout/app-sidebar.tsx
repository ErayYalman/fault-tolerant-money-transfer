import {
  Activity,
  ArrowLeftRight,
  ClipboardList,
  LayoutDashboard,
  Landmark,
  ShieldCheck,
  WalletCards,
} from "lucide-react"
import { NavLink, useLocation } from "react-router"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar"

const navigationGroups = [
  {
    label: "ÇALIŞMA ALANI",
    items: [
      {
        title: "Genel Bakış",
        url: "/",
        icon: LayoutDashboard,
      },
      {
        title: "Hesaplarım",
        url: "/accounts",
        icon: WalletCards,
      },
      {
        title: "Para Transferi",
        url: "/transfers/new",
        icon: ArrowLeftRight,
      },
      {
        title: "İşlem Geçmişi",
        url: "/transfers",
        icon: ClipboardList,
      },
    ],
  },
  {
    label: "OPERASYON",
    items: [
      {
        title: "Mutabakat Merkezi",
        url: "/operations/reconciliation",
        icon: Activity,
      },
      {
        title: "Anomali İzleme",
        url: "/operations/anomalies",
        icon: ShieldCheck,
      },
    ],
  },
]

function isItemActive(pathname: string, href: string) {
  if (href === "/") {
    return pathname === "/"
  }

  return pathname === href || pathname.startsWith(`${href}/`)
}

export function AppSidebar() {
  const { pathname } = useLocation()

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader className="border-b border-sidebar-border px-3 py-3">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-sidebar-primary text-sidebar-primary-foreground shadow-sm">
            <Landmark className="size-5" />
          </div>

          <div className="min-w-0 group-data-[collapsible=icon]:hidden">
            <p className="truncate text-sm font-semibold tracking-wide text-sidebar-foreground">
              MONEY TRANSFER
            </p>
            <p className="mt-0.5 truncate text-[11px] text-sidebar-foreground/60">
              Finansal İşlem Platformu
            </p>
          </div>
        </div>
      </SidebarHeader>

      <SidebarContent className="gap-2 px-2 py-4">
        {navigationGroups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel className="px-2 text-[10px] font-semibold tracking-[0.14em] text-sidebar-foreground/45">
              {group.label}
            </SidebarGroupLabel>

            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => {
                  const active = isItemActive(pathname, item.url)
                  const Icon = item.icon

                  return (
                    <SidebarMenuItem key={item.url}>
                      <SidebarMenuButton
                        render={
                          <NavLink
                            to={item.url}
                            end={item.url === "/"}
                          />
                        }
                        isActive={active}
                        tooltip={item.title}
                        className="h-10 px-3 text-sidebar-foreground/75 hover:bg-sidebar-accent hover:text-sidebar-foreground data-[active=true]:bg-sidebar-accent data-[active=true]:font-medium data-[active=true]:text-sidebar-primary"
                      >
                        <Icon className="size-[18px]" />
                        <span>{item.title}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  )
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter className="p-3">
        <div className="rounded-xl border border-sidebar-border bg-sidebar-accent/60 p-3 group-data-[collapsible=icon]:hidden">
          <div className="flex items-center gap-2">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-sidebar-primary/15 text-sidebar-primary">
              <ShieldCheck className="size-4" />
            </div>

            <div className="min-w-0">
              <p className="text-xs font-medium text-sidebar-foreground">
                Hata Toleranslı Altyapı
              </p>
              <p className="mt-0.5 text-[11px] text-sidebar-foreground/55">
                Dağıtık işlem mimarisi
              </p>
            </div>
          </div>
        </div>
      </SidebarFooter>

      <SidebarRail />
    </Sidebar>
  )
}