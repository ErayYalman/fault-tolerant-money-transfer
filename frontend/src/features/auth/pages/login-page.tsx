import { useState } from "react"
import { ArrowRight, Landmark, ShieldCheck } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { useAuth } from "@/app/providers/AuthProvider"

export function LoginPage() {
  const { login } = useAuth()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleLogin() {
    setIsLoading(true)
    setError(null)

    try {
      await login()
    } catch {
      setError("Keycloak giriş sayfasına yönlendirme başarısız oldu.")
      setIsLoading(false)
    }
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-md shadow-sm">
        <CardHeader className="items-center text-center">
          <div className="mb-3 flex size-14 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
            <Landmark className="size-7" />
          </div>

          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            MONEY TRANSFER PLATFORM
          </p>

          <CardTitle className="mt-2 text-2xl">
            Hoş geldiniz
          </CardTitle>

          <CardDescription className="max-w-xs leading-6">
            Dağıtık para transferi ve hesap işlemlerinize güvenli şekilde
            erişmek için oturum açın.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <Button
            className="h-10 w-full"
            onClick={() => void handleLogin()}
            disabled={isLoading}
          >
            Güvenli Giriş Yap
            <ArrowRight className="size-4" />
          </Button>

          {error && (
            <p role="alert" className="text-center text-sm text-destructive">
              {error}
            </p>
          )}

          <div className="flex items-center justify-center gap-2 border-t border-border pt-4 text-xs text-muted-foreground">
            <ShieldCheck className="size-4 text-primary" />
            Keycloak ile güvenli kimlik doğrulama
          </div>
        </CardContent>
      </Card>
    </main>
  )
}
