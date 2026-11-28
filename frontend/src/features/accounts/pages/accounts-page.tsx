import { useState } from "react"
import {
  ArrowLeftRight,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Landmark,
  RefreshCw,
  WalletCards,
} from "lucide-react"
import { Link } from "react-router"

import { ApiAuthenticationError, ApiError, ApiNetworkError, ApiTimeoutError } from "@/lib/api/api-error"
import { useAccounts } from "@/features/accounts/hooks/useAccounts"
import type { AccountSummary } from "@/features/accounts/api/accounts.api"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

const PAGE_SIZE = 12

const accountStatusLabels: Record<
  string,
  { label: string; className: string }
> = {
  ACTIVE: {
    label: "Aktif",
    className: "bg-primary/10 text-primary",
  },
  FROZEN: {
    label: "Dondurulmuş",
    className: "bg-amber-100 text-amber-800",
  },
  CLOSED: {
    label: "Kapalı",
    className: "bg-muted text-muted-foreground",
  },
}

function formatBalance(
  balance: number | undefined,
  currency: string | undefined,
): string {
  if (typeof balance !== "number" || !Number.isFinite(balance)) {
    return "—"
  }

  const normalizedCurrency = currency?.toUpperCase()

  try {
    if (!normalizedCurrency) {
      throw new Error("Currency is missing.")
    }

    return new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency: normalizedCurrency,
    }).format(balance)
  } catch {
    const formattedNumber = new Intl.NumberFormat("tr-TR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(balance)

    return [formattedNumber, normalizedCurrency].filter(Boolean).join(" ")
  }
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Hesap bilgileri zamanında alınamadı. Tekrar deneyebilirsiniz."
  }

  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Bu hesap bilgilerini görüntüleme yetkiniz bulunmuyor."
    }

    if (error.status >= 500) {
      return "Hesap servisi şu anda yanıt veremiyor. Daha sonra tekrar deneyin."
    }
  }

  return "Hesap bilgileri alınırken beklenmeyen bir hata oluştu."
}

function AccountStatus({ status }: { status: string | undefined }) {
  const presentation = status
    ? accountStatusLabels[status]
    : undefined

  const label = presentation?.label ?? status ?? "Bilinmiyor"
  const className =
    presentation?.className ?? "bg-muted text-muted-foreground"

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  )
}

function AccountCard({ account }: { account: AccountSummary }) {
  return (
    <Card className="shadow-none transition-shadow hover:shadow-sm">
      <CardHeader className="flex flex-row items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Landmark className="size-5" />
          </div>

          <div className="min-w-0">
            <CardTitle className="text-sm">
              Bank A hesabı
            </CardTitle>

            <CardDescription className="mt-1 truncate font-mono text-xs">
              {account.accountNumber ?? "Hesap numarası bulunamadı"}
            </CardDescription>
          </div>
        </div>

        <AccountStatus status={account.status} />
      </CardHeader>

      <CardContent className="space-y-3">
        <div className="border-t border-border/70 pt-4">
          <p className="text-xs font-medium text-muted-foreground">
            Kullanılabilir bakiye
          </p>

          <p className="mt-2 text-2xl font-semibold tracking-tight tabular-nums">
            {formatBalance(account.balance, account.currency)}
          </p>
        </div>

        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>Hesap kimliği</span>

          <span className="font-mono">
            {account.accountId
              ? `${account.accountId.slice(0, 8)}…`
              : "—"}
          </span>
        </div>
      </CardContent>

      <CardFooter className="justify-end border-t border-border/70 bg-transparent">
        {account.accountId ? (
          <Button
            size="sm"
            variant="ghost"
            render={
              <Link
                to={`/accounts/${encodeURIComponent(account.accountId)}/ledger`}
                state={{
                  accountNumber: account.accountNumber,
                  currency: account.currency,
                }}
              />
            }
          >
            Hesap hareketleri
            <ArrowUpRight className="size-4" />
          </Button>
        ) : (
          <Button size="sm" variant="ghost" disabled>
            Hesap kimliği bulunamadı
          </Button>
        )}
      </CardFooter>
    </Card>
  )
}

