export function buildQueryString<T extends object>(query: T): string {
  const params = new URLSearchParams()

  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) {
      continue
    }

    params.set(key, String(value))
  }

  const search = params.toString()

  return search ? `?${search}` : ""
}
