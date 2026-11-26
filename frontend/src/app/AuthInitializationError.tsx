import { useState } from "react"
import { AlertCircle, RefreshCw } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

export function AuthInitializationError() {
  const [isReloading, setIsReloading] = useState(false)

  function handleRetry() {
    setIsReloading(true)
    window.location.reload()
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-md shadow-none">
        <CardHeader className="items-center text-center">
          <div className="mb-2 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
            <AlertCircle className="size-6" />
          </div>

          <CardTitle>Kimlik doğrulama başlatılamadı</CardTitle>

          <CardDescription className="leading-6">
            Keycloak servisine ulaşılamadı veya bağlantı yapılandırmasında
            bir sorun oluştu.
          </CardDescription>
        </CardHeader>

        <CardContent className="flex flex-col items-center gap-4">
          <p className="text-center text-xs leading-5 text-muted-foreground">
            Keycloak servisinin çalıştığını ve frontend ortam değişkenlerinin
            doğru tanımlandığını kontrol edin.
          </p>

          <Button
            onClick={handleRetry}
            disabled={isReloading}
            className="w-full"
          >
            <RefreshCw className="size-4" />
            Tekrar Dene
          </Button>
        </CardContent>
      </Card>
    </main>
  )
}