function AccountLoadingState() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 6 }, (_, index) => (
        <Card key={index} className="shadow-none">
          <CardContent className="space-y-5 pt-5">
            <div className="flex items-center gap-3">
              <Skeleton className="size-10 rounded-xl" />

              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-3 w-24" />
              </div>
            </div>

            <div className="space-y-2 pt-3">
              <Skeleton className="h-3 w-28" />
              <Skeleton className="h-8 w-40" />
            </div>

            <Skeleton className="h-3 w-full" />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

export function AccountsPage() {
  const [page, setPage] = useState(0)

  const accountsQuery = useAccounts(page, PAGE_SIZE)

  const accounts = accountsQuery.data?.content ?? []
  const totalElements = accountsQuery.data?.totalElements ?? 0
  const totalPages = accountsQuery.data?.totalPages ?? 0

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            HESAPLAR
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Hesaplarım
          </h1>

          <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
            Bank A hesaplarınızı, güncel bakiyelerinizi ve hesap durumlarınızı
            görüntüleyin.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            render={<Link to="/accounts/internal-transfer" />}
          >
            <ArrowLeftRight className="size-4" />
            Hesaplar Arası Transfer
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => void accountsQuery.refetch()}
            disabled={accountsQuery.isFetching}
          >
            <RefreshCw
              className={`size-4 ${
                accountsQuery.isFetching ? "animate-spin" : ""
              }`}
            />
            Yenile
          </Button>
        </div>
      </section>

      {accountsQuery.isPending ? (
        <AccountLoadingState />
      ) : accountsQuery.isError ? (
        <Card className="border-destructive/30 shadow-none">
          <CardContent className="flex min-h-64 flex-col items-center justify-center px-6 py-10 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
              <WalletCards className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Hesap bilgileri yüklenemedi
            </h2>

            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              {getErrorMessage(accountsQuery.error)}
            </p>

            <Button
              className="mt-5"
              variant="outline"
              onClick={() => void accountsQuery.refetch()}
              disabled={accountsQuery.isFetching}
            >
              <RefreshCw className="size-4" />
              Tekrar Dene
            </Button>
          </CardContent>
        </Card>
      ) : accounts.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <WalletCards className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Henüz hesabınız bulunmuyor
            </h2>

            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              Bu kullanıcıya ait görüntülenebilir bir Bank A hesabı
              bulunamadı. Hesaplar yalnızca oturum açan kullanıcıya ait
              olduklarında listelenir.
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-2">
            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <WalletCards className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Toplam hesap
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {totalElements}
                  </p>
                </div>
              </CardContent>
            </Card>

            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Landmark className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Bu sayfada görüntülenen
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {accounts.length}
                  </p>
                </div>
              </CardContent>
            </Card>
          </section>

          <section
            aria-label="Banka hesapları"
            className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
          >
            {accounts.map((account, index) => (
              <AccountCard
                key={
                  account.accountId ??
                  `${account.accountNumber ?? "account"}-${index}`
                }
                account={account}
              />
            ))}
          </section>

          {totalPages > 1 && (
            <div className="flex flex-col items-center justify-between gap-3 border-t border-border/70 pt-5 sm:flex-row">
              <p className="text-xs text-muted-foreground">
                Toplam {totalElements} hesap · Sayfa {page + 1} / {totalPages}
              </p>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || accountsQuery.isFetching}
                  onClick={() => setPage((current) => current - 1)}
                >
                  <ChevronLeft className="size-4" />
                  Önceki
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    page + 1 >= totalPages || accountsQuery.isFetching
                  }
                  onClick={() => setPage((current) => current + 1)}
                >
                  Sonraki
                  <ChevronRight className="size-4" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
