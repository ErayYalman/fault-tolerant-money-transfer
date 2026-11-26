import Keycloak from "keycloak-js"

let keycloakInstance: Keycloak | null = null

export async function initializeKeycloak(): Promise<void> {
  const url = import.meta.env.VITE_KEYCLOAK_URL
  const realm = import.meta.env.VITE_KEYCLOAK_REALM
  const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID

  if (!url || !realm || !clientId) {
    throw new Error("Keycloak ortam değişkenleri eksik.")
  }

  const client = new Keycloak({
    url,
    realm,
    clientId,
  })

  keycloakInstance = client

  const authenticated = await client.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    scope: "openid profile email",
  })

  if (!authenticated) {
    throw new Error("Keycloak kimlik doğrulaması tamamlanamadı.")
  }
}

export function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    throw new Error("Keycloak henüz başlatılmadı.")
  }

  return keycloakInstance
}