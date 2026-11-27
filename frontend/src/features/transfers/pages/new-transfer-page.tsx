import { useState } from "react"
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowLeftRight,
  CheckCircle2,
  RefreshCw,
  WalletCards,
} from "lucide-react"
import { Link } from "react-router"

import {
  ApiAuthenticationError,
  ApiError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/api-error"

import { useAccounts } from "@/features/accounts/hooks/useAccounts"
import type { AccountSummary } from "@/features/accounts/api/accounts.api"
import { useRecipientLookup } from "@/features/transfers/hooks/useRecipientLookup"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"

const ACCOUNT_PAGE_SIZE = 100

function formatMoney(value: number | undefined): string {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—"
  }

  return new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency: "TRY",
  }).format(value)
}

function getAccountLabel(account: AccountSummary): string {
  return account.accountNumber ?? account.accountId ?? "Hesap"
}

function getRecipientErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yapmanız gerekiyor."
  }

  if (error instanceof ApiNetworkError) {
    return "Alıcı doğrulama servisine ulaşılamadı."
  }

  if (error instanceof ApiTimeoutError) {
    return "Alıcı doğrulama isteği zamanında yanıt vermedi."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Girilen hesap numarası geçersiz."
    }

    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapın."
    }

    if (error.status === 403) {
      return "Alıcı doğrulama işlemi için yetkiniz bulunmuyor."
    }

    if (error.status === 404) {
      return "Bu hesap numarası Bank B üzerinde bulunamadı."
    }

    if (error.status === 503) {
      return "Alıcı banka şu anda doğrulama isteğine yanıt veremiyor."
    }

    if (error.status >= 500) {
      return "Alıcı doğrulama servisi şu anda kullanılamıyor."
    }
  }

  return "Alıcı doğrulanırken beklenmeyen bir hata oluştu."
}

function isEligibleSourceAccount(
  account: AccountSummary,
): boolean {
  return (
    Boolean(account.accountId) &&
    account.status === "ACTIVE" &&
    account.currency?.toUpperCase() === "TRY"
  )
}

function AccountLoadingState() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-5 w-48" />
    </div>
  )
}

function AccountSelect({
  accounts,
  value,
  onChange,
}: {
  accounts: AccountSummary[]
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className="space-y-2">
      <label
        htmlFor="source-account"
        className="text-sm font-medium"
      >
        Gönderen hesap
      </label>

      <select
        id="source-account"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 w-full rounded-lg border border-input bg-transparent px-3 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <option value="" disabled>
          Kaynak hesap seçin
        </option>

        {accounts.map((account) => (
          <option
            key={account.accountId}
            value={account.accountId}
          >
            {getAccountLabel(account)} · {formatMoney(account.balance)}
          </option>
        ))}
      </select>
    </div>
  )
}

function RecipientVerificationStatus({
  isFetching,
  isRequested,
  isError,
  error,
  isSuccess,
  isActive,
  currencyMatches,
}: {
  isFetching: boolean
  isRequested: boolean
  isError: boolean
  error: unknown
  isSuccess: boolean
  isActive: boolean
  currencyMatches: boolean
}) {
  if (!isRequested) {
    return (
      <p className="text-xs text-muted-foreground">
        Devam etmeden önce alıcı hesabını doğrulamanız gerekiyor.
      </p>
    )
  }

  if (isFetching) {
    return (
      <p
        role="status"
        className="flex items-center gap-2 text-sm text-muted-foreground"
      >
        <RefreshCw className="size-4 animate-spin" />
        Alıcı hesabı doğrulanıyor...
      </p>
    )
  }

  if (isError) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 text-sm text-destructive"
      >
        <AlertCircle className="mt-0.5 size-4 shrink-0" />
        {getRecipientErrorMessage(error)}
      </p>
    )
  }

  if (!isSuccess) {
    return null
  }

  if (!isActive) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 text-sm text-destructive"
      >
        <AlertCircle className="mt-0.5 size-4 shrink-0" />
        Alıcı hesabı aktif değil. Bu hesaba transfer yapılamaz.
      </p>
    )
  }

  if (!currencyMatches) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 text-sm text-destructive"
      >
        <AlertCircle className="mt-0.5 size-4 shrink-0" />
        Alıcı hesabının para birimi kaynak hesapla eşleşmiyor.
      </p>
    )
  }

  return (
    <p
      role="status"
      className="flex items-center gap-2 text-sm text-primary"
    >
      <CheckCircle2 className="size-4" />
      Alıcı hesabı doğrulandı ve transfer almaya uygun.
    </p>
  )
}

