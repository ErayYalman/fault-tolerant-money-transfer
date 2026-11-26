import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter } from "react-router"

import { AuthInitializationError } from "@/app/AuthInitializationError"
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
          <App />
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