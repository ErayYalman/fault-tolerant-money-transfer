import { useState } from "react"
import {
  Activity,
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
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
  ReconciliationRunDetails,
} from "@/features/reconciliation/api/reconciliation.api"
import { useReconciliationRun } from "@/features/reconciliation/hooks/useReconciliation"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

const PAGE_SIZE = 20

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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
  value: number
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

function NoRunSelected() {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
        <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Activity className="size-6" />
        </div>

        <h2 className="text-base font-semibold">
          İncelenecek mutabakat çalışması seçilmedi
        </h2>

        <p className="mt-2 max-w-lg text-sm leading-6 text-muted-foreground">
          Anomali sonuçları belirli bir mutabakat çalışmasına bağlıdır.
          Önce Mutabakat Merkezi üzerinden bir çalışma başlatın veya
          daha önce çalıştırdığınız bir çalışmanın sonuçlarından
          bu ekrana geçin.
        </p>

        <Button
          className="mt-5"
          render={<Link to="/operations/reconciliation" />}
        >
          <CalendarClock className="size-4" />
          Mutabakat Merkezine Git
        </Button>
      </CardContent>
    </Card>
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

  const requestedRunId = searchParams.get("runId")?.trim() || undefined

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
            Mutabakat çalışmaları sırasında makine öğrenmesi modeli
            tarafından değerlendirilen transferleri, anomali skorlarını
            ve model sonuçlarını inceleyin.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          render={<Link to="/operations/reconciliation" />}
        >
          <CalendarClock className="size-4" />
          Mutabakat Merkezi
        </Button>
      </section>

      {!requestedRunId ? (
        <NoRunSelected />
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
        <AnomalyResults
          run={runQuery.data}
          onRefresh={() => void runQuery.refetch()}
          isFetching={runQuery.isFetching}
          isRefetchError={runQuery.isRefetchError}
        />
      ) : null}
    </div>
  )
}