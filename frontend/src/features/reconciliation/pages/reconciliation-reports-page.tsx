import { useState } from "react"
import { Link } from "react-router"
import {
  Activity,
  AlertCircle,
  ArrowRight,
  CalendarRange,
  ClipboardList,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  TriangleAlert,
  type LucideIcon,
} from "lucide-react"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import {
  type ReconciliationDailySummary,
  type ReconciliationRunsPage,
} from "@/features/reconciliation/api/reconciliation.api"

import {
  useReconciliationDailySummary,
  useReconciliationRuns,
} from "@/features/reconciliation/hooks/useReconciliation"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { DailyMetricsCharts } from "@/features/reconciliation/components/daily-metrics-charts"

const HISTORY_PAGE_SIZE = 10

type RunSummary = NonNullable<ReconciliationRunsPage["content"]>[number]
type DailySummaryItem = NonNullable<ReconciliationDailySummary["daily"]>[number]

const RANGE_PRESETS = [
  { days: 7, label: "7 gün" },
  { days: 30, label: "30 gün" },
  { days: 90, label: "90 gün" },
  { days: 365, label: "1 yıl" },
] as const

const RUN_STATUS_PRESENTATIONS: Record<
  string,
  { label: string; className: string }
> = {
  COMPLETED: {
    label: "Tamamlandı",
    className: "bg-primary/10 text-primary",
  },
  FAILED: {
    label: "Başarısız",
    className: "bg-destructive/10 text-destructive",
  },
  RUNNING: {
    label: "Çalışıyor",
    className: "bg-amber-100 text-amber-800",
  },
}

const SCORING_STATUS_PRESENTATIONS: Record<
  string,
  { label: string; className: string }
> = {
  NOT_RUN: {
    label: "Çalıştırılmadı",
    className: "bg-muted text-muted-foreground",
  },
  NOT_REQUIRED: {
    label: "Gerekli değil",
    className: "bg-muted text-muted-foreground",
  },
  COMPLETED: {
    label: "Tamamlandı",
    className: "bg-primary/10 text-primary",
  },
  PARTIAL: {
    label: "Kısmen tamamlandı",
    className: "bg-amber-100 text-amber-800",
  },
  UNAVAILABLE: {
    label: "Kullanılamıyor",
    className: "bg-destructive/10 text-destructive",
  },
}

function getIstanbulDate(): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Istanbul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date())

  const values = new Map(
    parts.map((part) => [part.type, part.value]),
  )

  const year = values.get("year") ?? "2026"
  const month = values.get("month") ?? "01"
  const day = values.get("day") ?? "01"

  return `${year}-${month}-${day}`
}

