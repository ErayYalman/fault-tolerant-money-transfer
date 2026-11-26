import {
  Activity,
  ArrowLeftRight,
  ArrowUpRight,
  Clock3,
  WalletCards,
} from "lucide-react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

const summaryCards = [
  {
    title: "Toplam Bakiye",
    value: "—",
    description: "Hesap bilgileri yüklendiğinde görüntülenecek.",
    icon: WalletCards,
  },
  {
    title: "Transfer İşlemleri",
    value: "—",
    description: "İşlem geçmişi bağlandığında görüntülenecek.",
    icon: ArrowLeftRight,
  },
  {
    title: "Bekleyen İşlemler",
    value: "—",
    description: "Güncel işlem durumları burada gösterilecek.",
    icon: Clock3,
  },
]

export function OverviewPage() {
  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">
            GENEL BAKIŞ
          </p>

          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Finansal görünümünüz
          </h1>

          <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
            Hesaplarınızı, para transferlerinizi ve işlem hareketlerinizi tek
            merkezden takip edin.
          </p>
        </div>

        <Button
          size="lg"
          className="shrink-0"
          render={<Link to="/transfers/new" />}
        >
          <ArrowLeftRight className="size-4" />
          Yeni Transfer
          <ArrowUpRight className="size-4" />
        </Button>
      </section>

      <section
        aria-label="Finansal özet"
        className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
      >
        {summaryCards.map((item) => {
          const Icon = item.icon

          return (
            <Card key={item.title} className="shadow-none">
              <CardContent className="flex items-start justify-between gap-4">
                <div className="min-w-0 space-y-3">
                  <p className="text-sm font-medium text-muted-foreground">
                    {item.title}
                  </p>

                  <p className="text-3xl font-semibold tracking-tight">
                    {item.value}
                  </p>

                  <p className="text-xs leading-5 text-muted-foreground">
                    {item.description}
                  </p>
                </div>

                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Icon className="size-5" />
                </div>
              </CardContent>
            </Card>
          )
        })}
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(280px,0.9fr)]">
        <Card className="shadow-none">
          <CardHeader>
            <CardTitle>Son İşlemler</CardTitle>
            <CardDescription>
              Hesap ve transfer hareketlerinizin özeti.
            </CardDescription>
          </CardHeader>

          <CardContent className="pb-5">
            <div className="flex min-h-52 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-muted/30 px-6 py-8 text-center">
              <div className="mb-4 flex size-11 items-center justify-center rounded-full bg-background text-muted-foreground shadow-sm">
                <Activity className="size-5" />
              </div>

              <p className="text-sm font-medium">
                İşlem verileri henüz yüklenmedi
              </p>

              <p className="mt-1 max-w-sm text-xs leading-5 text-muted-foreground">
                Canlı hesap ve transfer verileri, kimlik doğrulama ve API
                entegrasyonları tamamlandığında burada görüntülenecek.
              </p>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-none">
          <CardHeader>
            <CardTitle>Hızlı Erişim</CardTitle>
            <CardDescription>
              Sık kullanılan işlem alanlarına geçiş yapın.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-3 pb-5">
            <Link
              to="/transfers/new"
              className="group flex items-center gap-3 rounded-xl border border-border p-3 transition-colors hover:bg-muted/60"
            >
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <ArrowLeftRight className="size-5" />
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">Para Transferi</p>
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
                <p className="text-sm font-medium">Hesaplarım</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Hesap bakiyelerinizi ve hareketlerinizi inceleyin.
                </p>
              </div>

              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </Link>
          </CardContent>
        </Card>
      </section>
    </div>
  )
}