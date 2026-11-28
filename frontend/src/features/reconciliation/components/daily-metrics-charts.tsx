import type { LucideIcon } from "lucide-react"
import {
  Activity,
  ShieldAlert,
} from "lucide-react"

import type {
  ReconciliationDailySummary,
} from "@/features/reconciliation/api/reconciliation.api"

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

type DailyMetricItem =
  NonNullable<ReconciliationDailySummary["daily"]>[number]

type DailyMetricKey =
  | "transfersChecked"
  | "discrepanciesFound"
  | "anomaliesFound"

type ChartDataItem = DailyMetricItem & {
  businessDate: string
}

interface ChartSeries {
  key: DailyMetricKey
  label: string
  color: string
}

interface TrendChartCardProps {
  title: string
  description: string
  icon: LucideIcon
  items: ChartDataItem[]
  fromDate: string
  toDate: string
  series: ChartSeries[]
  showArea?: boolean
}

interface PlotPoint {
  businessDate: string
  x: number
  y: number
  value: number
}

const WIDTH = 760
const HEIGHT = 270

const MARGIN = {
  top: 18,
  right: 18,
  bottom: 44,
  left: 58,
}

const PLOT_WIDTH =
  WIDTH - MARGIN.left - MARGIN.right

const PLOT_HEIGHT =
  HEIGHT - MARGIN.top - MARGIN.bottom

const PLOT_BOTTOM =
  MARGIN.top + PLOT_HEIGHT

const DAY_IN_MS = 86_400_000

const TRANSFER_SERIES: ChartSeries[] = [
  {
    key: "transfersChecked",
    label: "İncelenen transfer",
    color: "var(--chart-1)",
  },
]

const FINDING_SERIES: ChartSeries[] = [
  {
    key: "discrepanciesFound",
    label: "Mutabakat farkı",
    color: "var(--chart-3)",
  },
  {
    key: "anomaliesFound",
    label: "Anomali",
    color: "var(--chart-4)",
  },
]

function parseBusinessDate(value: string): number {
  const [year, month, day] = value.split("-").map(Number)

  return Date.UTC(year, month - 1, day)
}

function formatCount(value: number): string {
  return new Intl.NumberFormat("tr-TR", {
    maximumFractionDigits: 0,
  }).format(value)
}

function formatChartDate(
  value: string,
  includeYear = false,
): string {
  const [year, month, day] = value.split("-")

  return includeYear
    ? `${day}.${month}.${year.slice(2)}`
    : `${day}.${month}`
}

function formatFullDate(value: string): string {
  return formatChartDate(value, true)
}

function getDateFromTimestamp(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10)
}

function createYAxisScale(maxValue: number) {
  const target = Math.max(1, maxValue)
  const roughStep = target / 4

  const magnitude = Math.pow(
    10,
    Math.floor(Math.log10(roughStep)),
  )

  const normalized = roughStep / magnitude

  const multiplier =
    normalized <= 1
      ? 1
      : normalized <= 2
        ? 2
        : normalized <= 3
          ? 3
          : normalized <= 5
            ? 5
            : 10

  const step = Math.max(1, multiplier * magnitude)
  const max = step * 4

  return {
    step,
    max,
    ticks: Array.from(
      { length: 5 },
      (_, index) => index * step,
    ),
  }
}

function createDateTicks(
  fromDate: string,
  toDate: string,
) {
  const start = parseBusinessDate(fromDate)
  const end = parseBusinessDate(toDate)

  if (start === end) {
    return [
      {
        x: MARGIN.left + PLOT_WIDTH / 2,
        label: formatChartDate(fromDate),
      },
    ]
  }

  const includeYear =
    end - start >= 90 * DAY_IN_MS

  const ticks: {
    x: number
    label: string
  }[] = []

  for (let index = 0; index <= 4; index += 1) {
    const ratio = index / 4

    const timestamp =
      start + Math.round((end - start) * ratio)

    const date = getDateFromTimestamp(timestamp)
    const label = formatChartDate(date, includeYear)

    if (ticks.some((tick) => tick.label === label)) {
      continue
    }

    ticks.push({
      x: MARGIN.left + ratio * PLOT_WIDTH,
      label,
    })
  }

  return ticks
}

function createSegments(points: PlotPoint[]): PlotPoint[][] {
  const segments: PlotPoint[][] = []

  for (const point of points) {
    const currentSegment = segments[segments.length - 1]
    const previousPoint =
      currentSegment?.[currentSegment.length - 1]

    if (
      !previousPoint ||
      parseBusinessDate(point.businessDate) -
        parseBusinessDate(previousPoint.businessDate) >
        DAY_IN_MS
    ) {
      segments.push([point])
      continue
    }

    currentSegment.push(point)
  }

  return segments
}