function shiftDate(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00.000Z`)
  date.setUTCDate(date.getUTCDate() + days)

  return date.toISOString().slice(0, 10)
}

function getDateRangeError(
  fromDate: string,
  toDate: string,
): string | null {
  if (!fromDate || !toDate) {
    return "Başlangıç ve bitiş tarihlerini seçin."
  }

  if (fromDate > toDate) {
    return "Başlangıç tarihi bitiş tarihinden sonra olamaz."
  }

  const from = new Date(`${fromDate}T00:00:00.000Z`)
  const to = new Date(`${toDate}T00:00:00.000Z`)

  if (
    Number.isNaN(from.getTime()) ||
    Number.isNaN(to.getTime())
  ) {
    return "Geçerli bir tarih aralığı seçin."
  }

  const days =
    Math.round((to.getTime() - from.getTime()) / 86_400_000) + 1

  if (days > 366) {
    return "Rapor aralığı en fazla 366 gün olabilir."
  }

  return null
}

function formatDate(value: string | undefined): string {
  if (!value) {
    return "—"
  }

  const parts = value.split("-")

  if (parts.length !== 3) {
    return value
  }

  return `${parts[2]}.${parts[1]}.${parts[0]}`
}

function formatDateTime(value: string | undefined): string {
  if (!value) {
    return "—"
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

function formatCount(value: number | undefined): string {
  return new Intl.NumberFormat("tr-TR").format(value ?? 0)
}

function getRunStatus(status: string | undefined) {
  if (!status) {
    return {
      label: "Bilinmiyor",
      className: "bg-muted text-muted-foreground",
    }
  }

  return (
    RUN_STATUS_PRESENTATIONS[status] ?? {
      label: status,
      className: "bg-muted text-muted-foreground",
    }
  )
}

function getScoringStatus(status: string | undefined) {
  if (!status) {
    return {
      label: "Bilinmiyor",
      className: "bg-muted text-muted-foreground",
    }
  }

  return (
    SCORING_STATUS_PRESENTATIONS[status] ?? {
      label: status,
      className: "bg-muted text-muted-foreground",
    }
  )
}

function getQueryErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Rapor isteği zamanında yanıt vermedi."
  }

  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Raporları görüntülemek için OPERATIONS veya ADMIN rolü gerekiyor."
    }

    if (error.status >= 500) {
      return "Mutabakat servisi şu anda yanıt veremiyor."
    }
  }

  return "Rapor verileri alınırken beklenmeyen bir hata oluştu."
}

function SummaryCard({
  title,
  value,
  description,
  icon: Icon,
}: {
  title: string
  value: number | undefined
  description: string
  icon: LucideIcon
}) {
  return (
    <Card className="shadow-none">
      <CardContent className="flex items-center gap-4">
        <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Icon className="size-5" />
        </div>

        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">
            {title}
          </p>

          <p className="mt-1 text-2xl font-semibold tabular-nums">
            {formatCount(value)}
          </p>

          <p className="mt-1 text-xs text-muted-foreground">
            {description}
          </p>
        </div>
      </CardContent>
    </Card>
  )
}

function DailyMetricRow({
  item,
}: {
  item: DailySummaryItem
}) {
  const scoring = getScoringStatus(item.anomalyScoringStatus)

  return (
    <div className="grid gap-3 border-b border-border/70 px-4 py-4 last:border-0 sm:grid-cols-[1.1fr_repeat(3,minmax(0,1fr))_1.3fr] sm:items-center">
      <div>
        <p className="text-sm font-medium">
          {formatDate(item.businessDate)}
        </p>

        <p className="mt-1 break-all font-mono text-[10px] text-muted-foreground">
          {item.runId}
        </p>
      </div>

      <div>
        <p className="text-xs text-muted-foreground sm:hidden">
          İncelenen transfer
        </p>

        <p className="text-sm font-semibold tabular-nums">
          {formatCount(item.transfersChecked)}
        </p>
      </div>

      <div>
        <p className="text-xs text-muted-foreground sm:hidden">
          Mutabakat farkı
        </p>

        <p className="text-sm font-semibold tabular-nums">
          {formatCount(item.discrepanciesFound)}
        </p>
      </div>

      <div>
        <p className="text-xs text-muted-foreground sm:hidden">
          Anomali
        </p>

        <p className="text-sm font-semibold tabular-nums">
          {formatCount(item.anomaliesFound)}
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-medium ${scoring.className}`}
        >
          {scoring.label}
        </span>

        {(item.anomalyScoringFailedCount ?? 0) > 0 && (
          <span className="text-xs text-muted-foreground">
            {formatCount(item.anomalyScoringFailedCount)} başarısız
          </span>
        )}
      </div>
    </div>
  )
}

