import { useState } from "react"
import {
  Activity,
  AlertCircle,
  CalendarClock,
  CalendarRange,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Filter,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  TriangleAlert,
  type LucideIcon,
} from "lucide-react"
import { Link, useSearchParams } from "react-router"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import type {
  AnomalyScore,
  ReconciliationAnomaliesPage,
  ReconciliationRunDetails,
} from "@/features/reconciliation/api/reconciliation.api"

import {
  useReconciliationAnomalies,
  useReconciliationDailySummary,
  useReconciliationRun,
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

const PAGE_SIZE = 20

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const HISTORY_PAGE_SIZE = 20

const RANGE_PRESETS = [
  { days: 7, label: "7 gün" },
  { days: 30, label: "30 gün" },
  { days: 90, label: "90 gün" },
  { days: 365, label: "1 yıl" },
] as const

type AnomalyFilter = "all" | "anomalies" | "normal"

const RUN_STATUS_LABELS: Record<string, string> = {
  RUNNING: "Çalışıyor",
  COMPLETED: "Tamamlandı",
  FAILED: "Başarısız",
}

const SCORING_STATUS_LABELS: Record<string, string> = {
  NOT_RUN: "Çalıştırılmadı",
  NOT_REQUIRED: "Skorlama gerekmiyor",
  COMPLETED: "Tamamlandı",
  PARTIAL: "Kısmen tamamlandı",
  UNAVAILABLE: "Kullanılamıyor",
}

function formatCount(value: number | undefined): string {
  return new Intl.NumberFormat("tr-TR").format(value ?? 0)
}

function formatScore(value: number | undefined): string {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—"
  }

  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 3,
    maximumFractionDigits: 6,
  }).format(value)
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

function formatBusinessDate(value: string | undefined): string {
  if (!value) {
    return "—"
  }

  const parts = value.split("-")

  if (parts.length !== 3) {
    return value
  }

  return `${parts[2]}.${parts[1]}.${parts[0]}`
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
    return "Tarih aralığı en fazla 366 gün olabilir."
  }

  return null
}

function getRunStatusPresentation(status: string | undefined) {
  if (!status) {
    return {
      label: "Bilinmiyor",
      className: "bg-muted text-muted-foreground",
    }
  }

  const classNames: Record<string, string> = {
    RUNNING: "bg-amber-100 text-amber-800",
    COMPLETED: "bg-primary/10 text-primary",
    FAILED: "bg-destructive/10 text-destructive",
  }

  return {
    label: RUN_STATUS_LABELS[status] ?? status,
    className:
      classNames[status] ?? "bg-muted text-muted-foreground",
  }
}

function getScoringStatusLabel(status: string | undefined): string {
  if (!status) {
    return "Bilinmiyor"
  }

  return SCORING_STATUS_LABELS[status] ?? status
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Mutabakat çalışma bilgileri zamanında alınamadı."
  }

  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Bu çalışma ayrıntılarını görüntülemek için OPERATIONS veya ADMIN rolü gerekiyor."
    }

    if (error.status === 404) {
      return "Belirtilen mutabakat çalışması bulunamadı."
    }

    if (error.status >= 500) {
      return "Mutabakat servisi şu anda yanıt veremiyor."
    }
  }

  return "Anomali sonuçları alınırken beklenmeyen bir hata oluştu."
}

function SummaryCard({
  title,
  value,
  description,
  icon: Icon,
}: {
  title: string
  value: number | string
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
            {typeof value === "number" ? formatCount(value) : value}
          </p>

          <p className="mt-1 text-xs text-muted-foreground">
            {description}
          </p>
        </div>
      </CardContent>
    </Card>
  )
}

function DetailValue({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-xs text-muted-foreground">
        {label}
      </p>

      <p className="break-all text-sm font-medium">
        {value}
      </p>
    </div>
  )
}

