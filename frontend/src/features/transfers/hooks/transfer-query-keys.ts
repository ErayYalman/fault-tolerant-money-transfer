export const transferQueryKeys = {
  all: ["transfers"] as const,

  lists: () => ["transfers", "list"] as const,

  list: (subject: string, page: number, size: number) =>
    ["transfers", "list", subject, page, size] as const,

  detail: (subject: string, transferId: string) =>
    ["transfers", "detail", subject, transferId] as const,
}
