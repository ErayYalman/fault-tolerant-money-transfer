import { useState } from "react"
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  CalendarClock,
  RefreshCw,
  WalletCards,
} from "lucide-react"
import { Link, useLocation, useParams } from "react-router"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import { useAccountLedger } from "@/features/accounts/hooks/useAccountLedger"
import type { LedgerEntry } from "@/features/accounts/api/accounts.api"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

const PAGE_SIZE = 20

interface AccountNavigationState {
  accountNumber?: unknown
  currency?: unknown
}

function getAccountNavigationState(
  state: unknown,
): AccountNavigationState {
  if (typeof state !== "object" || state === null) {
    return {}
  }

  return state as AccountNavigationState
}

function getCurrency(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined
  }

  const currency = value.toUpperCase()

  return /^[A-Z]{3}$/.test(currency)
    ? currency
    : undefined
}

function formatMoney(
  value: number | undefined,
  currency: string | undefined,
): string {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—"
  }

  if (currency) {
    try {
      return new Intl.NumberFormat("tr-TR", {
        style: "currency",
        currency,
      }).format(value)
    } catch {
      // Geçersiz para biriminde sayısal gösterime geri dön.
    }
  }

  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value)
}

function formatEntryAmount(
  amount: number | undefined,
  entryType: string | undefined,
  currency: string | undefined,
): string {
  const formatted = formatMoney(amount, currency)

  if (entryType === "DEBIT") {
    return `− ${formatted}`
  }

  if (entryType === "CREDIT" || entryType === "REVERSAL") {
    return `+ ${formatted}`
  }

  return formatted
}

function formatDate(value: string | undefined): string {
  if (!value) {
    return "Tarih bilgisi bulunamadı"
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return "Geçersiz tarih"
  }

  return new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date)
}

function getEntryTypeLabel(type: string | undefined): string {
  switch (type) {
    case "DEBIT":
      return "Para çıkışı"

    case "CREDIT":
      return "Para girişi"

    case "REVERSAL":
      return "Debit telafisi"

    default:
      return type ?? "Bilinmeyen işlem"
  }
}

function getStatusLabel(status: string | undefined): string {
  if (status === "COMPLETED") {
    return "Tamamlandı"
  }

  return status ?? "Bilinmiyor"
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Hesap hareketleri zamanında alınamadı."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Hesap kimliği veya sayfalama parametreleri geçersiz."
    }

    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Bu hesap hareketlerini görüntüleme yetkiniz bulunmuyor."
    }

    if (error.status === 404) {
      return "Hesap bulunamadı veya bu hesaba erişim yetkiniz yok."
    }

    if (error.status >= 500) {
      return "Hesap hareketleri servisi şu anda yanıt veremiyor."
    }
  }

  return "Hesap hareketleri alınırken beklenmeyen bir hata oluştu."
}