function RunHistoryRow({
  run,
}: {
  run: RunSummary
}) {
  const status = getRunStatus(run.status)
  const scoring = getScoringStatus(run.anomalyScoringStatus)

  return (
    <div className="grid gap-4 border-b border-border/70 px-4 py-4 last:border-0 xl:grid-cols-[1.1fr_0.8fr_0.8fr_repeat(3,minmax(0,0.65fr))_1.2fr] xl:items-center">
      <div className="min-w-0">
        <p className="text-sm font-medium">
          {formatDate(run.businessDate)}
        </p>

        <p
          className="mt-1 truncate font-mono text-[10px] text-muted-foreground"
          title={run.runId}
        >
          {run.runId}
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span
          className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${status.className}`}
        >
          {status.label}
        </span>

        <span className="text-xs text-muted-foreground">
          {run.runType === "MANUAL" ? "Manuel" : "Zamanlanmış"}
        </span>
      </div>

      <div>
        <p className="text-xs text-muted-foreground xl:hidden">
          Skorlama
        </p>

        <span
          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${scoring.className}`}
        >
          {scoring.label}
        </span>
      </div>

      <div>
        <p className="text-xs text-muted-foreground xl:hidden">
          Transfer
        </p>

        <p className="text-sm font-medium tabular-nums">
          {formatCount(run.transfersChecked)}
        </p>
      </div>

      <div>
        <p className="text-xs text-muted-foreground xl:hidden">
          Fark
        </p>

        <p className="text-sm font-medium tabular-nums">
          {formatCount(run.discrepanciesFound)}
        </p>
      </div>

      <div>
        <p className="text-xs text-muted-foreground xl:hidden">
          Anomali
        </p>

        <p className="text-sm font-medium tabular-nums">
          {formatCount(run.anomaliesFound)}
        </p>
      </div>

      <div className="flex items-center justify-between gap-3 xl:justify-end">
        <p className="text-xs text-muted-foreground">
          {formatDateTime(run.startedAt)}
        </p>

        {run.runId && (
          <Button
            size="sm"
            variant="outline"
            render={
              <Link
                to={`/operations/anomalies?runId=${encodeURIComponent(run.runId)}`}
              />
            }
          >
            İncele
            <ArrowRight className="size-3.5" />
          </Button>
        )}
      </div>
    </div>
  )
}

function QueryError({
  message,
  onRetry,
  isFetching,
}: {
  message: string
  onRetry: () => void
  isFetching: boolean
}) {
  return (
    <Card className="border-destructive/30 shadow-none">
      <CardContent className="flex flex-col items-center justify-center px-6 py-10 text-center">
        <AlertCircle className="mb-3 size-7 text-destructive" />

        <p className="text-sm font-semibold">
          Rapor verileri yüklenemedi
        </p>

        <p className="mt-2 max-w-lg text-xs leading-5 text-muted-foreground">
          {message}
        </p>

        <Button
          className="mt-4"
          size="sm"
          variant="outline"
          onClick={onRetry}
          disabled={isFetching}
        >
          <RefreshCw
            className={`size-4 ${isFetching ? "animate-spin" : ""}`}
          />
          Tekrar Dene
        </Button>
      </CardContent>
    </Card>
  )
}

