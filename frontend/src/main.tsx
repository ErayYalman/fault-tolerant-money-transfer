import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { QueryClientProvider } from "@tanstack/react-query"
import { BrowserRouter } from "react-router"

import { AuthInitializationError } from "@/app/AuthInitializationError"
import { AuthProvider } from "@/app/providers/AuthProvider"
import { queryClient } from "@/app/providers/query-client"
import App from "@/app/App"
import { initializeKeycloak } from "@/lib/auth/keycloak"
import "@/index.css"

const rootElement = document.getElementById("root")

if (!rootElement) {
  throw new Error("Root element bulunamadı.")
}

const root = createRoot(rootElement)

async function bootstrap() {
  try {
    await initializeKeycloak()

    root.render(
      <StrictMode>
        <BrowserRouter>
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <App />
            </AuthProvider>
          </QueryClientProvider>
        </BrowserRouter>
      </StrictMode>,
    )
  } catch (error) {
    console.error("Keycloak initialization failed:", error)

    root.render(
      <StrictMode>
        <AuthInitializationError />
      </StrictMode>,
    )
  }
}

void bootstrap()
