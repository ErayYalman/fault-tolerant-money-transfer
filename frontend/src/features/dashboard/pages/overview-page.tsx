import {
  Activity,
  AlertCircle,
  ArrowLeftRight,
  ArrowUpRight,
  CalendarClock,
  CheckCircle2,
  Clock3,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  WalletCards,
  type LucideIcon,
} from "lucide-react"
import { Link } from "react-router"

import { useAuth } from "@/app/providers/AuthProvider"

import {
  type AccountSummary,
} from "@/features/accounts/api/accounts.api"
import { useAccounts } from "@/features/accounts/hooks/useAccounts"

import {
  type TransferResponse,
  type TransferStatus,
} from "@/features/transfers/api/transfers.api"
import { useTransfers } from "@/features/transfers/hooks/useTransfers"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

const ACCOUNT_PAGE_SIZE = 100
const TRANSFER_PAGE_SIZE = 20
const RECENT_TRANSFER_COUNT = 5

const STATUS_PRESENTATIONS: Record<
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

function formatCount(value: number): string {
  return new Intl.NumberFormat("tr-TR").format(value)
}

function formatMoney(
  amount: number | undefined,
  currency: string | undefined,
): string {
  if (typeof amount !== "number" || !Number.isFinite(amount)) {
    return "—"
  }

  const normalizedCurrency = currency?.toUpperCase()

  if (normalizedCurrency) {
    try {
      return new Intl.NumberFormat("tr-TR", {
        style: "currency",
        currency: normalizedCurrency,
      }).format(amount)
    } catch {
      // Geçersiz para biriminde sayısal gösterime geri dön.
    }
  }

  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(amount)
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
    timeZone: "Europe/Istanbul",
  }).format(date)
}

function getShortId(value: string | undefined): string {
  return value ? `${value.slice(0, 8)}…` : "—"
}

function isTerminalStatus(
  status: TransferResponse["status"],
): boolean {
  return (
    status === "COMPLETED" ||
    status === "DEBIT_FAILED" ||
    status === "COMPENSATED"
  )
}

function getStatusPresentation(
  status: TransferResponse["status"],
) {
  if (!status) {
    return {
      label: "Durum bilinmiyor",
      className: "bg-muted text-muted-foreground",
    }
  }

  return STATUS_PRESENTATIONS[status]
}

function getApiErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "İstek zamanında yanıt vermedi. Daha sonra tekrar deneyin."
  }

  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Bu verileri görüntüleme yetkiniz bulunmuyor."
    }

    if (error.status >= 500) {
      return "İlgili servis şu anda yanıt veremiyor."
    }
  }

  return "Veriler alınırken beklenmeyen bir hata oluştu."
}

function getGreetingName(
  name: string | undefined,
  username: string | undefined,
): string {
  const candidate = name?.trim() || username?.trim()

  if (!candidate) {
    return "Hoş geldiniz"
  }

  return `Merhaba, ${candidate}`
}

function AccountBalanceCard({
  accounts,
  totalElements,
}: {
  accounts: AccountSummary[]
  totalElements: number
}) {
  const balancesByCurrency = accounts.reduce<Map<string, number>>(
    (balances, account) => {
      if (
        typeof account.balance !== "number" ||
        !Number.isFinite(account.balance)
      ) {
        return balances
      }

      const currency = account.currency?.toUpperCase() ?? "UNKNOWN"

      balances.set(
        currency,
        (balances.get(currency) ?? 0) + account.balance,
      )

      return balances
    },
    new Map(),
  )

  const balances = [...balancesByCurrency.entries()].sort(
    ([left], [right]) => left.localeCompare(right),
  )

  return (
    <Card className="shadow-none">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <WalletCards className="size-5 text-primary" />
          Hesap bakiyeleri
        </CardTitle>

        <CardDescription>
          Bank A hesaplarınızın para birimine göre toplam bakiyesi.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {balances.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Görüntülenebilir bakiye bulunmuyor.
          </p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {balances.map(([currency, balance]) => (
              <div
                key={currency}
                className="rounded-xl bg-muted/40 p-4"
              >
                <p className="text-xs font-medium text-muted-foreground">
                  {currency === "UNKNOWN"
                    ? "Para birimi belirtilmedi"
                    : currency}
                </p>

                <p className="mt-2 text-xl font-semibold tabular-nums">
                  {formatMoney(
                    balance,
                    currency === "UNKNOWN" ? undefined : currency,
                  )}
                </p>
              </div>
            ))}
          </div>
        )}

        {totalElements > accounts.length && (
          <p className="text-xs leading-5 text-muted-foreground">
            Bakiye toplamları ilk {accounts.length} hesap üzerinden
            hesaplandı. Tüm {formatCount(totalElements)} hesabı
            görüntülemek için hesaplar sayfasına geçin.
          </p>
        )}

        <Button
          variant="outline"
          size="sm"
          className="w-full"
          render={<Link to="/accounts" />}
        >
          Tüm Hesaplarım
          <ArrowUpRight className="size-4" />
        </Button>
      </CardContent>
    </Card>
  )
}

