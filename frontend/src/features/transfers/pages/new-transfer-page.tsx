import { useRef, useState } from "react"
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
import { useCreateTransfer } from "@/features/transfers/hooks/useCreateTransfer"
import { useRecipientLookup } from "@/features/transfers/hooks/useRecipientLookup"
import { useTransferStatus } from "@/features/transfers/hooks/useTransferStatus"
import type {
  CreateTransferRequest,
  TransferResponse,
} from "@/features/transfers/api/transfers.api"

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

function isAmbiguousTransferError(error: unknown): boolean {
  if (
    error instanceof ApiNetworkError ||
    error instanceof ApiTimeoutError
  ) {
    return true
  }

  return (
    error instanceof ApiError &&
    (error.status === 408 || error.status >= 500)
  )
}

function getTransferCreationErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yapıp tekrar deneyin."
  }

  if (
    error instanceof ApiNetworkError ||
    error instanceof ApiTimeoutError
  ) {
    return "Sunucudan kesin yanıt alınamadı. Transfer oluşturulmuş olabilir. Aynı isteği aynı idempotency anahtarıyla tekrar gönderin; yeni bir transfer başlatmayın."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Transfer bilgileri geçersiz. Girdiğiniz alanları kontrol edin."
    }

    if (error.status === 401 || error.status === 403) {
      return "Bu transfer işlemi için oturum veya yetki doğrulaması başarısız oldu."
    }

    if (error.status === 404) {
      return "Kaynak veya alıcı hesap bulunamadı. Hesap bilgilerini kontrol edin."
    }

    if (error.status === 409) {
      return "Idempotency anahtarı çakışması oluştu. Yeni bir istek oluşturmadan önce işlemi kontrol edin."
    }

    if (error.status === 422) {
      return "Hesap durumu veya para birimi nedeniyle transfer kabul edilmedi."
    }

    if (error.status === 429) {
      return "Çok fazla transfer isteği gönderildi. Bir süre bekleyip tekrar deneyin."
    }

    if (error.status >= 500) {
      return "Sunucu hatası nedeniyle isteğin sonucu belirsiz. Aynı isteği aynı anahtarla tekrar gönderin."
    }
  }

  return "Transfer gönderilirken beklenmeyen bir hata oluştu."
}

const TRANSFER_STATUS_LABELS: Record<
  NonNullable<TransferResponse["status"]>,
  string
> = {
  PENDING: "Beklemede",
  DEBIT_REQUESTED: "Gönderen bankada borçlandırma bekleniyor",
  DEBIT_COMPLETED: "Gönderen banka borçlandırmayı tamamladı",
  DEBIT_FAILED: "Gönderen banka işlemi başarısız",
  CREDIT_REQUESTED: "Alıcı bankada alacaklandırma bekleniyor",
  CREDIT_FAILED: "Alıcı banka işlemi başarısız",
  COMPLETED: "Transfer tamamlandı",
  COMPENSATING: "Telafi işlemi devam ediyor",
  COMPENSATED: "Transfer telafi edildi",
}