export function NewTransferPage() {
  const [sourceAccountId, setSourceAccountId] = useState("")
  const [recipientAccountNumber, setRecipientAccountNumber] = useState("")
  const [verificationRequested, setVerificationRequested] = useState(false)
  const [amount, setAmount] = useState("")

  const accountsQuery = useAccounts(0, ACCOUNT_PAGE_SIZE)

  const accounts = accountsQuery.data?.content ?? []
  const eligibleAccounts = accounts.filter(isEligibleSourceAccount)

  const selectedAccount = eligibleAccounts.find(
    (account) => account.accountId === sourceAccountId,
  )

  const normalizedRecipientNumber = recipientAccountNumber.trim()

  const isRecipientInputValid =
    normalizedRecipientNumber.length > 0 &&
    normalizedRecipientNumber.length <= 50

  const recipientQuery = useRecipientLookup(
    normalizedRecipientNumber,
    verificationRequested,
  )

  const recipient = recipientQuery.data

  const isRecipientActive =
    recipient?.status?.toUpperCase() === "ACTIVE"

  const recipientCurrencyMatches =
    Boolean(recipient?.currency) &&
    recipient?.currency?.toUpperCase() ===
      selectedAccount?.currency?.toUpperCase()

  const normalizedAmount = amount.trim().replace(",", ".")
  const amountPattern = /^\d{1,15}(?:\.\d{1,4})?$/

  const parsedAmount = Number(normalizedAmount)

  const isAmountValid =
    amountPattern.test(normalizedAmount) &&
    Number.isFinite(parsedAmount) &&
    parsedAmount > 0

const selectedAccountBalance =
    typeof selectedAccount?.balance === "number" &&
    Number.isFinite(selectedAccount.balance)
      ? selectedAccount.balance
      : undefined

  const hasKnownBalance = selectedAccountBalance !== undefined

  const hasSufficientBalance =
    selectedAccountBalance !== undefined &&
    isAmountValid &&
    parsedAmount <= selectedAccountBalance

  const recipientIsReady =
    verificationRequested &&
    recipientQuery.isSuccess &&
    isRecipientActive &&
    recipientCurrencyMatches

  const isTransferReady =
    Boolean(selectedAccount) &&
    recipientIsReady &&
    isAmountValid &&
    hasSufficientBalance

  function handleRecipientNumberChange(value: string) {
    setRecipientAccountNumber(value.slice(0, 50))
    setVerificationRequested(false)
  }

  function handleVerifyRecipient() {
    if (!isRecipientInputValid) {
      return
    }

    setVerificationRequested(true)
  }

  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">
          TRANSFER İŞLEMLERİ
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Para transferi
        </h1>

        <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
          Bank A hesabınızdan Bank B üzerindeki bir hesaba para
          transferi hazırlayın. Transfer öncesinde alıcı hesabı
          doğrulanır.
        </p>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <WalletCards className="size-5 text-primary" />
                Kaynak hesap
              </CardTitle>

              <CardDescription>
                Transfer tutarının çekileceği Bank A hesabınızı seçin.
                Yalnızca aktif TRY hesapları kullanılabilir.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              {accountsQuery.isPending ? (
                <AccountLoadingState />
              ) : accountsQuery.isError ? (
                <div className="space-y-3">
                  <p
                    role="alert"
                    className="text-sm text-destructive"
                  >
                    Kaynak hesaplarınız alınamadı. Servisleri kontrol
                    edip tekrar deneyin.
                  </p>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => void accountsQuery.refetch()}
                    disabled={accountsQuery.isFetching}
                  >
                    <RefreshCw className="size-4" />
                    Tekrar Dene
                  </Button>
                </div>
              ) : eligibleAccounts.length === 0 ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted-foreground">
                    Transfer yapabileceğiniz aktif bir TRY hesabı
                    bulunamadı.
                  </p>

                  <Button
                    variant="outline"
                    size="sm"
                    render={<Link to="/accounts" />}
                  >
                    Hesaplarımı Görüntüle
                  </Button>
                </div>
              ) : (
                <>
                  <AccountSelect
                    accounts={eligibleAccounts}
                    value={sourceAccountId}
                    onChange={setSourceAccountId}
                  />

                  {selectedAccount && (
                    <div className="rounded-lg bg-muted/50 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <p className="text-xs text-muted-foreground">
                            Kullanılabilir bakiye
                          </p>

                          <p className="mt-1 text-lg font-semibold tabular-nums">
                            {formatMoney(selectedAccount.balance)}
                          </p>
                        </div>

                        <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
                          Aktif
                        </span>
                      </div>

                      <p className="mt-3 break-all font-mono text-xs text-muted-foreground">
                        {selectedAccount.accountNumber ?? selectedAccount.accountId}
                      </p>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <ArrowDownLeft className="size-5 text-primary" />
                Alıcı hesap
              </CardTitle>

              <CardDescription>
                Bank B alıcısının hesap numarasını girin ve transfer
                öncesinde doğrulayın.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="recipient-account-number"
                  className="text-sm font-medium"
                >
                  Alıcı hesap numarası
                </label>

                <div className="flex flex-col gap-2 sm:flex-row">
                  <Input
                    id="recipient-account-number"
                    value={recipientAccountNumber}
                    onChange={(event) =>
                      handleRecipientNumberChange(event.target.value)
                    }
                    placeholder="BANK-B-RECIPIENT-001"
                    maxLength={50}
                    autoComplete="off"
                    aria-invalid={
                      recipientAccountNumber.length > 0 &&
                      !isRecipientInputValid
                    }
                  />

                  <Button
                    type="button"
                    onClick={handleVerifyRecipient}
                    disabled={
                      !isRecipientInputValid ||
                      recipientQuery.isFetching
                    }
                  >
                    {recipientQuery.isFetching ? (
                      <RefreshCw className="size-4 animate-spin" />
                    ) : (
                      <CheckCircle2 className="size-4" />
                    )}
                    Doğrula
                  </Button>
                </div>

                <p className="text-xs text-muted-foreground">
                  Hesap numarası en fazla 50 karakter olabilir.
                </p>
              </div>

              <RecipientVerificationStatus
                isFetching={recipientQuery.isFetching}
                isRequested={verificationRequested}
                isError={recipientQuery.isError}
                error={recipientQuery.error}
                isSuccess={recipientQuery.isSuccess}
                isActive={Boolean(isRecipientActive)}
                currencyMatches={Boolean(recipientCurrencyMatches)}
              />

              {recipientQuery.isError && verificationRequested && (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => void recipientQuery.refetch()}
                  disabled={recipientQuery.isFetching}
                >
                  <RefreshCw className="size-4" />
                  Yeniden Dene
                </Button>
              )}

              {recipientQuery.isSuccess && recipient && (
                <div className="rounded-lg border border-border/70 bg-muted/30 p-4">
                  <p className="text-xs text-muted-foreground">
                    Doğrulanan hesap numarası
                  </p>

                  <p className="mt-1 break-all font-mono text-sm font-medium">
                    {recipient.accountNumber ?? normalizedRecipientNumber}
                  </p>

                  <div className="mt-3 flex flex-wrap gap-2 text-xs">
                    <span className="rounded-full bg-muted px-2.5 py-1">
                      Para birimi: {recipient.currency ?? "Bilinmiyor"}
                    </span>

                    <span className="rounded-full bg-muted px-2.5 py-1">
                      Durum: {recipient.status ?? "Bilinmiyor"}
                    </span>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="text-base">
                Transfer tutarı
              </CardTitle>

              <CardDescription>
                MVP kapsamında yalnızca TRY cinsinden transfer
                yapılabilir.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-3">
              <label
                htmlFor="transfer-amount"
                className="text-sm font-medium"
              >
                Gönderilecek tutar (TRY)
              </label>

              <div className="relative">
                <Input
                  id="transfer-amount"
                  type="text"
                  inputMode="decimal"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  placeholder="0,00"
                  autoComplete="off"
                  aria-invalid={amount.length > 0 && !isAmountValid}
                  disabled={!selectedAccount}
                />

                <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                  TRY
                </span>
              </div>

              {amount.length > 0 && !isAmountValid && (
                <p className="text-xs text-destructive">
                  Sıfırdan büyük, en fazla 15 tam ve 4 ondalık basamak
                  içeren geçerli bir tutar girin.
                </p>
              )}

              {isAmountValid && !hasKnownBalance && (
                <p className="text-xs text-destructive">
                  Kaynak hesabın bakiyesi alınamadığı için tutar
                  doğrulanamıyor.
                </p>
              )}

              {isAmountValid && hasKnownBalance && !hasSufficientBalance && (
                <p className="text-xs text-destructive">
                  Girilen tutar kaynak hesabın kullanılabilir bakiyesini
                  aşıyor.
                </p>
              )}

              {isAmountValid &&
                hasSufficientBalance &&
                selectedAccountBalance !== undefined && (
                  <p className="text-xs text-muted-foreground">
                    Transfer sonrası tahmini bakiye:{" "}
                    <span className="font-semibold tabular-nums">
                      {formatMoney(selectedAccountBalance - parsedAmount)}
                    </span>
                  </p>
                )}
            </CardContent>
          </Card>
        </div>

        <aside className="space-y-4">
          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <ArrowLeftRight className="size-5 text-primary" />
                Transfer özeti
              </CardTitle>

              <CardDescription>
                Girdiğiniz bilgilerin ön kontrolü.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">
                  Gönderen
                </p>

                <p className="break-all text-sm font-medium">
                  {selectedAccount
                    ? getAccountLabel(selectedAccount)
                    : "Hesap seçilmedi"}
                </p>
              </div>

              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">
                  Alıcı
                </p>

                <p className="break-all text-sm font-medium">
                  {recipientIsReady
                    ? recipient?.accountNumber ?? normalizedRecipientNumber
                    : "Henüz doğrulanmadı"}
                </p>
              </div>

              <div className="border-t border-border/70 pt-4">
                <p className="text-xs text-muted-foreground">
                  Transfer tutarı
                </p>

                <p className="mt-1 text-2xl font-semibold tabular-nums">
                  {isAmountValid ? formatMoney(parsedAmount) : "—"}
                </p>
              </div>

              <div
                className={`rounded-lg p-3 text-sm ${
                  isTransferReady
                    ? "bg-primary/10 text-primary"
                    : "bg-muted text-muted-foreground"
                }`}
              >
                {isTransferReady ? (
                  <span className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0" />
                    Transfer bilgileri ön kontrollerden geçti.
                  </span>
                ) : (
                  <span className="flex items-start gap-2">
                    <AlertCircle className="mt-0.5 size-4 shrink-0" />
                    Devam edebilmek için kaynak hesap, alıcı doğrulaması
                    ve tutar kontrolleri tamamlanmalıdır.
                  </span>
                )}
              </div>
            </CardContent>
          </Card>

          <p className="text-xs leading-5 text-muted-foreground">
            Bu aşamada transfer henüz gönderilmez. Sonraki adımda
            transfer oluşturma isteğini, idempotency korumasını ve
            işlem sonucunun takibini ekleyeceğiz.
          </p>
        </aside>
      </div>
    </div>
  )
}