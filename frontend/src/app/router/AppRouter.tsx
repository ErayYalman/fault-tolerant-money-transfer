import { Route, Routes } from "react-router"

import { FeaturePlaceholderPage } from "@/app/FeaturePlaceholderPage"
import { AppShell } from "@/components/layout/app-shell"
import { OverviewPage } from "@/features/dashboard/pages/overview-page"

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<OverviewPage />} />

        <Route
          path="accounts"
          element={
            <FeaturePlaceholderPage
              title="Hesaplarım"
              description="Hesap bakiyeleri, hesap bilgileri ve hesap hareketlerinizi görüntüleyin."
            />
          }
        />

        <Route
          path="transfers/new"
          element={
            <FeaturePlaceholderPage
              title="Para Transferi"
              description="Alıcı doğrulama, para transferi oluşturma ve transfer durumunu takip etme."
            />
          }
        />

        <Route
          path="transfers"
          element={
            <FeaturePlaceholderPage
              title="İşlem Geçmişi"
              description="Gerçekleşen transferlerinizi ve dağıtık işlem adımlarını inceleyin."
            />
          }
        />

        <Route
          path="operations/reconciliation"
          element={
            <FeaturePlaceholderPage
              title="Mutabakat Merkezi"
              description="T+1 mutabakat koşularını, mutabakat sonuçlarını ve fark kayıtlarını takip edin."
            />
          }
        />

        <Route
          path="operations/anomalies"
          element={
            <FeaturePlaceholderPage
              title="Anomali İzleme"
              description="Kural tabanlı farklılıkları ve makine öğrenmesi anomali skorlarını inceleyin."
            />
          }
        />

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