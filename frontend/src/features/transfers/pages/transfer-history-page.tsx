import { useState } from "react"
import {
  ArrowLeftRight,
  ArrowUpRight,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  WalletCards,
} from "lucide-react"
import { Link } from "react-router"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import {
  type TransferResponse,
  type TransferStatus,
} from "@/features/transfers/api/transfers.api"
import { useTransfers } from "@/features/transfers/hooks/useTransfers"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

const PAGE_SIZE = 10

const statusPresentations: Record<
  TransferStatus,
  { label: string; className: string }
> = {
  PENDING: {
    label: "Beklemede",
    className: "bg-muted text-muted-foreground",
  },
  DEBIT_REQUESTED: {
    label: "Borçlandırma bekleniyor",
    className: "bg-amber-100 text-amber-800",
  },
  DEBIT_COMPLETED: {
    label: "Borçlandırma tamamlandı",
    className: "bg-amber-100 text-amber-800",
  },
  DEBIT_FAILED: {
    label: "Borçlandırma başarısız",
    className: "bg-destructive/10 text-destructive",
  },
  CREDIT_REQUESTED: {
    label: "Alacaklandırma bekleniyor",
    className: "bg-amber-100 text-amber-800",
  },
  CREDIT_FAILED: {
    label: "Alacaklandırma başarısız",
    className: "bg-destructive/10 text-destructive",
  },
  COMPLETED: {
    label: "Tamamlandı",
    className: "bg-primary/10 text-primary",
  },
  COMPENSATING: {
    label: "Telafi devam ediyor",
    className: "bg-amber-100 text-amber-800",
  },
  COMPENSATED: {
    label: "Telafi edildi",
    className: "bg-muted text-muted-foreground",
  },
}

function formatMoney(
  amount: number | undefined,
  currency: string | undefined,
): string {
  if (typeof amount !== "number" || !Number.isFinite(amount)) {
    return "—"
  }

  const normalizedCurrency = currency?.toUpperCase() ?? "TRY"

  try {
    return new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency: normalizedCurrency,
    }).format(amount)
  } catch {
    return `${new Intl.NumberFormat("tr-TR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(amount)} ${normalizedCurrency}`
  }
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

function getShortId(value: string | undefined): string {
  return value ? `${value.slice(0, 8)}…` : "—"
}

function getStatusPresentation(status: TransferResponse["status"]) {
  if (!status) {
    return {
      label: "Durum bilinmiyor",
      className: "bg-muted text-muted-foreground",
    }
  }

  return statusPresentations[status]
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Transfer geçmişi isteği zamanında yanıt vermedi."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Sayfalama parametreleri geçersiz."
    }

    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Transfer geçmişini görüntüleme yetkiniz bulunmuyor."
    }

    if (error.status >= 500) {
      return "Transfer servisi şu anda yanıt veremiyor. Daha sonra tekrar deneyin."
    }
  }

  return "Transfer geçmişi alınırken beklenmeyen bir hata oluştu."
}