function AnomalyScoreCard({
  score,
}: {
  score: AnomalyScore
}) {
  const isAnomaly = score.isAnomaly === true
  const isNormal = score.isAnomaly === false

  const classification = isAnomaly
    ? {
        label: "Anomali",
        className: "bg-amber-500/10 text-amber-800",
      }
    : isNormal
      ? {
          label: "Normal",
          className: "bg-primary/10 text-primary",
        }
      : {
          label: "Sınıflandırılmadı",
          className: "bg-muted text-muted-foreground",
        }

  return (
    <Card className="shadow-none">
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex min-w-0 items-start gap-3">
            <div
              className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
                isAnomaly
                  ? "bg-amber-500/10 text-amber-800"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {isAnomaly ? (
                <ShieldAlert className="size-5" />
              ) : (
                <ShieldCheck className="size-5" />
              )}
            </div>

            <div className="min-w-0">
              <p className="text-sm font-semibold">
                Transfer anomali değerlendirmesi
              </p>

              <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                Transfer ID: {score.transferId ?? "—"}
              </p>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <span
              className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${classification.className}`}
            >
              {classification.label}
            </span>

            <div className="text-right">
              <p className="text-lg font-semibold tabular-nums">
                {formatScore(score.anomalyScore)}
              </p>

              <p className="text-[11px] text-muted-foreground">
                Anomali skoru
              </p>
            </div>
          </div>
        </div>

        <div className="grid gap-4 rounded-xl bg-muted/40 p-4 sm:grid-cols-3">
          <DetailValue
            label="Correlation ID"
            value={score.correlationId ?? "—"}
          />

          <DetailValue
            label="Model sürümü"
            value={score.modelVersion ?? "—"}
          />

          <DetailValue
            label="Skorlama zamanı"
            value={formatDateTime(score.scoredAt)}
          />
        </div>
      </CardContent>
    </Card>
  )
}

type HistoricalAnomaly =
  NonNullable<ReconciliationAnomaliesPage["content"]>[number]

function HistoricalAnomalyCard({
  record,
}: {
  record: HistoricalAnomaly
}) {
  const isAnomaly = record.isAnomaly === true

  return (
    <Card className="shadow-none">
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex min-w-0 items-start gap-3">
            <div
              className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
                isAnomaly
                  ? "bg-amber-500/10 text-amber-800"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {isAnomaly ? (
                <ShieldAlert className="size-5" />
              ) : (
                <ShieldCheck className="size-5" />
              )}
            </div>

            <div className="min-w-0">
              <p className="text-sm font-semibold">
                Transfer anomali değerlendirmesi
              </p>

              <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                Transfer ID: {record.transferId ?? "—"}
              </p>

              <p className="mt-1 text-xs text-muted-foreground">
                İş günü: {formatBusinessDate(record.businessDate)}
              </p>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <span
              className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${
                isAnomaly
                  ? "bg-amber-500/10 text-amber-800"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {isAnomaly ? "Anomali" : "Normal"}
            </span>

            <div className="text-right">
              <p className="text-lg font-semibold tabular-nums">
                {formatScore(record.anomalyScore)}
              </p>

              <p className="text-[11px] text-muted-foreground">
                Anomali skoru
              </p>
            </div>
          </div>
        </div>

        <div className="grid gap-4 rounded-xl bg-muted/40 p-4 sm:grid-cols-3">
          <DetailValue
            label="Correlation ID"
            value={record.correlationId ?? "—"}
          />

          <DetailValue
            label="Model sürümü"
            value={record.modelVersion ?? "—"}
          />

          <DetailValue
            label="Skorlama zamanı"
            value={formatDateTime(record.scoredAt)}
          />
        </div>

        <div className="flex flex-col justify-between gap-3 border-t border-border/70 pt-3 sm:flex-row sm:items-center">
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">
              Mutabakat çalışması
            </p>

            <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
              {record.runId ?? "—"}
            </p>
          </div>

          {record.runId && (
            <Button
              size="sm"
              variant="outline"
              render={
                <Link
                  to={`/operations/anomalies?runId=${encodeURIComponent(record.runId)}`}
                />
              }
            >
              Çalışmayı İncele
              <ChevronRight className="size-3.5" />
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function AnomalyHistoryPage() {
  const today = getIstanbulDate()
  const initialFromDate = shiftDate(today, -29)

  const [fromDate, setFromDate] = useState(initialFromDate)
  const [toDate, setToDate] = useState(today)

  const [appliedRange, setAppliedRange] = useState({
    fromDate: initialFromDate,
    toDate: today,
  })

  const [filter, setFilter] = useState<AnomalyFilter>("all")
  const [page, setPage] = useState(0)
  const [rangeError, setRangeError] = useState<string | null>(null)

  const isAnomaly =
    filter === "all"
      ? undefined
      : filter === "anomalies"

  const anomalyQuery = useReconciliationAnomalies({
    fromDate: appliedRange.fromDate,
    toDate: appliedRange.toDate,
    page,
    size: HISTORY_PAGE_SIZE,
    isAnomaly,
  })

  const summaryQuery = useReconciliationDailySummary({
    fromDate: appliedRange.fromDate,
    toDate: appliedRange.toDate,
  })

  const records = anomalyQuery.data?.content ?? []
  const summary = summaryQuery.data
  const totalPages = anomalyQuery.data?.totalPages ?? 0
  const currentPage = anomalyQuery.data?.page ?? page

  function applyRange(
    nextFromDate = fromDate,
    nextToDate = toDate,
  ) {
    const error = getDateRangeError(nextFromDate, nextToDate)

    if (error) {
      setRangeError(error)
      return
    }

    setRangeError(null)
    setPage(0)

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

  function changeFilter(nextFilter: AnomalyFilter) {
    setFilter(nextFilter)
    setPage(0)
  }

  const showScoringWarning =
    (summary?.incompleteAnomalyScoringDays ?? 0) > 0 ||
    (summary?.anomalyScoringFailedCount ?? 0) > 0

  const hasAnomalyData = Boolean(anomalyQuery.data)

  const filterOptions: {
    value: AnomalyFilter
    label: string
  }[] = [
    { value: "all", label: "Tümü" },
    { value: "anomalies", label: "Anomaliler" },
    { value: "normal", label: "Normal" },
  ]

  return (
    <div className="space-y-6">
      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarRange className="size-5 text-primary" />
            Anomali arama dönemi
          </CardTitle>

          <CardDescription>
            İncelenecek iş günü aralığını belirleyin. En fazla
            366 günlük veri sorgulanabilir.
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
                htmlFor="anomaly-from-date"
                className="text-sm font-medium"
              >
                Başlangıç tarihi
              </label>

              <Input
                id="anomaly-from-date"
                type="date"
                value={fromDate}
                max={today}
                onChange={(event) => setFromDate(event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <label
                htmlFor="anomaly-to-date"
                className="text-sm font-medium"
              >
                Bitiş tarihi
              </label>

              <Input
                id="anomaly-to-date"
                type="date"
                value={toDate}
                max={today}
                onChange={(event) => setToDate(event.target.value)}
              />
            </div>

            <Button
              onClick={() => applyRange()}
              disabled={anomalyQuery.isFetching}
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
            Uygulanan dönem: {formatBusinessDate(appliedRange.fromDate)}
            {" – "}
            {formatBusinessDate(appliedRange.toDate)}
          </p>
        </CardContent>
      </Card>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          title="Eşleşen skor kaydı"
          value={
            anomalyQuery.isPending
              ? "…"
              : anomalyQuery.isError
                ? "—"
                : anomalyQuery.data?.totalElements ?? 0
          }
          description="Seçilen filtreye uyan kayıtlar"
          icon={Activity}
        />

        <SummaryCard
          title="İşaretlenen anomali"
          value={
            summaryQuery.isPending
              ? "…"
              : summaryQuery.isError
                ? "—"
                : summary?.anomaliesFound ?? 0
          }
          description="Son başarılı günlük çalıştırmalar"
          icon={ShieldAlert}
        />

        <SummaryCard
          title="Tamamlanan gün"
          value={
            summaryQuery.isPending
              ? "…"
              : summaryQuery.isError
                ? "—"
                : summary?.completedDays ?? 0
          }
          description="Başarılı mutabakat bulunan günler"
          icon={CheckCircle2}
        />

        <SummaryCard
          title="Eksik puanlama günü"
          value={
            summaryQuery.isPending
              ? "…"
              : summaryQuery.isError
                ? "—"
                : summary?.incompleteAnomalyScoringDays ?? 0
          }
          description="Kısmi veya kullanılamayan ML sonucu"
          icon={TriangleAlert}
        />
      </section>

      {summaryQuery.isError && (
        <div
          role="status"
          className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs leading-5 text-muted-foreground"
        >
          Günlük puanlama kapsamı bilgisi alınamadı. Liste sonuçları
          görüntülenebilir ancak seçilen dönem için puanlama
          bütünlüğü doğrulanamadı.
        </div>
      )}

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
              {formatCount(summary?.incompleteAnomalyScoringDays)} günde
              kısmi veya kullanılamayan puanlama durumu var.
              Toplam başarısız puanlama denemesi:{" "}
              {formatCount(summary?.anomalyScoringFailedCount)}.
              Bu nedenle gösterilen skor kayıtları eksik olabilir.
            </p>

            <p className="text-xs leading-5 text-muted-foreground">
              Puanı bulunmayan transferlerin normal olduğu sonucuna
              varılmamalıdır.
            </p>
          </div>
        </div>
      )}

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Filter className="size-5 text-primary" />
            Transfer skorları
          </CardTitle>

          <CardDescription>
            Tarih aralığında her günün en son başarılı mutabakat
            çalışmasında üretilmiş skorlar listelenir.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
            <div className="flex flex-wrap gap-2">
              {filterOptions.map((option) => (
                <Button
                  key={option.value}
                  size="sm"
                  variant={
                    filter === option.value
                      ? "secondary"
                      : "outline"
                  }
                  aria-pressed={filter === option.value}
                  onClick={() => changeFilter(option.value)}
                >
                  {option.label}
                </Button>
              ))}
            </div>

            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                void anomalyQuery.refetch()
                void summaryQuery.refetch()
              }}
              disabled={
                anomalyQuery.isFetching ||
                summaryQuery.isFetching
              }
            >
              <RefreshCw
                className={`size-4 ${
                  anomalyQuery.isFetching || summaryQuery.isFetching
                    ? "animate-spin"
                    : ""
                }`}
              />
              Yenile
            </Button>
          </div>

          {anomalyQuery.isError && !hasAnomalyData ? (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <AlertCircle className="mb-3 size-7 text-destructive" />

              <p className="text-sm font-semibold">
                Anomali kayıtları yüklenemedi
              </p>

              <p className="mt-2 max-w-lg text-xs leading-5 text-muted-foreground">
                {getErrorMessage(anomalyQuery.error)}
              </p>

              <Button
                className="mt-4"
                size="sm"
                variant="outline"
                onClick={() => void anomalyQuery.refetch()}
                disabled={anomalyQuery.isFetching}
              >
                Tekrar Dene
              </Button>
            </div>
          ) : anomalyQuery.isPending ? (
            <div className="flex min-h-48 items-center justify-center gap-3">
              <RefreshCw className="size-5 animate-spin text-muted-foreground" />

              <p className="text-sm text-muted-foreground">
                Anomali kayıtları yükleniyor...
              </p>
            </div>
          ) : anomalyQuery.data ? (
            <>
              {anomalyQuery.isRefetchError && (
                <div
                  role="status"
                  className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs text-muted-foreground"
                >
                  Güncel kayıtlar alınamadı. Son başarılı sorgudaki
                  veriler gösterilmeye devam ediyor.
                </div>
              )}

              {records.length === 0 ? (
                <div className="flex min-h-48 flex-col items-center justify-center text-center">
                  <ShieldCheck className="mb-3 size-8 text-primary" />

                  <p className="text-sm font-medium">
                    Bu filtrede anomali skoru bulunamadı
                  </p>

                  <p className="mt-1 max-w-lg text-xs leading-5 text-muted-foreground">
                    Farklı bir filtre veya tarih aralığı
                    seçebilirsiniz. Skorlama kapsamının durumunu
                    yukarıdaki kartlardan kontrol edin.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {records.map((record) => (
                    <HistoricalAnomalyCard
                      key={`${record.runId}-${record.transferId}`}
                      record={record}
                    />
                  ))}
                </div>
              )}

              <div className="flex flex-col justify-between gap-3 border-t border-border pt-4 sm:flex-row sm:items-center">
                <p className="text-xs text-muted-foreground">
                  Toplam {formatCount(anomalyQuery.data.totalElements)}
                  {" kayıt · "}
                  Sayfa{" "}
                  {totalPages === 0
                    ? 0
                    : currentPage + 1}
                  {" / "}
                  {totalPages}
                </p>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={
                      page === 0 ||
                      anomalyQuery.isFetching
                    }
                    onClick={() =>
                      setPage((current) => Math.max(0, current - 1))
                    }
                  >
                    <ChevronLeft className="size-4" />
                    Önceki
                  </Button>

                  <Button
                    variant="outline"
                    size="sm"
                    disabled={
                      page + 1 >= totalPages ||
                      anomalyQuery.isFetching
                    }
                    onClick={() => setPage((current) => current + 1)}
                  >
                    Sonraki
                    <ChevronRight className="size-4" />
                  </Button>
                </div>
              </div>
            </>
          ) : null}
        </CardContent>
      </Card>

      <div className="rounded-xl border border-border/70 bg-muted/30 p-4">
        <div className="flex items-start gap-3">
          <Activity className="mt-0.5 size-5 shrink-0 text-primary" />

          <div className="space-y-1">
            <p className="text-sm font-semibold">
              Anomali skorları hakkında
            </p>

            <p className="text-xs leading-5 text-muted-foreground">
              Anomali skoru kalibre edilmiş bir olasılık değildir.
              Modelin istatistiksel çıktısıdır ve sınıflandırma,
              modelin karar eşiğine göre yapılır. Bu ekran yalnızca
              başarıyla kaydedilmiş skorları gösterir. Kısmi veya
              başarısız puanlamalarda skor kaydı olmayan transferler
              listede görünmeyebilir.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

function RunNotFound({
  message,
  onRetry,
  isFetching,
}: {
  message: string
  onRetry?: () => void
  isFetching?: boolean
}) {
  return (
    <Card className="border-destructive/30 shadow-none">
      <CardContent className="flex min-h-64 flex-col items-center justify-center px-6 py-10 text-center">
        <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
          <AlertCircle className="size-6" />
        </div>

        <h2 className="text-base font-semibold">
          Anomali sonuçları yüklenemedi
        </h2>

        <p className="mt-2 max-w-lg text-sm leading-6 text-muted-foreground">
          {message}
        </p>

        {onRetry && (
          <Button
            className="mt-5"
            variant="outline"
            onClick={onRetry}
            disabled={isFetching}
          >
            <RefreshCw
              className={`size-4 ${isFetching ? "animate-spin" : ""}`}
            />
            Tekrar Dene
          </Button>
        )}

        <Button
          className="mt-2"
          variant="ghost"
          render={<Link to="/operations/reconciliation" />}
        >
          Mutabakat Merkezine Dön
        </Button>
      </CardContent>
    </Card>
  )
}

function AnomalyResults({
  run,
  onRefresh,
  isFetching,
  isRefetchError,
}: {
  run: ReconciliationRunDetails
  onRefresh: () => void
  isFetching: boolean
  isRefetchError: boolean
}) {
  const [filter, setFilter] = useState<AnomalyFilter>("all")
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE)

  const scores = run.anomalyScores ?? []
  const summary = run.anomalySummary

  const flaggedCount = scores.filter(
    (score) => score.isAnomaly === true,
  ).length

  const normalCount = scores.filter(
    (score) => score.isAnomaly === false,
  ).length

  const sortedScores = [...scores].sort(
    (left, right) =>
      (right.anomalyScore ?? -1) -
      (left.anomalyScore ?? -1),
  )

  const filteredScores = sortedScores.filter((score) => {
    if (filter === "anomalies") {
      return score.isAnomaly === true
    }

    if (filter === "normal") {
      return score.isAnomaly === false
    }

    return true
  })

  const visibleScores = filteredScores.slice(0, visibleCount)
  const runStatus = getRunStatusPresentation(run.status)

  const scoredCount = summary?.scoredCount ?? scores.length
  const anomalyCount = summary?.anomalyCount ?? flaggedCount
  const attemptedCount = summary?.attemptedCount ?? scores.length
  const failedCount = summary?.failedCount ?? 0

  const filterOptions: {
    value: AnomalyFilter
    label: string
    count: number
  }[] = [
    {
      value: "all",
      label: "Tümü",
      count: scores.length,
    },
    {
      value: "anomalies",
      label: "Anomaliler",
      count: flaggedCount,
    },
    {
      value: "normal",
      label: "Normal",
      count: normalCount,
    },
  ]

  return (
    <div className="space-y-6">
      {isRefetchError && (
        <div
          role="status"
          className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-sm text-muted-foreground"
        >
          Güncel sonuçlar alınamadı. Son başarılı sorgudan gelen kayıtlar
          görüntülenmeye devam ediyor.
        </div>
      )}

      {run.errorMessage && (
        <div
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
        >
          {run.errorMessage}
        </div>
      )}

      <section className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div className="space-y-1">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            MUTABAKAT ÇALIŞMASI
          </p>

          <h2 className="text-xl font-semibold tracking-tight">
            Anomali değerlendirme sonuçları
          </h2>

          <p className="break-all font-mono text-xs text-muted-foreground">
            Run ID: {run.runId ?? "—"}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`w-fit rounded-full px-3 py-1.5 text-xs font-semibold ${runStatus.className}`}
          >
            {runStatus.label}
          </span>

          <Button
            size="sm"
            variant="outline"
            onClick={onRefresh}
            disabled={isFetching}
          >
            <RefreshCw
              className={`size-4 ${isFetching ? "animate-spin" : ""}`}
            />
            Yenile
          </Button>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          title="Skorlama denemesi"
          value={attemptedCount}
          description="ML servisine gönderilen uygun transfer"
          icon={Activity}
        />

        <SummaryCard
          title="Başarılı skor"
          value={scoredCount}
          description="Skor sonucu alınan transfer"
          icon={CheckCircle2}
        />

        <SummaryCard
          title="Anomali"
          value={anomalyCount}
          description="Model tarafından işaretlenen transfer"
          icon={ShieldAlert}
        />

        <SummaryCard
          title="Başarısız deneme"
          value={failedCount}
          description="Skorlama sonucu alınamayan transfer"
          icon={AlertCircle}
        />
      </section>

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="size-5 text-primary" />
            Çalışma ve model bilgileri
          </CardTitle>

          <CardDescription>
            Bu sonuçlar seçilen mutabakat çalışmasının kapsamına aittir.
          </CardDescription>
        </CardHeader>

        <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <DetailValue
            label="İş günü"
            value={formatBusinessDate(run.businessDate)}
          />

          <DetailValue
            label="Çalışma türü"
            value={run.runType ?? "—"}
          />

          <DetailValue
            label="Skorlama durumu"
            value={getScoringStatusLabel(summary?.status)}
          />

          <DetailValue
            label="Çalışma başlangıcı"
            value={formatDateTime(run.startedAt)}
          />

          <DetailValue
            label="Çalışma bitişi"
            value={formatDateTime(run.finishedAt)}
          />

          <DetailValue
            label="Kullanılan model sürümleri"
            value={
              summary?.modelVersions?.length
                ? summary.modelVersions.join(", ")
                : "—"
            }
          />
        </CardContent>

        {summary?.errorMessage && (
          <div className="mx-4 mb-4 rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs leading-5 text-muted-foreground">
            {summary.errorMessage}
          </div>
        )}
      </Card>

      <section className="space-y-4">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
          <div>
            <h3 className="text-base font-semibold">
              Transfer skorları
            </h3>

            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              Kayıtlar anomali skoruna göre yüksekten düşüğe sıralanır.
              Anomali olarak sınıflandırılmış transferler ayrıca işaretlenir.
            </p>
          </div>

          <span className="text-xs text-muted-foreground">
            {formatCount(filteredScores.length)} kayıt
          </span>
        </div>

        <div className="flex flex-wrap gap-2">
          {filterOptions.map((option) => (
            <Button
              key={option.value}
              size="sm"
              variant={filter === option.value ? "secondary" : "outline"}
              aria-pressed={filter === option.value}
              onClick={() => {
                setFilter(option.value)
                setVisibleCount(PAGE_SIZE)
              }}
            >
              {option.label}
              <span className="ml-1 tabular-nums text-muted-foreground">
                {formatCount(option.count)}
              </span>
            </Button>
          ))}
        </div>

        {filteredScores.length === 0 ? (
          <Card className="border-dashed shadow-none">
            <CardContent className="flex min-h-40 flex-col items-center justify-center px-6 py-8 text-center">
              <ShieldCheck className="mb-3 size-8 text-primary" />

              <p className="font-medium">
                {scores.length === 0
                  ? "Bu çalışma için anomali skoru bulunmuyor"
                  : "Bu filtrede gösterilecek kayıt bulunmuyor"}
              </p>

              <p className="mt-1 max-w-lg text-xs leading-5 text-muted-foreground">
                {scores.length === 0
                  ? "Skorlama durumu ve varsa hata açıklamaları çalışma bilgileri bölümünde görüntülenebilir."
                  : "Diğer kayıtları görmek için farklı bir filtre seçebilirsiniz."}
              </p>
            </CardContent>
          </Card>
        ) : (
          <>
            <div className="space-y-3" aria-label="Anomali skor listesi">
              {visibleScores.map((score, index) => (
                <AnomalyScoreCard
                  key={`${score.transferId ?? "score"}-${score.scoredAt ?? index}`}
                  score={score}
                />
              ))}
            </div>

            {filteredScores.length > visibleScores.length && (
              <div className="flex flex-col items-center gap-2 pt-2">
                <p className="text-xs text-muted-foreground">
                  {visibleScores.length} / {filteredScores.length} kayıt gösteriliyor
                </p>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setVisibleCount((current) => current + PAGE_SIZE)
                  }
                >
                  Daha Fazla Göster
                </Button>
              </div>
            )}
          </>
        )}
      </section>

      <div className="rounded-xl border border-border/70 bg-muted/30 p-4">
        <div className="flex items-start gap-3">
          <Activity className="mt-0.5 size-5 shrink-0 text-primary" />

          <div className="space-y-1">
            <p className="text-sm font-semibold">
              Anomali skoru nasıl yorumlanmalı?
            </p>

            <p className="text-xs leading-5 text-muted-foreground">
              Anomali skoru 0 ile 1 arasında istatistiksel bir model
              çıktısıdır; kalibre edilmiş bir olasılık veya dolandırıcılık
              ihtimali değildir. Sınıflandırma, modelin karar eşiğine göre
              yapılır. Bu nedenle skor tek başına kesin bir risk kanıtı
              olarak değerlendirilmemelidir.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

export function AnomalyMonitoringPage() {
  const [searchParams] = useSearchParams()

  const requestedRunId =
    searchParams.get("runId")?.trim() || undefined

  const isValidRunId =
    requestedRunId !== undefined &&
    UUID_PATTERN.test(requestedRunId)

  const runId = isValidRunId ? requestedRunId : undefined

  const runQuery = useReconciliationRun(runId)

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            OPERASYON
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Anomali İzleme
          </h1>

          <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
            Transferlerin makine öğrenmesi modeli tarafından
            değerlendirilen anomali skorlarını tarih aralığına göre
            tarayın veya belirli bir mutabakat çalışmasının
            sonuçlarını ayrıntılı inceleyin.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            render={<Link to="/operations/reports" />}
          >
            <ClipboardList className="size-4" />
            Mutabakat Raporları
          </Button>

          <Button
            variant="outline"
            size="sm"
            render={<Link to="/operations/reconciliation" />}
          >
            <CalendarClock className="size-4" />
            Mutabakat Merkezi
          </Button>
        </div>
      </section>

      {!requestedRunId ? (
        <AnomalyHistoryPage />
      ) : !isValidRunId ? (
        <RunNotFound message="URL içerisindeki çalışma kimliği geçerli bir UUID değil. Mutabakat Merkezi üzerinden bir çalışma seçerek tekrar deneyin." />
      ) : runQuery.isPending ? (
        <Card className="shadow-none">
          <CardContent className="flex min-h-64 items-center justify-center gap-3">
            <RefreshCw className="size-5 animate-spin text-muted-foreground" />

            <p className="text-sm text-muted-foreground">
              Mutabakat ve anomali sonuçları yükleniyor...
            </p>
          </CardContent>
        </Card>
      ) : runQuery.isError && !runQuery.data ? (
        <RunNotFound
          message={getErrorMessage(runQuery.error)}
          onRetry={() => void runQuery.refetch()}
          isFetching={runQuery.isFetching}
        />
      ) : runQuery.data ? (
        <>
          <div>
            <Button
              variant="outline"
              size="sm"
              render={<Link to="/operations/anomalies" />}
            >
              <ChevronLeft className="size-4" />
              Anomali geçmişine dön
            </Button>
          </div>

          <AnomalyResults
            run={runQuery.data}
            onRefresh={() => void runQuery.refetch()}
            isFetching={runQuery.isFetching}
            isRefetchError={runQuery.isRefetchError}
          />
        </>
      ) : null}
    </div>
  )
}