function TrendChartCard({
  title,
  description,
  icon: Icon,
  items,
  fromDate,
  toDate,
  series,
  showArea = false,
}: TrendChartCardProps) {
  const start = parseBusinessDate(fromDate)
  const end = parseBusinessDate(toDate)

  const maxValue = Math.max(
    0,
    ...items.flatMap((item) =>
      series.map((itemSeries) =>
        Math.max(0, item[itemSeries.key] ?? 0),
      ),
    ),
  )

  const scale = createYAxisScale(maxValue)

  const yTicks = scale.ticks.map((value) => ({
    value,
    y:
      PLOT_BOTTOM -
      (value / scale.max) * PLOT_HEIGHT,
  }))

  const xTicks = createDateTicks(fromDate, toDate)

  function getPoints(key: DailyMetricKey): PlotPoint[] {
    return items.map((item) => {
      const timestamp = parseBusinessDate(item.businessDate)

      const ratio =
        start === end
          ? 0.5
          : (timestamp - start) / (end - start)

      const value = Math.max(0, item[key] ?? 0)

      return {
        businessDate: item.businessDate,
        x: MARGIN.left + ratio * PLOT_WIDTH,
        y:
          PLOT_BOTTOM -
          (value / scale.max) * PLOT_HEIGHT,
        value,
      }
    })
  }

  return (
    <Card className="min-w-0 shadow-none">
      <CardHeader className="space-y-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className="size-5 text-primary" />
          {title}
        </CardTitle>

        <CardDescription className="leading-5">
          {description}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="w-full">
          <svg
            viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
            className="h-auto max-h-64 w-full overflow-visible"
            role="img"
            aria-label={title}
          >
            <title>{title}</title>

            <desc>
              {description} Tarihler iş günü sırasına göre
              gösterilir.
            </desc>

            {yTicks.map((tick) => (
              <g key={tick.value}>
                <line
                  x1={MARGIN.left}
                  x2={WIDTH - MARGIN.right}
                  y1={tick.y}
                  y2={tick.y}
                  stroke="var(--border)"
                  strokeDasharray="4 4"
                />

                <text
                  x={MARGIN.left - 12}
                  y={tick.y + 4}
                  textAnchor="end"
                  fill="var(--muted-foreground)"
                  fontSize="11"
                >
                  {formatCount(tick.value)}
                </text>
              </g>
            ))}

            {xTicks.map((tick, index) => (
              <text
                key={`${tick.label}-${index}`}
                x={tick.x}
                y={HEIGHT - 12}
                textAnchor={
                  index === 0
                    ? "start"
                    : index === xTicks.length - 1
                      ? "end"
                      : "middle"
                }
                fill="var(--muted-foreground)"
                fontSize="11"
              >
                {tick.label}
              </text>
            ))}

            {series.map((itemSeries) => {
              const points = getPoints(itemSeries.key)
              const segments = createSegments(points)

              const markerRadius =
                points.length > 120
                  ? 1.8
                  : points.length > 60
                    ? 2.3
                    : 3

              return (
                <g key={itemSeries.key}>
                  {segments.map((segment, segmentIndex) => {
                    const linePath = segment
                      .map(
                        (point, index) =>
                          `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`,
                      )
                      .join(" ")

                    const firstPoint = segment[0]
                    const lastPoint =
                      segment[segment.length - 1]

                    const areaPath =
                      showArea && segment.length > 1
                        ? [
                            linePath,
                            `L ${lastPoint.x} ${PLOT_BOTTOM}`,
                            `L ${firstPoint.x} ${PLOT_BOTTOM}`,
                            "Z",
                          ].join(" ")
                        : undefined

                    return (
                      <g
                        key={`${itemSeries.key}-${segmentIndex}`}
                      >
                        {areaPath && (
                          <path
                            d={areaPath}
                            fill={itemSeries.color}
                            opacity="0.10"
                          />
                        )}

                        {segment.length > 1 && (
                          <path
                            d={linePath}
                            fill="none"
                            stroke={itemSeries.color}
                            strokeWidth="2.5"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        )}
                      </g>
                    )
                  })}

                  {points.map((point) => (
                    <circle
                      key={`${itemSeries.key}-${point.businessDate}`}
                      cx={point.x}
                      cy={point.y}
                      r={markerRadius}
                      fill={itemSeries.color}
                      stroke="var(--card)"
                      strokeWidth="1.5"
                    >
                      <title>
                        {formatFullDate(point.businessDate)}
                        {" · "}
                        {itemSeries.label}
                        {": "}
                        {formatCount(point.value)}
                      </title>
                    </circle>
                  ))}
                </g>
              )
            })}
          </svg>
        </div>

        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
          {series.map((itemSeries) => (
            <div
              key={itemSeries.key}
              className="flex items-center gap-2 text-xs text-muted-foreground"
            >
              <span
                className="size-2.5 rounded-full"
                style={{
                  backgroundColor: itemSeries.color,
                }}
              />

              {itemSeries.label}
            </div>
          ))}
        </div>

        <p className="text-[11px] leading-5 text-muted-foreground">
          Her noktanın üzerine gelerek ilgili günün değerini
          görebilirsiniz. Kayıt bulunmayan tarihler boşluk olarak
          bırakılır.
        </p>
      </CardContent>
    </Card>
  )
}

interface DailyMetricsChartsProps {
  items: DailyMetricItem[]
  fromDate: string
  toDate: string
}

export function DailyMetricsCharts({
  items,
  fromDate,
  toDate,
}: DailyMetricsChartsProps) {
  const datedItems = items
    .filter(
      (
        item,
      ): item is DailyMetricItem & { businessDate: string } => {
        if (!item.businessDate) {
          return false
        }

        const timestamp = parseBusinessDate(item.businessDate)

        return (
          Number.isFinite(timestamp) &&
          item.businessDate >= fromDate &&
          item.businessDate <= toDate
        )
      },
    )
    .sort((left, right) =>
      left.businessDate.localeCompare(right.businessDate),
    )

  if (datedItems.length === 0) {
    return null
  }

  return (
    <section className="grid gap-4 xl:grid-cols-2">
      <TrendChartCard
        title="Günlük transfer hacmi"
        description="Her iş günü mutabakat kapsamına alınan transfer sayısı."
        icon={Activity}
        items={datedItems}
        fromDate={fromDate}
        toDate={toDate}
        series={TRANSFER_SERIES}
        showArea
      />

      <TrendChartCard
        title="Günlük tespitler"
        description="Mutabakat farkları ile ML modeli tarafından anomali olarak işaretlenen transferlerin günlük dağılımı."
        icon={ShieldAlert}
        items={datedItems}
        fromDate={fromDate}
        toDate={toDate}
        series={FINDING_SERIES}
      />
    </section>
  )
}
