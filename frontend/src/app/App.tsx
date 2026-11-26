import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

function App() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10 text-foreground">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <CardTitle>Money Transfer Platform</CardTitle>
          <CardDescription>
            Dağıtık para transferi ve otomatik mutabakat platformu.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <p className="text-sm leading-6 text-muted-foreground">
            React, TypeScript, Tailwind CSS ve shadcn/ui altyapısı hazır.
          </p>
        </CardContent>

        <CardFooter className="justify-end">
          <Button disabled>Arayüz altyapısı hazır</Button>
        </CardFooter>
      </Card>
    </main>
  )
}

export default App