function SummaryCard({
  title,
  value,
  description,
  icon: Icon,
}: {
  title: string
  value: string
  description: string
  icon: LucideIcon
}) {
  return (
    <Card className="shadow-none">
      <CardContent className="flex items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <p className="text-sm font-medium text-muted-foreground">
            {title}
          </p>

          <p className="text-3xl font-semibold tracking-tight tabular-nums">
            {value}
          </p>

          <p className="text-xs leading-5 text-muted-foreground">
            {description}
          </p>
        </div>

        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Icon className="size-5" />
        </div>
      </CardContent>
    </Card>
  )
}

function RecentTransferCard({
  transfer,
}: {
  transfer: TransferResponse
}) {
  const status = getStatusPresentation(transfer.status)
  const date =
    transfer.createdAt ??
    transfer.startedAt ??
    transfer.updatedAt

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <ArrowUpRight className="size-4" />
        </div>

        <div className="min-w-0">
          <p className="text-sm font-medium">
            Bank A → Bank B transferi
          </p>

          <p className="mt-1 text-xs text-muted-foreground">
            {formatDate(date)}
          </p>

          <p
            className="mt-1 break-all font-mono text-xs text-muted-foreground"
            title={transfer.transferId}
          >
            {getShortId(transfer.transferId)}
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 sm:flex-col sm:items-end">
        <span
          className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${status.className}`}
        >
          {status.label}
        </span>

        <p className="text-sm font-semibold tabular-nums">
          {formatMoney(transfer.amount, transfer.currency)}
        </p>
      </div>
    </div>
  )
}

function CustomerOverviewPage() {
  const { user } = useAuth()

  const accountsQuery = useAccounts(0, ACCOUNT_PAGE_SIZE)
  const transfersQuery = useTransfers(0, TRANSFER_PAGE_SIZE)

  const accounts = accountsQuery.data?.content ?? []
  const transfers = transfersQuery.data?.content ?? []

  const totalAccounts =
    accountsQuery.data?.totalElements ?? 0

  const totalTransfers =
    transfersQuery.data?.totalElements ?? 0

  const inProgressCount = transfers.filter(
    (transfer) => !isTerminalStatus(transfer.status),
  ).length

  const recentTransfers = transfers.slice(
    0,
    RECENT_TRANSFER_COUNT,
  )

  const isInitialLoading =
    accountsQuery.isPending || transfersQuery.isPending

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            GENEL BAKIŞ
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            {getGreetingName(user?.name, user?.username)}
          </h1>

          <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
            Hesaplarınızı, para transferlerinizi ve işlem hareketlerinizi
            tek merkezden takip edin.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void accountsQuery.refetch()
              void transfersQuery.refetch()
            }}
            disabled={accountsQuery.isFetching || transfersQuery.isFetching}
          >
            <RefreshCw
              className={`size-4 ${
                accountsQuery.isFetching || transfersQuery.isFetching
                  ? "animate-spin"
                  : ""
              }`}
            />
            Yenile
          </Button>

          <Button
            size="lg"
            className="shrink-0"
            render={<Link to="/transfers/new" />}
          >
            <ArrowLeftRight className="size-4" />
            Yeni Transfer
            <ArrowUpRight className="size-4" />
          </Button>
        </div>
      </section>

      <section
        aria-label="Finansal özet"
        className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
      >
        <SummaryCard
          title="Toplam hesap"
          value={
            accountsQuery.isPending
              ? "…"
              : accountsQuery.isError
                ? "—"
                : formatCount(totalAccounts)
          }
          description="Bank A tarafındaki hesaplarınız"
          icon={WalletCards}
        />

        <SummaryCard
          title="Toplam transfer"
          value={
            transfersQuery.isPending
              ? "…"
              : transfersQuery.isError
                ? "—"
                : formatCount(totalTransfers)
          }
          description="Oluşturduğunuz tüm transferler"
          icon={ArrowLeftRight}
        />

        <SummaryCard
          title="Devam eden işlemler"
          value={
            transfersQuery.isPending
              ? "…"
              : transfersQuery.isError
                ? "—"
                : formatCount(inProgressCount)
          }
          description={`Son ${TRANSFER_PAGE_SIZE} transfer içindeki aktif Saga işlemleri`}
          icon={Clock3}
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
        {accountsQuery.isPending ? (
          <Card className="shadow-none">
            <CardContent className="flex min-h-52 items-center justify-center gap-3">
              <RefreshCw className="size-4 animate-spin text-muted-foreground" />
              <p className="text-sm text-muted-foreground">
                Hesap bakiyeleri yükleniyor...
              </p>
            </CardContent>
          </Card>
        ) : accountsQuery.isError ? (
          <Card className="border-destructive/30 shadow-none">
            <CardContent className="flex min-h-52 flex-col items-center justify-center px-6 py-8 text-center">
              <AlertCircle className="mb-3 size-7 text-destructive" />

              <p className="text-sm font-semibold">
                Hesap bilgileri yüklenemedi
              </p>

              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                {getApiErrorMessage(accountsQuery.error)}
              </p>

              <Button
                className="mt-4"
                size="sm"
                variant="outline"
                onClick={() => void accountsQuery.refetch()}
                disabled={accountsQuery.isFetching}
              >
                Tekrar Dene
              </Button>
            </CardContent>
          </Card>
        ) : (
          <AccountBalanceCard
            accounts={accounts}
            totalElements={totalAccounts}
          />
        )}

        <Card className="shadow-none">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Activity className="size-5 text-primary" />
              Hızlı Erişim
            </CardTitle>

            <CardDescription>
              Sık kullanılan işlem alanlarına geçiş yapın.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-3">
            <Link
              to="/transfers/new"
              className="group flex items-center gap-3 rounded-xl border border-border p-3 transition-colors hover:bg-muted/60"
            >
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <ArrowLeftRight className="size-5" />
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">
                  Para Transferi
                </p>

                <p className="mt-0.5 text-xs text-muted-foreground">
                  Yeni bir transfer oluşturun.
                </p>
              </div>

              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </Link>

            <Link
              to="/accounts"
              className="group flex items-center gap-3 rounded-xl border border-border p-3 transition-colors hover:bg-muted/60"
            >
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <WalletCards className="size-5" />
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">
                  Hesaplarım
                </p>

                <p className="mt-0.5 text-xs text-muted-foreground">
                  Hesap bakiyelerinizi ve hareketlerinizi inceleyin.
                </p>
              </div>

              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </Link>

            <Link
              to="/transfers"
              className="group flex items-center gap-3 rounded-xl border border-border p-3 transition-colors hover:bg-muted/60"
            >
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <CalendarClock className="size-5" />
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">
                  İşlem Geçmişi
                </p>

                <p className="mt-0.5 text-xs text-muted-foreground">
                  Tüm transferlerinizi görüntüleyin.
                </p>
              </div>

              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </Link>
          </CardContent>
        </Card>
      </section>

      <section className="space-y-4">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-end">
          <div>
            <h2 className="text-base font-semibold">
              Son İşlemler
            </h2>

            <p className="mt-1 text-xs text-muted-foreground">
              En son oluşturduğunuz transferler ve güncel durumları.
            </p>
          </div>

          <Button
            size="sm"
            variant="outline"
            render={<Link to="/transfers" />}
          >
            Tümünü Gör
            <ArrowUpRight className="size-4" />
          </Button>
        </div>

        {transfersQuery.isPending ? (
          <Card className="shadow-none">
            <CardContent className="flex min-h-44 items-center justify-center gap-3">
              <RefreshCw className="size-4 animate-spin text-muted-foreground" />
              <p className="text-sm text-muted-foreground">
                Transfer geçmişi yükleniyor...
              </p>
            </CardContent>
          </Card>
        ) : transfersQuery.isError ? (
          <Card className="border-destructive/30 shadow-none">
            <CardContent className="flex min-h-44 flex-col items-center justify-center px-6 py-8 text-center">
              <AlertCircle className="mb-3 size-7 text-destructive" />

              <p className="text-sm font-semibold">
                Transfer geçmişi yüklenemedi
              </p>

              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                {getApiErrorMessage(transfersQuery.error)}
              </p>

              <Button
                className="mt-4"
                size="sm"
                variant="outline"
                onClick={() => void transfersQuery.refetch()}
                disabled={transfersQuery.isFetching}
              >
                Tekrar Dene
              </Button>
            </CardContent>
          </Card>
        ) : recentTransfers.length === 0 ? (
          <Card className="border-dashed shadow-none">
            <CardContent className="flex min-h-52 flex-col items-center justify-center px-6 py-8 text-center">
              <CalendarClock className="mb-3 size-8 text-muted-foreground" />

              <p className="text-sm font-semibold">
                Henüz transfer işleminiz bulunmuyor
              </p>

              <p className="mt-1 max-w-md text-xs leading-5 text-muted-foreground">
                Oluşturduğunuz transferler burada görüntülenecek.
              </p>

              <Button
                className="mt-4"
                size="sm"
                render={<Link to="/transfers/new" />}
              >
                <ArrowLeftRight className="size-4" />
                İlk Transferinizi Oluşturun
              </Button>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-3">
            {transfersQuery.isRefetchError && (
              <div
                role="status"
                className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs text-muted-foreground"
              >
                Transfer durumu güncellenemedi. Son başarılı sorgudan
                alınan veriler gösteriliyor.
              </div>
            )}

            {recentTransfers.map((transfer, index) => (
              <RecentTransferCard
                key={
                  transfer.transferId ??
                  `${transfer.correlationId ?? "transfer"}-${index}`
                }
                transfer={transfer}
              />
            ))}
          </div>
        )}
      </section>

      {isInitialLoading && (
        <p className="text-center text-xs text-muted-foreground">
          Finansal bilgileriniz hazırlanıyor...
        </p>
      )}
    </div>
  )
}

function OperationsOverviewPage() {
  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">
          OPERASYON
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Operasyon çalışma alanı
        </h1>

        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
          Dağıtık para transferi altyapısının mutabakat ve anomali
          değerlendirme süreçlerini yönetin.
        </p>
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <Card className="shadow-none">
          <CardHeader>
            <div className="mb-2 flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Activity className="size-5" />
            </div>

            <CardTitle>Mutabakat Merkezi</CardTitle>

            <CardDescription>
              Transfer kayıtlarını ve iki banka sistemindeki ledger
              hareketlerini belirli bir iş günü için karşılaştırın.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <div className="space-y-2 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Kural tabanlı tutarsızlık kontrolleri
              </div>

              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Bank A ve Bank B ledger karşılaştırması
              </div>

              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Mutabakat koşusu ve sonuç ayrıntıları
              </div>
            </div>

            <Button
              className="w-full"
              render={<Link to="/operations/reconciliation" />}
            >
              Mutabakat Merkezini Aç
              <ArrowUpRight className="size-4" />
            </Button>
          </CardContent>
        </Card>

        <Card className="shadow-none">
          <CardHeader>
            <div className="mb-2 flex size-11 items-center justify-center rounded-xl bg-amber-500/10 text-amber-800">
              <ShieldAlert className="size-5" />
            </div>

            <CardTitle>Anomali İzleme</CardTitle>

            <CardDescription>
              Mutabakat çalışmaları sırasında makine öğrenmesi
              tarafından skorlanan transferleri inceleyin.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <div className="space-y-2 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Anomali ve normal sınıflandırmaları
              </div>

              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Model sürümleri ve istatistiksel skorlar
              </div>

              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-primary" />
                Çalışma bazlı anomali sonuçları
              </div>
            </div>

            <Button
              className="w-full"
              variant="outline"
              render={<Link to="/operations/anomalies" />}
            >
              Anomali İzlemeyi Aç
              <ArrowUpRight className="size-4" />
            </Button>
          </CardContent>
        </Card>
      </section>

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ShieldCheck className="size-5 text-primary" />
            Operasyon akışı
          </CardTitle>

          <CardDescription>
            Mutabakat ve makine öğrenmesi değerlendirmesi birlikte
            çalışır; anomali sonuçları ilgili mutabakat koşusuna bağlıdır.
          </CardDescription>
        </CardHeader>

        <CardContent className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-xl bg-muted/40 p-4">
            <p className="text-sm font-semibold">
              1. Mutabakat
            </p>

            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              Transfer ve banka kayıtları karşılaştırılır.
            </p>
          </div>

          <div className="rounded-xl bg-muted/40 p-4">
            <p className="text-sm font-semibold">
              2. Anomali skorlama
            </p>

            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              Uygun transferler Isolation Forest modeliyle değerlendirilir.
            </p>
          </div>

          <div className="rounded-xl bg-muted/40 p-4">
            <p className="text-sm font-semibold">
              3. Sonuç inceleme
            </p>

            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              Operasyon ekibi çalışma sonuçlarını ve tespitleri inceler.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export function OverviewPage() {
  const { hasRole } = useAuth()

  if (hasRole("CUSTOMER")) {
    return <CustomerOverviewPage />
  }

  return <OperationsOverviewPage />
}