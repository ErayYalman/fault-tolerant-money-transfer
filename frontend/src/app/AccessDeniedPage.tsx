import { ArrowLeft, ShieldAlert } from "lucide-react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

export function AccessDeniedPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Card className="w-full max-w-md shadow-none">
        <CardHeader className="items-center text-center">
          <div className="mb-2 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
            <ShieldAlert className="size-6" />
          </div>

          <CardTitle>Erişim yetkiniz bulunmuyor</CardTitle>

          <CardDescription className="leading-6">
            Bu sayfayı görüntülemek için hesabınıza tanımlanmış yetkiler
            yeterli değil.
          </CardDescription>
        </CardHeader>

        <CardContent className="flex justify-center">
          <Button variant="outline" render={<Link to="/" />}>
            <ArrowLeft className="size-4" />
            Genel Bakışa Dön
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