export function ReconciliationReportsPage() {
  const today = getIstanbulDate()
  const initialFromDate = shiftDate(today, -29)

  const [fromDate, setFromDate] = useState(initialFromDate)
  const [toDate, setToDate] = useState(today)

  const [appliedRange, setAppliedRange] = useState({
    fromDate: initialFromDate,
    toDate: today,
  })

  const [historyPage, setHistoryPage] = useState(0)
  const [rangeError, setRangeError] = useState<string | null>(null)

  const summaryQuery = useReconciliationDailySummary({
    fromDate: appliedRange.fromDate,
    toDate: appliedRange.toDate,
  })

  const runsQuery = useReconciliationRuns({
    fromDate: appliedRange.fromDate,
    toDate: appliedRange.toDate,
    page: historyPage,
    size: HISTORY_PAGE_SIZE,
  })

  const summary = summaryQuery.data
  const dailyItems = summary?.daily ?? []
  const runs = runsQuery.data?.content ?? []
  const totalPages = runsQuery.data?.totalPages ?? 0
  const currentPage = runsQuery.data?.page ?? historyPage

  function applyRange(nextFromDate = fromDate, nextToDate = toDate) {
    const error = getDateRangeError(nextFromDate, nextToDate)

    if (error) {
      setRangeError(error)
      return
    }

    setRangeError(null)
    setHistoryPage(0)

    setAppliedRange({
      fromDate: nextFromDate,
      toDate: nextToDate,
    })
  }

  function selectPreset(days: number) {
    const nextFromDate = shiftDate(today, -(days - 1))

    setFromDate(nextFromDate)
    setToDate(today)
    applyRange(nextFromDate, today)
  }

  const hasSummaryData = Boolean(summary)
  const hasRunData = Boolean(runsQuery.data)

  const showScoringWarning =
    (summary?.incompleteAnomalyScoringDays ?? 0) > 0 ||
    (summary?.anomalyScoringFailedCount ?? 0) > 0

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            OPERASYON
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Mutabakat Raporları
          </h1>

          <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
            Günlük mutabakat performansını, tespit edilen farkları,
            anomali sonuçlarını ve geçmiş çalışma kayıtlarını
            seçtiğiniz tarih aralığında inceleyin.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            void summaryQuery.refetch()
            void runsQuery.refetch()
          }}
          disabled={summaryQuery.isFetching || runsQuery.isFetching}
        >
          <RefreshCw
            className={`size-4 ${
              summaryQuery.isFetching || runsQuery.isFetching
                ? "animate-spin"
                : ""
            }`}
          />
          Raporları Yenile
        </Button>
      </section>

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarRange className="size-5 text-primary" />
            Rapor dönemi
          </CardTitle>

          <CardDescription>
            En fazla 366 günlük bir tarih aralığı seçebilirsiniz.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {RANGE_PRESETS.map((preset) => (
              <Button
                key={preset.days}
                size="sm"
                variant="outline"
                onClick={() => selectPreset(preset.days)}
              >
                {preset.label}
              </Button>
            ))}
          </div>

          <div className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
            <div className="space-y-2">
              <label
                htmlFor="report-from-date"
                className="text-sm font-medium"
              >
                Başlangıç tarihi
              </label>

              <Input
                id="report-from-date"
                type="date"
                value={fromDate}
                max={today}
                onChange={(event) => setFromDate(event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <label
                htmlFor="report-to-date"
                className="text-sm font-medium"
              >
                Bitiş tarihi
              </label>

              <Input
                id="report-to-date"
                type="date"
                value={toDate}
                max={today}
                onChange={(event) => setToDate(event.target.value)}
              />
            </div>

            <Button
              onClick={() => applyRange()}
              disabled={summaryQuery.isFetching || runsQuery.isFetching}
            >
              <CalendarRange className="size-4" />
              Uygula
            </Button>
          </div>

          {rangeError && (
            <p role="alert" className="text-sm text-destructive">
              {rangeError}
            </p>
          )}

          <p className="text-xs text-muted-foreground">
            Gösterilen dönem: {formatDate(appliedRange.fromDate)}
            {" – "}
            {formatDate(appliedRange.toDate)}
          </p>
        </CardContent>
      </Card>

      {summaryQuery.isError && !hasSummaryData ? (
        <QueryError
          message={getQueryErrorMessage(summaryQuery.error)}
          onRetry={() => void summaryQuery.refetch()}
          isFetching={summaryQuery.isFetching}
        />
      ) : summaryQuery.isPending ? (
        <Card className="shadow-none">
          <CardContent className="flex min-h-48 items-center justify-center gap-3">
            <RefreshCw className="size-5 animate-spin text-muted-foreground" />

            <p className="text-sm text-muted-foreground">
              Günlük rapor verileri yükleniyor...
            </p>
          </CardContent>
        </Card>
      ) : summary ? (
        <>
          {summaryQuery.isRefetchError && (
            <div
              role="status"
              className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-sm text-muted-foreground"
            >
              Güncel özet alınamadı. Son başarılı sorgudaki veriler
              gösterilmeye devam ediyor.
            </div>
          )}

          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              title="Tamamlanan gün"
              value={summary.completedDays}
              description="En az bir başarılı mutabakat bulunan gün"
              icon={CalendarRange}
            />

            <SummaryCard
              title="İncelenen transfer"
              value={summary.transfersChecked}
              description="Seçilen dönemin toplam transfer sayısı"
              icon={Activity}
            />

            <SummaryCard
              title="Mutabakat farkı"
              value={summary.discrepanciesFound}
              description="Son başarılı günlük çalıştırmalardaki farklar"
              icon={ShieldAlert}
            />

            <SummaryCard
              title="Anomali"
              value={summary.anomaliesFound}
              description="Son başarılı günlük çalıştırmalarda işaretlenenler"
              icon={TriangleAlert}
            />
          </section>

          {showScoringWarning && (
            <div
              role="status"
              className="flex items-start gap-3 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4"
            >
              <AlertCircle className="mt-0.5 size-5 shrink-0 text-amber-700" />

              <div className="space-y-1">
                <p className="text-sm font-semibold">
                  Bazı günlerde anomali puanlaması eksik
                </p>

                <p className="text-xs leading-5 text-muted-foreground">
                  {formatCount(summary.incompleteAnomalyScoringDays)} günde
                  kısmi veya kullanılamayan puanlama durumu var.
                  Toplam başarısız puanlama denemesi:{" "}
                  {formatCount(summary.anomalyScoringFailedCount)}.
                  Bu nedenle anomali sayıları eksik olabilir.
                </p>
              </div>
            </div>
          )}

          {dailyItems.length > 0 && (
            <DailyMetricsCharts
              items={dailyItems}
              fromDate={appliedRange.fromDate}
              toDate={appliedRange.toDate}
            />
          )}

          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Activity className="size-5 text-primary" />
                Günlük mutabakat özeti
              </CardTitle>

              <CardDescription>
                Her iş günü için yalnızca en son başarılı mutabakat
                çalıştırması hesaba katılır.
              </CardDescription>
            </CardHeader>

            {dailyItems.length === 0 ? (
              <CardContent>
                <div className="flex min-h-40 flex-col items-center justify-center text-center">
                  <CalendarRange className="mb-3 size-8 text-muted-foreground" />

                  <p className="text-sm font-medium">
                    Bu tarih aralığında başarılı mutabakat bulunamadı
                  </p>

                  <p className="mt-1 max-w-lg text-xs leading-5 text-muted-foreground">
                    Başarısız veya devam eden çalışmalar geçmiş
                    çalıştırmalar tablosunda görüntülenebilir.
                  </p>
                </div>
              </CardContent>
            ) : (
              <CardContent className="p-0">
                <div className="hidden grid-cols-[1.1fr_repeat(3,minmax(0,1fr))_1.3fr] gap-3 border-y border-border bg-muted/40 px-4 py-3 text-xs font-medium text-muted-foreground sm:grid">
                  <span>İş günü</span>
                  <span>Transfer</span>
                  <span>Fark</span>
                  <span>Anomali</span>
                  <span>Skorlama durumu</span>
                </div>

                {dailyItems.map((item, index) => (
                  <DailyMetricRow
                    key={item.runId ?? `${item.businessDate}-${index}`}
                    item={item}
                  />
                ))}
              </CardContent>
            )}
          </Card>
        </>
      ) : null}

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ClipboardList className="size-5 text-primary" />
            Mutabakat çalışma geçmişi
          </CardTitle>

          <CardDescription>
            Seçilen tarih aralığındaki bütün çalışmalar. Tekrarlanan
            manuel çalışmalar ve başarısız kayıtlar da dahildir.
          </CardDescription>
        </CardHeader>

        {runsQuery.isError && !hasRunData ? (
          <CardContent>
            <QueryError
              message={getQueryErrorMessage(runsQuery.error)}
              onRetry={() => void runsQuery.refetch()}
              isFetching={runsQuery.isFetching}
            />
          </CardContent>
        ) : runsQuery.isPending ? (
          <CardContent>
            <div className="flex min-h-40 items-center justify-center gap-3">
              <RefreshCw className="size-5 animate-spin text-muted-foreground" />

              <p className="text-sm text-muted-foreground">
                Çalışma geçmişi yükleniyor...
              </p>
            </div>
          </CardContent>
        ) : runsQuery.data ? (
          <>
            {runsQuery.isRefetchError && (
              <div className="mx-6 mb-2 rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs text-muted-foreground">
                Güncel çalışma geçmişi alınamadı. Son başarılı
                sorgudaki kayıtlar gösteriliyor.
              </div>
            )}

            {runs.length === 0 ? (
              <CardContent>
                <div className="flex min-h-40 flex-col items-center justify-center text-center">
                  <ClipboardList className="mb-3 size-8 text-muted-foreground" />

                  <p className="text-sm font-medium">
                    Bu dönemde mutabakat çalışması bulunmuyor
                  </p>

                  <p className="mt-1 text-xs text-muted-foreground">
                    Farklı bir tarih aralığı seçebilirsiniz.
                  </p>
                </div>
              </CardContent>
            ) : (
              <CardContent className="p-0">
                <div className="hidden grid-cols-[1.1fr_0.8fr_0.8fr_repeat(3,minmax(0,0.65fr))_1.2fr] gap-4 border-y border-border bg-muted/40 px-4 py-3 text-xs font-medium text-muted-foreground xl:grid">
                  <span>İş günü / Run ID</span>
                  <span>Durum / Tür</span>
                  <span>Skorlama</span>
                  <span>Transfer</span>
                  <span>Fark</span>
                  <span>Anomali</span>
                  <span className="text-right">Başlangıç / İşlem</span>
                </div>

                {runs.map((run) => (
                  <RunHistoryRow
                    key={run.runId}
                    run={run}
                  />
                ))}
              </CardContent>
            )}

            <div className="flex flex-col justify-between gap-3 border-t border-border px-4 py-4 sm:flex-row sm:items-center">
              <p className="text-xs text-muted-foreground">
                Toplam {formatCount(runsQuery.data.totalElements)} çalışma
                {" · "}
                Sayfa {totalPages === 0 ? 0 : currentPage + 1}
                {" / "}
                {totalPages}
              </p>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    historyPage === 0 ||
                    runsQuery.isFetching
                  }
                  onClick={() =>
                    setHistoryPage((current) => Math.max(0, current - 1))
                  }
                >
                  Önceki
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    historyPage + 1 >= totalPages ||
                    runsQuery.isFetching
                  }
                  onClick={() =>
                    setHistoryPage((current) => current + 1)
                  }
                >
                  Sonraki
                </Button>
              </div>
            </div>
          </>
        ) : null}
      </Card>

      <div className="rounded-xl border border-border/70 bg-muted/30 p-4">
        <div className="flex items-start gap-3">
          <ShieldCheck className="mt-0.5 size-5 shrink-0 text-primary" />

          <div className="space-y-1">
            <p className="text-sm font-semibold">
              Raporlama yöntemi
            </p>

            <p className="text-xs leading-5 text-muted-foreground">
              Günlük metrikler aynı iş günü için tekrar çalıştırılmış
              mutabakatları çift saymamak amacıyla en son COMPLETED
              çalıştırmadan hesaplanır. Çalışma geçmişi ise bütün
              kayıtları içerir. Anomali skorları kalibre edilmiş
              olasılık değildir; eksik puanlama olan günlerde rapor
              sonuçları eksik olabilir.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}