function TransferLoadingState() {
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

function TransferCard({
  transfer,
}: {
  transfer: TransferResponse
}) {
  const status = getStatusPresentation(transfer.status)
  const date = transfer.createdAt ?? transfer.startedAt ?? transfer.updatedAt

  return (
    <Card className="shadow-none">
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <ArrowUpRight className="size-5" />
            </div>

            <div className="min-w-0">
              <p className="text-sm font-semibold">
                Bank A → Bank B transferi
              </p>

              <p className="mt-1 text-xs text-muted-foreground">
                {formatDate(date)}
              </p>

              <p
                className="mt-1 break-all font-mono text-xs text-muted-foreground"
                title={transfer.transferId}
              >
                İşlem: {getShortId(transfer.transferId)}
              </p>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3 sm:flex-col sm:items-end">
            <span
              className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${status.className}`}
            >
              {status.label}
            </span>

            <p className="text-lg font-semibold tabular-nums">
              {formatMoney(transfer.amount, transfer.currency)}
            </p>
          </div>
        </div>

        <div className="grid gap-4 rounded-xl bg-muted/40 p-4 sm:grid-cols-3">
          <div>
            <p className="text-xs text-muted-foreground">
              Kaynak hesap referansı
            </p>

            <p
              className="mt-1 break-all font-mono text-sm font-medium"
              title={transfer.fromAccountId}
            >
              {getShortId(transfer.fromAccountId)}
            </p>
          </div>

          <div>
            <p className="text-xs text-muted-foreground">
              Alıcı hesap referansı
            </p>

            <p
              className="mt-1 break-all font-mono text-sm font-medium"
              title={transfer.toAccountId}
            >
              {getShortId(transfer.toAccountId)}
            </p>
          </div>

          <div>
            <p className="text-xs text-muted-foreground">
              Correlation ID
            </p>

            <p
              className="mt-1 break-all font-mono text-sm font-medium"
              title={transfer.correlationId}
            >
              {getShortId(transfer.correlationId)}
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-1 border-t border-border/70 pt-3 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <span>İşlem durumu</span>

          <span className="font-mono">
            {transfer.status ?? "UNKNOWN"}
          </span>
        </div>

        {transfer.failureReason && (
          <div className="rounded-lg bg-destructive/5 p-3 text-xs leading-5 text-destructive">
            {transfer.failureReason}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function TransferHistoryPage() {
  const [page, setPage] = useState(0)

  const transfersQuery = useTransfers(page, PAGE_SIZE)

  const transfers = transfersQuery.data?.content ?? []
  const totalElements = transfersQuery.data?.totalElements ?? 0
  const totalPages = transfersQuery.data?.totalPages ?? 0

  const inProgressCount = transfers.filter(
    (transfer) =>
      transfer.status !== "COMPLETED" &&
      transfer.status !== "DEBIT_FAILED" &&
      transfer.status !== "COMPENSATED",
  ).length

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            TRANSFER İŞLEMLERİ
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            İşlem geçmişi
          </h1>

          <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
            Bank A hesabınızdan Bank B hesaplarına gönderdiğiniz
            transferleri ve dağıtık işlem durumlarını görüntüleyin.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void transfersQuery.refetch()}
            disabled={transfersQuery.isFetching}
          >
            <RefreshCw
              className={`size-4 ${transfersQuery.isFetching ? "animate-spin" : ""}`}
            />
            Yenile
          </Button>

          <Button
            size="sm"
            render={<Link to="/transfers/new" />}
          >
            <ArrowLeftRight className="size-4" />
            Yeni Transfer
          </Button>
        </div>
      </section>

      {transfersQuery.isPending ? (
        <TransferLoadingState />
      ) : transfersQuery.isError && !transfersQuery.data ? (
        <Card className="border-destructive/30 shadow-none">
          <CardContent className="flex min-h-64 flex-col items-center justify-center px-6 py-10 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
              <WalletCards className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Transfer geçmişi yüklenemedi
            </h2>

            <p className="mt-2 max-w-lg text-sm leading-6 text-muted-foreground">
              {getErrorMessage(transfersQuery.error)}
            </p>

            <Button
              className="mt-5"
              variant="outline"
              onClick={() => void transfersQuery.refetch()}
              disabled={transfersQuery.isFetching}
            >
              <RefreshCw className="size-4" />
              Tekrar Dene
            </Button>
          </CardContent>
        </Card>
      ) : transfers.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <CalendarClock className="size-6" />
            </div>

            <h2 className="text-base font-semibold">
              Henüz transfer işleminiz bulunmuyor
            </h2>

            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              Oluşturduğunuz transferler ve Saga işlem adımları
              tamamlandıkça burada görüntülenecek.
            </p>

            <Button
              className="mt-5"
              render={<Link to="/transfers/new" />}
            >
              <ArrowLeftRight className="size-4" />
              İlk Transferinizi Oluşturun
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-3">
            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <ArrowLeftRight className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Toplam transfer
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
                  <WalletCards className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Bu sayfada görüntülenen
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {transfers.length}
                  </p>
                </div>
              </CardContent>
            </Card>

            <Card className="shadow-none">
              <CardContent className="flex items-center gap-4">
                <div className="flex size-10 items-center justify-center rounded-xl bg-amber-100 text-amber-800">
                  <RefreshCw className="size-5" />
                </div>

                <div>
                  <p className="text-sm text-muted-foreground">
                    Devam eden (bu sayfa)
                  </p>

                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {inProgressCount}
                  </p>
                </div>
              </CardContent>
            </Card>
          </section>

          {transfersQuery.isRefetchError && (
            <div
              role="status"
              className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-sm text-muted-foreground"
            >
              Liste güncellenemedi. Son başarılı sorgudan alınan
              kayıtlar gösteriliyor.
            </div>
          )}

          <section
            aria-label="Transfer geçmişi"
            className="space-y-3"
          >
            {transfers.map((transfer, index) => (
              <TransferCard
                key={
                  transfer.transferId ??
                  `${transfer.correlationId ?? "transfer"}-${index}`
                }
                transfer={transfer}
              />
            ))}
          </section>

          {totalPages > 1 && (
            <div className="flex flex-col items-center justify-between gap-3 border-t border-border/70 pt-5 sm:flex-row">
              <p className="text-xs text-muted-foreground">
                Toplam {totalElements} transfer · Sayfa {page + 1} / {totalPages}
              </p>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || transfersQuery.isFetching}
                  onClick={() => setPage((current) => current - 1)}
                >
                  <ChevronLeft className="size-4" />
                  Önceki
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    page + 1 >= totalPages ||
                    transfersQuery.isFetching
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