function LedgerLoadingState() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }, (_, index) => (
        <Card key={index} className="shadow-none">
          <CardContent className="space-y-4 py-5">
            <div className="flex items-center gap-3">
              <Skeleton className="size-10 rounded-xl" />

              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-36" />
                <Skeleton className="h-3 w-48" />
              </div>

              <Skeleton className="h-6 w-24" />
            </div>

            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="hidden h-12 w-full sm:block" />
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function LedgerEntryCard({
  entry,
  currency,
}: {
  entry: LedgerEntry
  currency: string | undefined
}) {
  const isIncoming =
    entry.entryType === "CREDIT" ||
    entry.entryType === "REVERSAL"

  const Icon = isIncoming ? ArrowDownLeft : ArrowUpRight

  return (
    <Card className="shadow-none">
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex min-w-0 items-center gap-3">
            <div
              className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
                isIncoming
                  ? "bg-primary/10 text-primary"
                  : "bg-muted text-muted-foreground"
              }`}
            >
              <Icon className="size-5" />
            </div>

            <div className="min-w-0">
              <p className="text-sm font-semibold">
                {getEntryTypeLabel(entry.entryType)}
              </p>

              <p className="mt-1 text-xs text-muted-foreground">
                {formatDate(entry.createdAt)}
              </p>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
              {getStatusLabel(entry.status)}
            </span>

            <p
              className={`text-lg font-semibold tabular-nums ${
                isIncoming ? "text-primary" : "text-foreground"
              }`}
            >
              {formatEntryAmount(
                entry.amount,
                entry.entryType,
                currency,
              )}
            </p>
          </div>
        </div>

        <div className="grid gap-4 rounded-xl bg-muted/40 p-4 sm:grid-cols-3">
          <div>
            <p className="text-xs text-muted-foreground">
              Önceki bakiye
            </p>

            <p className="mt-1 text-sm font-semibold tabular-nums">
              {formatMoney(entry.balanceBefore, currency)}
            </p>
          </div>

          <div>
            <p className="text-xs text-muted-foreground">
              Sonraki bakiye
            </p>

            <p className="mt-1 text-sm font-semibold tabular-nums">
              {formatMoney(entry.balanceAfter, currency)}
            </p>
          </div>

          <div>
            <p className="text-xs text-muted-foreground">
              İşlem türü
            </p>

            <p className="mt-1 text-sm font-medium">
              {entry.entryType ?? "—"}
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-1 border-t border-border/70 pt-3 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <span>Transfer kimliği</span>

          <span className="break-all font-mono">
            {entry.transferId ?? "—"}
          </span>
        </div>

        {entry.correlationId && (
          <div className="flex flex-col gap-1 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <span>Korelasyon kimliği</span>

            <span className="break-all font-mono">
              {entry.correlationId}
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function AccountLedgerPage() {
  const { accountId } = useParams()
  const location = useLocation()

  const [page, setPage] = useState(0)

  const navigationState = getAccountNavigationState(location.state)

  const accountNumber =
    typeof navigationState.accountNumber === "string"
      ? navigationState.accountNumber
      : undefined

  const currency = getCurrency(navigationState.currency)

  const ledgerQuery = useAccountLedger(
    accountId,
    page,
    PAGE_SIZE,
  )

  const entries = ledgerQuery.data?.content ?? []
  const totalElements = ledgerQuery.data?.totalElements ?? 0
  const totalPages = ledgerQuery.data?.totalPages ?? 0

  if (!accountId) {
    return (
      <Card className="shadow-none">
        <CardContent className="flex min-h-64 flex-col items-center justify-center text-center">
          <WalletCards className="mb-4 size-8 text-muted-foreground" />

          <h1 className="font-semibold">
            Hesap kimliği bulunamadı
          </h1>

          <Button className="mt-5" variant="outline" render={<Link to="/accounts" />}>
            <ArrowLeft className="size-4" />
            Hesaplarıma Dön
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <Button
            variant="ghost"
            size="sm"
            className="-ml-3"
            render={<Link to="/accounts" />}
          >
            <ArrowLeft className="size-4" />
            Hesaplarıma Dön
          </Button>

          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            HESAP DETAYI
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Hesap hareketleri
          </h1>

          <p className="text-sm text-muted-foreground">
            {accountNumber ?? "Hesap"} · {currency ?? "Para birimi belirtilmedi"}
          </p>

          <p className="break-all font-mono text-xs text-muted-foreground">
            {accountId}
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void ledgerQuery.refetch()}
          disabled={ledgerQuery.isFetching}
        >
          <RefreshCw
            className={`size-4 ${ledgerQuery.isFetching ? "animate-spin" : ""}`}
          />
          Yenile
        </Button>
      </section>

      {ledgerQuery.isPending ? (
        <LedgerLoadingState />
      ) : ledgerQuery.isError ? (
        <Card className="border-destructive/30 shadow-none">
          <CardContent className="flex min-h-64 flex-col items-center justify-center px-6 py-10 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
              <WalletCards className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Hesap hareketleri yüklenemedi
            </h2>

            <p className="mt-2 max-w-lg text-sm leading-6 text-muted-foreground">
              {getErrorMessage(ledgerQuery.error)}
            </p>

            <Button
              className="mt-5"
              variant="outline"
              onClick={() => void ledgerQuery.refetch()}
              disabled={ledgerQuery.isFetching}
            >
              <RefreshCw className="size-4" />
              Tekrar Dene
            </Button>
          </CardContent>
        </Card>
      ) : entries.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <CalendarClock className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Henüz hesap hareketi bulunmuyor
            </h2>

            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              Bu hesaba ait görüntülenebilir bir ledger kaydı bulunmuyor.
              Yeni transfer veya telafi işlemleri gerçekleştikçe hareketler
              burada listelenecek.
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-2">
            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <WalletCards className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Toplam hareket
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {totalElements}
                  </p>
                </div>
              </CardContent>
            </Card>

            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-10 items-center justify-center rounded-xl bg-muted text-muted-foreground">
                  <CalendarClock className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Görüntülenen hareket
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {entries.length}
                  </p>
                </div>
              </CardContent>
            </Card>
          </section>

          <section aria-label="Hesap hareketleri" className="space-y-3">
            {entries.map((entry, index) => (
              <LedgerEntryCard
                key={`${entry.transferId ?? "entry"}-${entry.entryType ?? "unknown"}-${entry.createdAt ?? index}`}
                entry={entry}
                currency={currency}
              />
            ))}
          </section>

          {totalPages > 1 && (
            <div className="flex flex-col items-center justify-between gap-3 border-t border-border/70 pt-5 sm:flex-row">
              <p className="text-xs text-muted-foreground">
                Toplam {totalElements} hareket · Sayfa {page + 1} / {totalPages}
              </p>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || ledgerQuery.isFetching}
                  onClick={() => setPage((current) => current - 1)}
                >
                  <ArrowLeft className="size-4" />
                  Önceki
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    page + 1 >= totalPages || ledgerQuery.isFetching
                  }
                  onClick={() => setPage((current) => current + 1)}
                >
                  Sonraki
                  <ArrowUpRight className="size-4" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
