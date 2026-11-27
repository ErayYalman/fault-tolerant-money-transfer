import { useState } from "react"
import {
  Activity,
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  Database,
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
  type AnomalyScore,
  type ReconciliationDiscrepancy,
  type ReconciliationRunDetails,
} from "@/features/reconciliation/api/reconciliation.api"
import {
  useReconciliationRun,
  useRunManualReconciliation,
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

const MAX_VISIBLE_DISCREPANCIES = 50
const MAX_VISIBLE_ANOMALIES = 20

const RULE_PRESENTATIONS: Record<
  string,
  { label: string; description: string }
> = {
  MISSING_CREDIT: {
    label: "Eksik alacak kaydı",
    description:
      "Bank A tarafında tamamlanmış debit bulunmasına rağmen Bank B tarafında tamamlanmış credit veya Bank A tarafında tamamlanmış reversal bulunamadı.",
  },
  AMOUNT_MISMATCH: {
    label: "Tutar uyuşmazlığı",
    description:
      "Transfer tutarı ile banka ledger kayıtlarındaki tutarlar arasında fark tespit edildi.",
  },
  STATUS_MISMATCH: {
    label: "Durum uyuşmazlığı",
    description:
      "Transfer yaşam döngüsü durumu ile banka ledger kayıtları arasında tutarsızlık tespit edildi.",
  },
}

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

const ANOMALY_STATUS_LABELS: Record<string, string> = {
  NOT_RUN: "Çalıştırılmadı",
  NOT_REQUIRED: "Skorlama gerekmiyor",
  COMPLETED: "Tamamlandı",
  PARTIAL: "Kısmen tamamlandı",
  UNAVAILABLE: "Kullanılamıyor",
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

function formatCount(value: number | undefined): string {
  return new Intl.NumberFormat("tr-TR").format(value ?? 0)
}

function formatAnomalyScore(value: number | undefined): string {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—"
  }

  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 3,
    maximumFractionDigits: 6,
  }).format(value)
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

function getAnomalyStatusLabel(status: string | undefined): string {
  if (!status) {
    return "Bilinmiyor"
  }

  return ANOMALY_STATUS_LABELS[status] ?? status
}

function getRulePresentation(ruleCode: string | undefined) {
  if (!ruleCode) {
    return {
      label: "Bilinmeyen kural",
      description: "Kural açıklaması bulunamadı.",
    }
  }

  return (
    RULE_PRESENTATIONS[ruleCode] ?? {
      label: ruleCode,
      description: "Bu kural için ek açıklama bulunmuyor.",
    }
  )
}

function formatDetailKey(key: string): string {
  const labels: Record<string, string> = {
    reason: "Açıklama",
    transferStatus: "Transfer durumu",
    debitAmount: "Debit tutarı",
    expectedTransferAmount: "Beklenen transfer tutarı",
    bankAAmountMismatches: "Bank A tutar farkları",
    bankBAmountMismatches: "Bank B tutar farkları",
    bankAEntries: "Bank A ledger kayıtları",
    bankBEntries: "Bank B ledger kayıtları",
    ledgerTransferIdMismatch: "Transfer kimliği uyuşmazlığı",
  }

  if (labels[key]) {
    return labels[key]
  }

  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (character) => character.toUpperCase())
}

function getRunIdFromError(error: unknown): string | undefined {
  if (!(error instanceof ApiError) || error.status !== 502) {
    return undefined
  }

  const body = error.responseBody

  if (
    typeof body !== "object" ||
    body === null ||
    Array.isArray(body)
  ) {
    return undefined
  }

  const runId = (body as Record<string, unknown>).runId

  return typeof runId === "string" && runId.length > 0
    ? runId
    : undefined
}

function getExecutionErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError || error instanceof ApiTimeoutError) {
    return "Sunucudan kesin yanıt alınamadı. Mutabakat çalışması başlamış veya tamamlanmış olabilir. Endpoint idempotency desteği sunmadığından isteği otomatik olarak tekrarlamadık."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Mutabakat tarihi geçersiz. Seçtiğiniz tarihi kontrol edin."
    }

    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Mutabakat çalıştırmak için OPERATIONS veya ADMIN rolü gerekiyor."
    }

    if (error.status === 502) {
      return "Mutabakat analizi sırasında hata oluştu. Başarısız çalışma kaydı varsa ayrıntıları aşağıda yüklemeyi deneyeceğiz."
    }

    if (error.status >= 500) {
      return "Mutabakat servisi şu anda yanıt veremiyor."
    }
  }

  return "Mutabakat çalıştırılırken beklenmeyen bir hata oluştu."
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

      <p className="break-words text-sm font-medium">
        {value}
      </p>
    </div>
  )
}

