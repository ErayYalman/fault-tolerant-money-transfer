import { Route, Routes } from "react-router"

import { AccessDeniedPage } from "@/app/AccessDeniedPage"
import { useAuth } from "@/app/providers/AuthProvider"
import { RequireRole } from "@/app/router/RequireRole"
import { AppShell } from "@/components/layout/app-shell"
import { LoginPage } from "@/features/auth/pages/login-page"
import { AccountsPage } from "@/features/accounts/pages/accounts-page"
import { AccountLedgerPage } from "@/features/accounts/pages/account-ledger-page"
import { OverviewPage } from "@/features/dashboard/pages/overview-page"
import { FeaturePlaceholderPage } from "@/app/FeaturePlaceholderPage"
import { NewTransferPage } from "@/features/transfers/pages/new-transfer-page"
import { TransferHistoryPage } from "@/features/transfers/pages/transfer-history-page"
import { ReconciliationCenterPage } from "@/features/reconciliation/pages/reconciliation-center-page"
import { AnomalyMonitoringPage } from "@/features/reconciliation/pages/anomaly-monitoring-page"

export function AppRouter() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <LoginPage />
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<OverviewPage />} />

        <Route
          path="forbidden"
          element={<AccessDeniedPage />}
        />

        {/* CUSTOMER */}
        <Route element={<RequireRole allowedRoles={["CUSTOMER"]} />}>
          <Route
            path="accounts"
            element={<AccountsPage />}
          />

          <Route
            path="accounts/:accountId/ledger"
            element={<AccountLedgerPage />}
          />

          <Route
            path="transfers/new"
            element={<NewTransferPage />}
          />

          <Route
            path="transfers"
            element={<TransferHistoryPage />}
          />
        </Route>

        {/* OPERATIONS ve ADMIN */}
        <Route element={<RequireRole allowedRoles={["OPERATIONS", "ADMIN"]} />}>
          <Route
            path="operations/reconciliation"
            element={<ReconciliationCenterPage />}
          />

          <Route
            path="operations/anomalies"
            element={<AnomalyMonitoringPage />}
          />
        </Route>

        <Route
          path="*"
          element={
            <FeaturePlaceholderPage
              title="Sayfa Bulunamadı"
              description="Aradığınız sayfa mevcut değil."
            />
          }
        />
      </Route>
    </Routes>
  )
}
