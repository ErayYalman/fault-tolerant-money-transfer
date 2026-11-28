import {
  Activity,
  ArrowUpRight,
  ServerCog,
  ShieldAlert,
  Users,
  WalletCards,
  type LucideIcon,
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

interface AdminModuleCardProps {
  title: string
  description: string
  icon: LucideIcon
  href?: string
  actionLabel?: string
}

function AdminModuleCard({
  title,
  description,
  icon: Icon,
  href,
  actionLabel,
}: AdminModuleCardProps) {
  const isAvailable = Boolean(href && actionLabel)

  return (
    <Card className="shadow-none">
      <CardHeader className="space-y-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Icon className="size-5" />
          </div>

          <span
            className={
              isAvailable
                ? "rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary"
                : "rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground"
            }
          >
            {isAvailable ? "Kullanılabilir" : "Planlandı"}
          </span>
        </div>

        <CardTitle className="text-base">{title}</CardTitle>

        <CardDescription className="leading-6">
          {description}
        </CardDescription>
      </CardHeader>

      <CardContent>
        {href && actionLabel ? (
          <Button
            className="w-full"
            variant="outline"
            render={<Link to={href} />}
          >
            {actionLabel}
            <ArrowUpRight className="size-4" />
          </Button>
        ) : (
          <p className="text-xs leading-5 text-muted-foreground">
            İlgili backend API ve yetkilendirme adımları
            tamamlandığında bu modül etkinleştirilecek.
          </p>
        )}
      </CardContent>
    </Card>
  )
}

export function AdminOverviewPage() {
  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">
          YÖNETİM
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Yönetim Paneli
        </h1>

        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
          Platformun operasyonel süreçlerini takip edin ve yönetim
          modüllerine bu merkezden erişin.
        </p>
      </section>

      <Card className="border-primary/20 bg-primary/[0.03] shadow-none">
        <CardContent className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Activity className="size-5" />
          </div>

          <div>
            <p className="text-sm font-semibold">
              Yönetici çalışma alanı
            </p>

            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              Yönetici hesabı mevcut operasyon ekranlarına da erişebilir.
              Kullanıcı, hesap ve sistem yönetimi modülleri ilgili
              backend geliştirmeleri tamamlandıkça burada açılacaktır.
            </p>
          </div>
        </CardContent>
      </Card>

      <section className="space-y-3">
        <div>
          <h2 className="text-base font-semibold">
            Operasyonel Kontrol
          </h2>

          <p className="mt-1 text-xs text-muted-foreground">
            Mevcut mutabakat ve anomali süreçlerine erişin.
          </p>
        </div>

        <div className="grid gap-4 xl:grid-cols-2">
          <AdminModuleCard
            title="Mutabakat Merkezi"
            description="Transfer kayıtlarını ve banka ledger hareketlerini karşılaştırın. Mutabakat çalışmaları oluşturun ve sonuçlarını inceleyin."
            icon={Activity}
            href="/operations/reconciliation"
            actionLabel="Mutabakat Merkezini Aç"
          />

          <AdminModuleCard
            title="Anomali İzleme"
            description="Isolation Forest modeli tarafından skorlanan transferleri ve mutabakat sırasında tespit edilen anomalileri inceleyin."
            icon={ShieldAlert}
            href="/operations/anomalies"
            actionLabel="Anomali İzlemeyi Aç"
          />
        </div>
      </section>

      <section className="space-y-3">
        <div>
          <h2 className="text-base font-semibold">
            Yönetim Modülleri
          </h2>

          <p className="mt-1 text-xs text-muted-foreground">
            Bir sonraki geliştirme adımlarında etkinleştirilecek alanlar.
          </p>
        </div>

        <div className="grid gap-4 xl:grid-cols-3">
          <AdminModuleCard
            title="Kullanıcı ve Yetki Yönetimi"
            description="Kullanıcıları, uygulama rollerini ve erişim yetkilerini yönetin."
            icon={Users}
          />

          <AdminModuleCard
            title="Banka Hesabı Yönetimi"
            description="Banka hesaplarını, hesap sahipliklerini ve hesap durumlarını yönetin."
            icon={WalletCards}
          />

          <AdminModuleCard
            title="Sistem Görünümü"
            description="Servislerin, Kafka'nın, Redis'in ve günlük mutabakat süreçlerinin durumunu takip edin."
            icon={ServerCog}
          />
        </div>
      </section>
    </div>
  )
}