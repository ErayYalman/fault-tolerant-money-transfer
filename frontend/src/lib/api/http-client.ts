import { refreshAccessToken } from "@/lib/auth/keycloak"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "./api-error"

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
  body?: unknown
  headers?: HeadersInit
  signal?: AbortSignal
  timeoutMs?: number
}

const DEFAULT_TIMEOUT_MS = 10_000

function resolveApiUrl(path: string): string {
  if (
    !path.startsWith("/api/") ||
    path.startsWith("//")
  ) {
    throw new Error("API yolu /api/ ile başlamalıdır.")
  }

  const baseUrlValue = import.meta.env.VITE_API_BASE_URL

  if (!baseUrlValue) {
    throw new Error("VITE_API_BASE_URL ortam değişkeni eksik.")
  }

  let baseUrl: URL

  try {
    baseUrl = new URL(baseUrlValue)
  } catch {
    throw new Error("VITE_API_BASE_URL geçerli bir URL değil.")
  }

  if (
    baseUrl.protocol !== "http:" &&
    baseUrl.protocol !== "https:"
  ) {
    throw new Error("API adresi HTTP veya HTTPS kullanmalıdır.")
  }

  if (baseUrl.pathname !== "/" || baseUrl.search || baseUrl.hash) {
    throw new Error("API base URL yalnızca servis adresini içermelidir.")
  }

  const targetUrl = new URL(path, baseUrl)

  if (
    targetUrl.origin !== baseUrl.origin ||
    !targetUrl.pathname.startsWith("/api/")
  ) {
    throw new Error("İzin verilmeyen API adresi.")
  }

  return targetUrl.toString()
}

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text()

  if (!text) {
    return undefined
  }

  const contentType = (
    response.headers.get("content-type") ?? ""
  ).toLowerCase()

  if (contentType.includes("json")) {
    try {
      return JSON.parse(text) as unknown
    } catch {
      return text
    }
  }

  return text
}

export async function apiRequest<TResponse>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<TResponse> {
  const {
    method = "GET",
    body,
    headers: customHeaders,
    signal: externalSignal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
  } = options

  const url = resolveApiUrl(path)

  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error("API timeout değeri pozitif olmalıdır.")
  }

  if (
    body !== undefined &&
    (method === "GET" || method === "DELETE")
  ) {
    throw new Error(`${method} isteğinde request body kullanılamaz.`)
  }

  const headers = new Headers(customHeaders)

  headers.set(
    "Accept",
    "application/json, application/problem+json",
  )

  if (body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }

  let accessToken: string

  try {
    accessToken = await refreshAccessToken(30)
  } catch (error) {
    throw new ApiAuthenticationError(error)
  }

  headers.set("Authorization", `Bearer ${accessToken}`)

  const controller = new AbortController()

  const abortFromCaller = () => {
    controller.abort(externalSignal?.reason)
  }

  if (externalSignal?.aborted) {
    abortFromCaller()
  } else {
    externalSignal?.addEventListener(
      "abort",
      abortFromCaller,
      { once: true },
    )
  }

  const timeoutId = window.setTimeout(() => {
    controller.abort()
  }, timeoutMs)

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
    })

    const responseBody = await readResponseBody(response)

    if (!response.ok) {
      throw new ApiError(
        response.status,
        response.statusText,
        responseBody,
      )
    }

    return responseBody as TResponse
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    if (externalSignal?.aborted) {
      throw error
    }

    if (controller.signal.aborted) {
      throw new ApiTimeoutError(timeoutMs)
    }

    if (error instanceof TypeError) {
      throw new ApiNetworkError(error)
    }

    throw error
  } finally {
    window.clearTimeout(timeoutId)

    externalSignal?.removeEventListener(
      "abort",
      abortFromCaller,
    )
  }
}

export function apiGet<TResponse>(
  path: string,
  options: Omit<ApiRequestOptions, "method" | "body"> = {},
): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    ...options,
    method: "GET",
  })
}

export function apiPost<TBody, TResponse>(
  path: string,
  body: TBody,
  options: Omit<ApiRequestOptions, "method" | "body"> = {},
): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    ...options,
    method: "POST",
    body,
  })
}