function getTransferStatusLabel(
  status: TransferResponse["status"],
): string {
  return status
    ? TRANSFER_STATUS_LABELS[status]
    : "Durum bilgisi alınamadı"
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
  disabled,
}: {
  accounts: AccountSummary[]
  value: string
  onChange: (value: string) => void
  disabled: boolean
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
        disabled={disabled}
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

  const createTransferMutation = useCreateTransfer()
  const submissionInFlight = useRef(false)

  const [createdTransfer, setCreatedTransfer] =
    useState<TransferResponse | null>(null)

  const transferStatusQuery = useTransferStatus(
    createdTransfer?.transferId,
  )

  const displayedTransfer =
    transferStatusQuery.data ?? createdTransfer

  const [ambiguousRequest, setAmbiguousRequest] =
    useState<CreateTransferRequest | null>(null)

  const [submissionError, setSubmissionError] =
    useState<unknown>(null)

  const [isAmbiguousOutcome, setIsAmbiguousOutcome] =
    useState(false)

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

  const isFormLocked =
    isAmbiguousOutcome || createdTransfer !== null

  function handleRecipientNumberChange(value: string) {
    setRecipientAccountNumber(value.slice(0, 50))
    setVerificationRequested(false)
    setSubmissionError(null)
  }

  function handleVerifyRecipient() {
    if (!isRecipientInputValid) {
      return
    }

    setVerificationRequested(true)
  }

  async function sendTransferRequest(
    request: CreateTransferRequest,
    retryingAmbiguousRequest = false,
  ) {
    if (submissionInFlight.current) {
      return
    }

    submissionInFlight.current = true
    setSubmissionError(null)

    try {
      const response =
        await createTransferMutation.mutateAsync(request)

      setCreatedTransfer(response)
      setAmbiguousRequest(null)
      setIsAmbiguousOutcome(false)
    } catch (error) {
      setSubmissionError(error)

      if (
        retryingAmbiguousRequest ||
        isAmbiguousTransferError(error)
      ) {
        // Belirsiz bir isteğin anahtarı ve gövdesi korunur.
        setAmbiguousRequest(request)
        setIsAmbiguousOutcome(true)
      } else {
        // Kesin olarak reddedilen isteğin yeniden düzenlenmesine izin ver.
        setAmbiguousRequest(null)
        setIsAmbiguousOutcome(false)
      }
    } finally {
      submissionInFlight.current = false
    }
  }

  function handleCreateTransfer() {
    if (
      !isTransferReady ||
      !selectedAccount?.accountId ||
      isFormLocked ||
      createTransferMutation.isPending
    ) {
      return
    }

    const request: CreateTransferRequest = {
      amount: parsedAmount,
      currency: "TRY",
      fromAccountId: selectedAccount.accountId,
      toAccountNumber: normalizedRecipientNumber,
      idempotencyKey: crypto.randomUUID(),
    }

    void sendTransferRequest(request)
  }

  function handleRetryAmbiguousTransfer() {
    if (
      !ambiguousRequest ||
      createTransferMutation.isPending
    ) {
      return
    }

    // Yeni UUID oluşturulmuyor. İlk isteğin aynısı gönderiliyor.
    void sendTransferRequest(ambiguousRequest, true)
  }

  function handlePrepareAnotherTransfer() {
    setSourceAccountId("")
    setRecipientAccountNumber("")
    setVerificationRequested(false)
    setAmount("")

    setCreatedTransfer(null)
    setAmbiguousRequest(null)
    setSubmissionError(null)
    setIsAmbiguousOutcome(false)

    createTransferMutation.reset()
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
                    onChange={(value) => {
                      setSourceAccountId(value)
                      setSubmissionError(null)
                    }}
                    disabled={isFormLocked}
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
                    disabled={isFormLocked}
                    aria-invalid={
                      recipientAccountNumber.length > 0 &&
                      !isRecipientInputValid
                    }
                  />

                  <Button
                    type="button"
                    onClick={handleVerifyRecipient}
                    disabled={
                      isFormLocked ||
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
                  onChange={(event) => {
                    setAmount(event.target.value)
                    setSubmissionError(null)
                  }}
                  placeholder="0,00"
                  autoComplete="off"
                  aria-invalid={amount.length > 0 && !isAmountValid}
                  disabled={!selectedAccount || isFormLocked}
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

          {submissionError !== null && (
            <div
              role="alert"
              className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
            >
              {getTransferCreationErrorMessage(submissionError)}
            </div>
          )}

          {isAmbiguousOutcome && ambiguousRequest && (
            <Card className="shadow-none">
              <CardHeader>
                <CardTitle className="text-base">
                  Transfer sonucu doğrulanamadı
                </CardTitle>

                <CardDescription>
                  Güvenlik nedeniyle yeni transfer başlatılamıyor.
                  Aynı isteği aynı idempotency anahtarıyla tekrar
                  göndererek mevcut sonucu öğrenebilirsiniz.
                </CardDescription>
              </CardHeader>

              <CardContent>
                <Button
                  type="button"
                  className="w-full"
                  variant="outline"
                  onClick={handleRetryAmbiguousTransfer}
                  disabled={createTransferMutation.isPending}
                >
                  {createTransferMutation.isPending ? (
                    <>
                      <RefreshCw className="size-4 animate-spin" />
                      Tekrar gönderiliyor...
                    </>
                  ) : (
                    "Aynı İsteği Tekrar Gönder"
                  )}
                </Button>
              </CardContent>
            </Card>
          )}

          {createdTransfer && displayedTransfer && (
            <Card className="shadow-none">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <CheckCircle2 className="size-5 text-primary" />
                  Transfer isteği alındı
                </CardTitle>

                <CardDescription>
                  Backend transfer isteğini kabul etti. Aşağıdaki
                  bilgiler POST yanıtından alınmıştır.
                </CardDescription>
              </CardHeader>

              <CardContent className="space-y-4">
                <div>
                  <p className="text-xs text-muted-foreground">
                    Transfer durumu
                  </p>

                  <p className="mt-1 text-sm font-semibold">
                    {getTransferStatusLabel(displayedTransfer.status)}
                  </p>

                  <p className="mt-1 font-mono text-xs text-muted-foreground">
                    {displayedTransfer.status ?? "Bilinmiyor"}
                  </p>

                  {transferStatusQuery.isError && (
                    <div
                      role="alert"
                      className="rounded-lg bg-destructive/5 p-3 text-xs text-destructive"
                    >
                      Transferin güncel durumu şu anda alınamadı.
                      Otomatik sorgulama devam edecek.

                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="mt-3"
                        onClick={() => void transferStatusQuery.refetch()}
                        disabled={transferStatusQuery.isFetching}
                      >
                        <RefreshCw className="size-4" />
                        Durumu Yenile
                      </Button>
                    </div>
                  )}
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Transfer ID
                  </p>

                  <p className="mt-1 break-all font-mono text-xs">
                    {displayedTransfer.transferId ?? "—"}
                  </p>
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Correlation ID
                  </p>

                  <p className="mt-1 break-all font-mono text-xs">
                    {displayedTransfer.correlationId ?? "—"}
                  </p>
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Tutar
                  </p>

                  <p className="mt-1 text-lg font-semibold tabular-nums">
                    {formatMoney(displayedTransfer.amount)}
                  </p>
                </div>

                <div className="rounded-lg bg-muted p-3 text-xs leading-5 text-muted-foreground">
                  {displayedTransfer.status === "COMPLETED" && (
                    <p>
                      Transfer başarıyla tamamlandı. Bank B hesabına para
                      aktarımı gerçekleştirildi.
                    </p>
                  )}

                  {displayedTransfer.status === "DEBIT_FAILED" && (
                    <p>
                      Kaynak bankadaki borçlandırma adımı başarısız oldu.
                      Transfer tamamlanmadı.
                    </p>
                  )}

                  {displayedTransfer.status === "COMPENSATING" && (
                    <p>
                      Alıcı bankadaki işlem tamamlanamadı. Kaynak bankadaki
                      borçlandırmanın telafi edilmesi bekleniyor.
                    </p>
                  )}

                  {displayedTransfer.status === "COMPENSATED" && (
                    <p>
                      Transfer tamamlanamadı. Kaynak bankadaki borçlandırma
                      telafi edilerek bakiye geri yüklendi.
                    </p>
                  )}

                  {displayedTransfer.status !== "COMPLETED" &&
                    displayedTransfer.status !== "DEBIT_FAILED" &&
                    displayedTransfer.status !== "COMPENSATING" &&
                    displayedTransfer.status !== "COMPENSATED" && (
                      <p>
                        Saga işlemi devam ediyor. Transfer durumu otomatik
                        olarak takip ediliyor.
                      </p>
                    )}

                  {displayedTransfer.failureReason && (
                    <p className="mt-2 break-words">
                      İşlem açıklaması: {displayedTransfer.failureReason}
                    </p>
                  )}
                </div>

                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  onClick={handlePrepareAnotherTransfer}
                >
                  Yeni Transfer Hazırla
                </Button>
              </CardContent>
            </Card>
          )}

          {!isFormLocked && (
            <Button
              type="button"
              className="w-full"
              disabled={
                !isTransferReady ||
                createTransferMutation.isPending
              }
              onClick={handleCreateTransfer}
            >
              {createTransferMutation.isPending ? (
                <>
                  <RefreshCw className="size-4 animate-spin" />
                  Transfer gönderiliyor...
                </>
              ) : (
                <>
                  <ArrowLeftRight className="size-4" />
                  Transferi Gönder
                </>
              )}
            </Button>
          )}

          <div className="space-y-2">
            <p className="text-xs leading-5 text-muted-foreground">
              Transfer işlemlerinizi ve Saga durumlarını işlem geçmişi
              sayfasından takip edebilirsiniz.
            </p>

            <Button
              type="button"
              variant="outline"
              className="w-full"
              render={<Link to="/transfers" />}
            >
              İşlem Geçmişini Görüntüle
            </Button>
          </div>
        </aside>
      </div>
    </div>
  )
}