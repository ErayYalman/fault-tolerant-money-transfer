import Keycloak from "keycloak-js"

import { APP_ROLES, type AppRole, type AuthSnapshot } from "./auth.types"

let keycloakInstance: Keycloak | null = null
let initializationPromise: Promise<boolean> | null = null

const listeners = new Set<() => void>()

let authSnapshot: AuthSnapshot = {
  isAuthenticated: false,
  user: null,
  roles: [],
  tokenRefreshError: false,
}

function publishAuthSnapshot(
  client: Keycloak,
  tokenRefreshError = false,
): void {
  const claims = client.tokenParsed as Record<string, unknown> | undefined

  const authenticated = Boolean(client.authenticated && client.token)

  const subject =
    typeof claims?.sub === "string"
      ? claims.sub
      : client.subject ?? ""

  const roles = (client.realmAccess?.roles ?? []).filter(
    (role): role is AppRole =>
      APP_ROLES.includes(role as AppRole),
  )

  authSnapshot = {
    isAuthenticated: authenticated,
    user: authenticated
      ? {
          id: subject,
          username:
            typeof claims?.preferred_username === "string"
              ? claims.preferred_username
              : undefined,
          name:
            typeof claims?.name === "string"
              ? claims.name
              : undefined,
          email:
            typeof claims?.email === "string"
              ? claims.email
              : undefined,
        }
      : null,
    roles,
    tokenRefreshError,
  }

  listeners.forEach((listener) => listener())
}

export function subscribeAuthState(listener: () => void): () => void {
  listeners.add(listener)

  return () => {
    listeners.delete(listener)
  }
}

export function getAuthSnapshot(): AuthSnapshot {
  return authSnapshot
}

export function initializeKeycloak(): Promise<boolean> {
  if (initializationPromise) {
    return initializationPromise
  }

  initializationPromise = (async () => {
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

    // Event callback'leri init öncesinde tanımlanır.
    client.onAuthSuccess = () => {
      publishAuthSnapshot(client)
    }

    client.onAuthRefreshSuccess = () => {
      publishAuthSnapshot(client)
    }

    client.onAuthRefreshError = () => {
      publishAuthSnapshot(client, true)
    }

    client.onAuthLogout = () => {
      publishAuthSnapshot(client)
    }

    client.onTokenExpired = () => {
      void refreshAccessToken(30).catch(() => undefined)
    }

    const authenticated = await client.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      scope: "openid profile email",
    })

    publishAuthSnapshot(client)

    return authenticated
  })()

  return initializationPromise
}

export function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    throw new Error("Keycloak henüz başlatılmadı.")
  }

  return keycloakInstance
}

export async function refreshAccessToken(
  minValidity = 30,
): Promise<string> {
  const client = getKeycloak()

  if (!client.authenticated) {
    throw new Error("Kullanıcı oturumu bulunamadı.")
  }

  try {
    await client.updateToken(minValidity)

    if (!client.token) {
      throw new Error("Geçerli bir access token alınamadı.")
    }

    publishAuthSnapshot(client)

    return client.token
  } catch (error) {
    publishAuthSnapshot(client, true)
    throw error
  }
}

export function loginWithKeycloak(): Promise<void> {
  return getKeycloak().login({
    redirectUri: window.location.origin,
  })
}

export function logoutFromKeycloak(): Promise<void> {
  return getKeycloak().logout({
    redirectUri: window.location.origin,
  })
}