function DiscrepancyCard({
  discrepancy,
}: {
  discrepancy: ReconciliationDiscrepancy
}) {
  const presentation = getRulePresentation(discrepancy.ruleCode)

  const detailEntries = Object.entries(discrepancy.details ?? {})
    .sort(([left], [right]) => left.localeCompare(right))

  return (
    <Card className="shadow-none">
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-amber-500/10 text-amber-700">
              <TriangleAlert className="size-5" />
            </div>

            <div className="min-w-0">
              <p className="text-sm font-semibold">
                {presentation.label}
              </p>

              <p className="mt-1 text-xs leading-5 text-muted-foreground">
                {presentation.description}
              </p>
            </div>
          </div>

          <span className="w-fit shrink-0 rounded-full bg-muted px-2.5 py-1 font-mono text-[11px] text-muted-foreground">
            {discrepancy.ruleCode ?? "UNKNOWN"}
          </span>
        </div>

        <div className="grid gap-4 rounded-xl bg-muted/40 p-4 sm:grid-cols-2">
          <DetailValue
            label="Transfer ID"
            value={discrepancy.transferId ?? "—"}
          />

          <DetailValue
            label="Correlation ID"
            value={discrepancy.correlationId ?? "—"}
          />

          <DetailValue
            label="Tespit zamanı"
            value={formatDateTime(discrepancy.detectedAt)}
          />
        </div>

        {detailEntries.length > 0 && (
          <div className="space-y-3 border-t border-border/70 pt-4">
            <p className="text-xs font-semibold text-muted-foreground">
              Kural ayrıntıları
            </p>

            <div className="space-y-3">
              {detailEntries.map(([key, value]) => (
                <div
                  key={key}
                  className="grid gap-1 sm:grid-cols-[180px_minmax(0,1fr)] sm:gap-4"
                >
                  <p className="text-xs text-muted-foreground">
                    {formatDetailKey(key)}
                  </p>

                  <p className="break-words text-xs leading-5">
                    {value}
                  </p>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function AnomalyScoreCard({
  score,
}: {
  score: AnomalyScore
}) {
  return (
    <Card className="shadow-none">
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
          <div className="min-w-0">
            <p className="text-sm font-semibold">
              Anomali olarak işaretlendi
            </p>

            <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
              Transfer: {score.transferId ?? "—"}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="rounded-full bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-800">
              Anomali
            </span>

            <p className="text-lg font-semibold tabular-nums">
              {formatAnomalyScore(score.anomalyScore)}
            </p>
          </div>
        </div>

        <div className="grid gap-3 border-t border-border/70 pt-3 sm:grid-cols-2">
          <DetailValue
            label="Model sürümü"
            value={score.modelVersion ?? "—"}
          />

          <DetailValue
            label="Skor zamanı"
            value={formatDateTime(score.scoredAt)}
          />

          <DetailValue
            label="Correlation ID"
            value={score.correlationId ?? "—"}
          />
        </div>
      </CardContent>
    </Card>
  )
}

function ReconciliationResults({
  run,
}: {
  run: ReconciliationRunDetails
}) {
  const status = getRunStatus(run.status)

  const discrepancies = [...(run.discrepancies ?? [])].sort(
    (left, right) => {
      const leftTime = left.detectedAt
        ? new Date(left.detectedAt).getTime()
        : 0

      const rightTime = right.detectedAt
        ? new Date(right.detectedAt).getTime()
        : 0

      return rightTime - leftTime
    },
  )

  const flaggedAnomalies = [...(run.anomalyScores ?? [])]
    .filter((score) => score.isAnomaly === true)
    .sort(
      (left, right) =>
        (right.anomalyScore ?? -1) - (left.anomalyScore ?? -1),
    )

  const visibleDiscrepancies = discrepancies.slice(
    0,
    MAX_VISIBLE_DISCREPANCIES,
  )

  const visibleAnomalies = flaggedAnomalies.slice(
    0,
    MAX_VISIBLE_ANOMALIES,
  )

  const anomalySummary = run.anomalySummary
  const modelVersions = anomalySummary?.modelVersions ?? []

  return (
    <div className="space-y-6">
      <section className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div className="space-y-1">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            MUTABAKAT SONUCU
          </p>

          <h2 className="text-xl font-semibold tracking-tight">
            Çalışma ayrıntıları
          </h2>
        </div>

        <span
          className={`w-fit rounded-full px-3 py-1.5 text-xs font-semibold ${status.className}`}
        >
          {status.label}
        </span>
      </section>

      {run.errorMessage && (
        <div
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
        >
          {run.errorMessage}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <SummaryCard
          title="İncelenen transfer"
          value={run.transfersChecked ?? 0}
          description="Seçilen iş günündeki transferler"
          icon={Database}
        />

        <SummaryCard
          title="Tespit edilen fark"
          value={run.discrepanciesFound ?? 0}
          description="Kural tabanlı mutabakat farkı"
          icon={ShieldAlert}
        />

        <SummaryCard
          title="Anomali işaretleri"
          value={run.anomaliesFound ?? 0}
          description="ML tarafından anomali işaretlenen kayıt"
          icon={Activity}
        />
      </section>

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="size-5 text-primary" />
            Çalışma bilgileri
          </CardTitle>
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
            label="Çalışma kimliği"
            value={run.runId ?? "—"}
          />

          <DetailValue
            label="Başlangıç"
            value={formatDateTime(run.startedAt)}
          />

          <DetailValue
            label="Bitiş"
            value={formatDateTime(run.finishedAt)}
          />

          <DetailValue
            label="İncelenen dönem"
            value={
              run.periodStart && run.periodEnd
                ? `${formatDateTime(run.periodStart)} – ${formatDateTime(run.periodEnd)}`
                : "—"
            }
          />
        </CardContent>
      </Card>

      <Card className="shadow-none">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Activity className="size-5 text-primary" />
            Makine öğrenmesi özeti
          </CardTitle>

          <CardDescription>
            İstatistiksel anomali skorlarının çalışma sonucu.
            Skorlar kalibre edilmiş olasılık değildir.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <DetailValue
              label="Skorlama durumu"
              value={getAnomalyStatusLabel(anomalySummary?.status)}
            />

            <DetailValue
              label="Skorlama denemesi"
              value={formatCount(anomalySummary?.attemptedCount)}
            />

            <DetailValue
              label="Başarılı skor"
              value={formatCount(anomalySummary?.scoredCount)}
            />

            <DetailValue
              label="Başarısız deneme"
              value={formatCount(anomalySummary?.failedCount)}
            />
          </div>

          {modelVersions.length > 0 && (
            <div className="space-y-2 border-t border-border/70 pt-4">
              <p className="text-xs font-medium text-muted-foreground">
                Kullanılan model sürümleri
              </p>

              <div className="flex flex-wrap gap-2">
                {modelVersions.map((version) => (
                  <span
                    key={version}
                    className="rounded-full bg-muted px-2.5 py-1 font-mono text-xs"
                  >
                    {version}
                  </span>
                ))}
              </div>
            </div>
          )}

          {anomalySummary?.errorMessage && (
            <div
              role="status"
              className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs leading-5 text-muted-foreground"
            >
              {anomalySummary.errorMessage}
            </div>
          )}
        </CardContent>
      </Card>

      <section className="space-y-3">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-end">
          <div>
            <h3 className="text-base font-semibold">
              Kural tabanlı farklar
            </h3>

            <p className="mt-1 text-xs text-muted-foreground">
              Mutabakat kurallarının tespit ettiği tutarsızlıklar.
            </p>
          </div>

          <span className="text-xs text-muted-foreground">
            {formatCount(discrepancies.length)} kayıt
          </span>
        </div>

        {discrepancies.length === 0 ? (
          <Card className="border-dashed shadow-none">
            <CardContent className="flex min-h-36 flex-col items-center justify-center px-6 py-8 text-center">
              <ShieldCheck className="mb-3 size-8 text-primary" />

              <p className="font-medium">
                Kural tabanlı fark bulunmadı
              </p>

              <p className="mt-1 text-xs text-muted-foreground">
                İncelenen transferlerde mutabakat kuralları bir tutarsızlık tespit etmedi.
              </p>
            </CardContent>
          </Card>
        ) : (
          <>
            {visibleDiscrepancies.map((discrepancy, index) => (
              <DiscrepancyCard
                key={
                  discrepancy.id ??
                  `${discrepancy.transferId ?? "discrepancy"}-${discrepancy.ruleCode ?? index}`
                }
                discrepancy={discrepancy}
              />
            ))}

            {discrepancies.length > MAX_VISIBLE_DISCREPANCIES && (
              <p className="text-center text-xs text-muted-foreground">
                İlk {MAX_VISIBLE_DISCREPANCIES} kayıt gösteriliyor.
                Toplam {formatCount(discrepancies.length)} fark bulundu.
              </p>
            )}
          </>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-end">
          <div>
            <h3 className="text-base font-semibold">
              Anomali olarak işaretlenen transferler
            </h3>

            <p className="mt-1 text-xs text-muted-foreground">
              En yüksek anomali skoruna sahip işaretli kayıtlar.
            </p>
          </div>

          <span className="text-xs text-muted-foreground">
            {formatCount(flaggedAnomalies.length)} kayıt
          </span>
        </div>

        {flaggedAnomalies.length === 0 ? (
          <Card className="border-dashed shadow-none">
            <CardContent className="flex min-h-36 flex-col items-center justify-center px-6 py-8 text-center">
              <CheckCircle2 className="mb-3 size-8 text-primary" />

              <p className="font-medium">
                İşaretlenmiş anomali bulunmuyor
              </p>

              <p className="mt-1 text-xs text-muted-foreground">
                Bu çalışmada ML tarafından anomali olarak işaretlenen transfer yok.
              </p>
            </CardContent>
          </Card>
        ) : (
          <>
            {visibleAnomalies.map((score) => (
              <AnomalyScoreCard
                key={`${score.transferId ?? "score"}-${score.scoredAt ?? "time"}`}
                score={score}
              />
            ))}

            {flaggedAnomalies.length > MAX_VISIBLE_ANOMALIES && (
              <p className="text-center text-xs text-muted-foreground">
                En yüksek skorlu {MAX_VISIBLE_ANOMALIES} kayıt gösteriliyor.
                Toplam {formatCount(flaggedAnomalies.length)} anomali işaretlendi.
              </p>
            )}
          </>
        )}
      </section>
    </div>
  )
}

export function ReconciliationCenterPage() {
  const today = getIstanbulDate()

  const [businessDate, setBusinessDate] = useState(today)
  const [runResult, setRunResult] =
    useState<ReconciliationRunDetails | null>(null)
  const [failedRunId, setFailedRunId] = useState<string | undefined>()
  const [executionError, setExecutionError] = useState<unknown>(null)

  const runMutation = useRunManualReconciliation()
  const failedRunQuery = useReconciliationRun(failedRunId)

  const displayedRun = failedRunQuery.data ?? runResult

  async function handleRunReconciliation() {
    if (!businessDate || runMutation.isPending) {
      return
    }

    setRunResult(null)
    setFailedRunId(undefined)
    setExecutionError(null)
    runMutation.reset()

    try {
      const result = await runMutation.mutateAsync({
        businessDate,
      })

      setRunResult(result)
    } catch (error) {
      setExecutionError(error)

      const runId = getRunIdFromError(error)

      if (runId) {
        setFailedRunId(runId)
      }
    }
  }

  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">
          OPERASYON
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Mutabakat Merkezi
        </h1>

        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
          Belirli bir iş günü için transfer kayıtlarını ve banka
          hareketlerini karşılaştırın. Kural tabanlı farkları ve
          makine öğrenmesi anomali sonuçlarını inceleyin.
        </p>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <Card className="shadow-none">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <CalendarClock className="size-5 text-primary" />
              Manuel mutabakat çalıştır
            </CardTitle>

            <CardDescription>
              İncelenecek iş gününü seçerek yeni bir mutabakat
              çalışması başlatın.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <div className="space-y-2">
              <label
                htmlFor="reconciliation-business-date"
                className="text-sm font-medium"
              >
                İş günü
              </label>

              <Input
                id="reconciliation-business-date"
                type="date"
                value={businessDate}
                max={today}
                onChange={(event) => setBusinessDate(event.target.value)}
                disabled={runMutation.isPending}
              />

              <p className="text-xs leading-5 text-muted-foreground">
                Tarih, Europe/Istanbul saat dilimine göre tam takvim
                gününü ifade eder. Varsayılan olarak bugünün tarihi
                seçilidir. Transferlerin oluşturulduğu iş gününü seçin.
              </p>
            </div>

            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => void handleRunReconciliation()}
              disabled={!businessDate || runMutation.isPending}
            >
              {runMutation.isPending ? (
                <>
                  <RefreshCw className="size-4 animate-spin" />
                  Mutabakat çalıştırılıyor...
                </>
              ) : (
                <>
                  <Activity className="size-4" />
                  Mutabakatı Başlat
                </>
              )}
            </Button>
          </CardContent>
        </Card>

        <Card className="shadow-none">
          <CardHeader>
            <CardTitle className="text-base">
              Çalışma hakkında
            </CardTitle>
          </CardHeader>

          <CardContent className="space-y-4 text-xs leading-5 text-muted-foreground">
            <div className="flex items-start gap-2">
              <Database className="mt-0.5 size-4 shrink-0 text-primary" />
              <p>
                Transfer Service, Bank A ve Bank B ledger kayıtları
                birlikte değerlendirilir.
              </p>
            </div>

            <div className="flex items-start gap-2">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary" />
              <p>
                Kural tabanlı mutabakat; eksik credit, tutar ve durum
                uyuşmazlıklarını kontrol eder.
              </p>
            </div>

            <div className="flex items-start gap-2">
              <Activity className="mt-0.5 size-4 shrink-0 text-primary" />
              <p>
                Uygun transferler Isolation Forest modeliyle ayrıca
                skorlanır. Anomali skoru olasılık değildir.
              </p>
            </div>

            <div className="flex items-start gap-2">
              <CalendarClock className="mt-0.5 size-4 shrink-0 text-primary" />
              <p>
                Otomatik mutabakat her gün 03.00'te, Europe/Istanbul
                saatine göre bir önceki takvim gününü işler.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {executionError !== null && (
        <div
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/5 p-4"
        >
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 size-5 shrink-0 text-destructive" />

            <div className="min-w-0">
              <p className="text-sm font-semibold text-destructive">
                Mutabakat çalıştırılamadı
              </p>

              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                {getExecutionErrorMessage(executionError)}
              </p>

              {failedRunId && (
                <p className="mt-2 break-all font-mono text-xs text-muted-foreground">
                  Run ID: {failedRunId}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {failedRunId && failedRunQuery.isPending && (
        <Card className="shadow-none">
          <CardContent className="flex items-center gap-3 py-5">
            <RefreshCw className="size-4 animate-spin text-muted-foreground" />

            <p className="text-sm text-muted-foreground">
              Başarısız mutabakat kaydının ayrıntıları yükleniyor...
            </p>
          </CardContent>
        </Card>
      )}

      {failedRunId && failedRunQuery.isError && (
        <Card className="border-destructive/30 shadow-none">
          <CardContent className="py-5">
            <p className="text-sm font-medium">
              Mutabakat çalışma kaydı alınamadı
            </p>

            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              Çalışma oluşturulmuş olabilir ancak ayrıntı endpoint'inden
              kayıt alınamadı. Run ID: {failedRunId}
            </p>

            <Button
              type="button"
              className="mt-3"
              size="sm"
              variant="outline"
              onClick={() => void failedRunQuery.refetch()}
              disabled={failedRunQuery.isFetching}
            >
              <RefreshCw className="size-4" />
              Ayrıntıları Yeniden Yükle
            </Button>
          </CardContent>
        </Card>
      )}

      {displayedRun && (
        <ReconciliationResults run={displayedRun} />
      )}

      {!displayedRun && executionError === null && (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex min-h-64 flex-col items-center justify-center px-6 py-12 text-center">
            <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Activity className="size-6 text-primary" />
            </div>

            <h2 className="text-base font-semibold">
              Henüz mutabakat çalıştırılmadı
            </h2>

            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              Bir iş günü seçip mutabakatı başlattığınızda incelenen
              transfer sayısı, tespit edilen farklar ve anomali
              skorları burada görüntülenecek.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
