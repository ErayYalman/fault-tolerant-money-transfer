import { ArrowLeft, Construction } from "lucide-react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
} from "@/components/ui/card"

interface FeaturePlaceholderPageProps {
  title: string
  description: string
}

export function FeaturePlaceholderPage({
  title,
  description,
}: FeaturePlaceholderPageProps) {
  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">
          MONEY TRANSFER PLATFORM
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          {title}
        </h1>

        <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
          {description}
        </p>
      </section>

      <Card className="border-dashed shadow-none">
        <CardContent className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
          <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Construction className="size-6" />
          </div>

          <h2 className="text-base font-semibold">
            Bu ekranın geliştirmesi sırada
          </h2>

          <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
            Bu bölümün işlevleri, ilgili backend servisleri ve kimlik
            doğrulama entegrasyonları tamamlandıkça eklenecek.
          </p>

          <Button
            className="mt-6"
            variant="outline"
            render={<Link to="/" />}
          >
            <ArrowLeft className="size-4" />
            Genel Bakışa Dön
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}