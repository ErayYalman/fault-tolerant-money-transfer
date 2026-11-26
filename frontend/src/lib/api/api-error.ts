interface ApiProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  message?: string
  errors?: Record<string, string>
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    !Array.isArray(value)
  )
}

function getString(
  record: Record<string, unknown>,
  key: string,
): string | undefined {
  const value = record[key]

  return typeof value === "string" && value.length > 0
    ? value
    : undefined
}

function getFieldErrors(
  value: unknown,
): Record<string, string> | undefined {
  if (!isRecord(value)) {
    return undefined
  }

  const entries = Object.entries(value).filter(
    (entry): entry is [string, string] =>
      typeof entry[1] === "string",
  )

  return entries.length > 0
    ? Object.fromEntries(entries)
    : undefined
}

export class ApiError extends Error {
  readonly status: number
  readonly type?: string
  readonly title?: string
  readonly detail?: string
  readonly instance?: string
  readonly fieldErrors?: Record<string, string>
  readonly responseBody: unknown

  constructor(
    status: number,
    statusText: string,
    responseBody: unknown,
  ) {
    const record = isRecord(responseBody) ? responseBody : {}

    const title = getString(record, "title")
    const detail =
      getString(record, "detail") ??
      getString(record, "message")

    const message =
      detail ??
      title ??
      statusText ??
      `API isteği başarısız oldu (${status}).`

    super(message)

    this.name = "ApiError"
    this.status = status
    this.type = getString(record, "type")
    this.title = title
    this.detail = detail
    this.instance = getString(record, "instance")
    this.fieldErrors = getFieldErrors(record.errors)
    this.responseBody = responseBody
  }
}

export class ApiTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`API isteği ${timeoutMs} ms içerisinde yanıt vermedi.`)
    this.name = "ApiTimeoutError"
  }
}

export class ApiNetworkError extends Error {
  readonly originalError: unknown

  constructor(originalError: unknown) {
    super("API servisine ulaşılamadı.")
    this.name = "ApiNetworkError"
    this.originalError = originalError
  }
}

export class ApiAuthenticationError extends Error {
  readonly originalError: unknown

  constructor(originalError: unknown) {
    super("API isteği için geçerli bir kullanıcı oturumu alınamadı.")
    this.name = "ApiAuthenticationError"
    this.originalError = originalError
  }
}

export type { ApiProblemDetails }