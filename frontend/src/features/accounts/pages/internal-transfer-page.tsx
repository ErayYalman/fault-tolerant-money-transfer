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
import type {
  AccountSummary,
  CreateInternalTransferRequest,
  InternalTransferResponse,
} from "@/features/accounts/api/accounts.api"
import { useCreateInternalTransfer } from "@/features/accounts/hooks/useInternalTransfer"

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

function normalizeCurrency(value: string | undefined): string | undefined {
  const currency = value?.trim().toUpperCase()

  return currency && /^[A-Z]{3}$/.test(currency)
    ? currency
    : undefined
}

function formatMoney(
  value: number | undefined,
  currency: string | undefined,
): string {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—"
  }

  const normalizedCurrency = normalizeCurrency(currency)

  if (normalizedCurrency) {
    try {
      return new Intl.NumberFormat("tr-TR", {
        style: "currency",
        currency: normalizedCurrency,
      }).format(value)
    } catch {
      // Geçersiz veya desteklenmeyen para biriminde sayısal gösterime dön.
    }
  }

  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value)
}

function getAccountLabel(account: AccountSummary): string {
  return account.accountNumber ?? account.accountId ?? "Hesap"
}

function isEligibleAccount(account: AccountSummary): boolean {
  return (
    Boolean(account.accountId) &&
    account.status === "ACTIVE" &&
    Boolean(normalizeCurrency(account.currency))
  )
}

function getAccountErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yaparak tekrar deneyin."
  }

  if (error instanceof ApiNetworkError) {
    return "API Gateway servisine ulaşılamadı. Servislerin çalıştığını kontrol edin."
  }

  if (error instanceof ApiTimeoutError) {
    return "Hesaplar zamanında alınamadı."
  }

  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Oturumunuz doğrulanamadı. Tekrar giriş yapmanız gerekiyor."
    }

    if (error.status === 403) {
      return "Hesapları görüntülemek için CUSTOMER rolü gerekiyor."
    }

    if (error.status >= 500) {
      return "Hesap servisi şu anda yanıt veremiyor."
    }
  }

  return "Hesaplar alınırken beklenmeyen bir hata oluştu."
}

function isAmbiguousTransferError(error: unknown): boolean {
  if (
    error instanceof ApiNetworkError ||
    error instanceof ApiTimeoutError
  ) {
    return true
  }

  if (error instanceof ApiAuthenticationError) {
    return false
  }

  if (error instanceof ApiError) {
    return error.status === 408 || error.status >= 500
  }

  // Tanınmayan bir istemci hatasında isteğin sunucuya ulaşıp
  // ulaşmadığı kesin değil. Yeni işlem başlatmak yerine aynı
  // idempotency anahtarıyla sonucu doğrulamayı tercih et.
  return true
}

function getTransferErrorMessage(error: unknown): string {
  if (error instanceof ApiAuthenticationError) {
    return "Oturum doğrulanamadı. Yeniden giriş yapmanız gerekiyor."
  }

  if (
    error instanceof ApiNetworkError ||
    error instanceof ApiTimeoutError
  ) {
    return "Sunucudan kesin yanıt alınamadı. Transfer gerçekleşmiş olabilir. Aynı isteği aynı anahtarla tekrar göndererek mevcut sonucu kontrol edin."
  }

  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Transfer bilgileri geçersiz. Girdiğiniz alanları kontrol edin."
    }

    if (error.status === 401 || error.status === 403) {
      return "Bu transfer işlemi için oturum veya yetki doğrulaması başarısız oldu."
    }

    if (error.status === 404) {
      return "Hesap bulunamadı veya bu hesaba erişim yetkiniz yok."
    }

    if (error.status === 409) {
      return "Idempotency anahtarı çakışması oluştu. Aynı anahtarla farklı bir işlem yapılmış olabilir."
    }

    if (error.status === 422) {
      return "Hesap durumu, bakiye veya para birimi nedeniyle transfer kabul edilmedi."
    }

    if (error.status === 429) {
      return "Çok fazla istek gönderildi. Bir süre bekleyip tekrar deneyin."
    }

    if (error.status >= 500) {
      return "Sunucu hatası nedeniyle isteğin sonucu belirsiz. Aynı isteği aynı anahtarla tekrar gönderin."
    }
  }

  return "Transfer gönderilirken beklenmeyen bir hata oluştu. Güvenlik nedeniyle aynı isteğin sonucu doğrulanana kadar yeni transfer başlatılamaz."
}

function AccountLoadingState() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-4 w-48" />
    </div>
  )
}

function AccountSelect({
  id,
  label,
  placeholder,
  accounts,
  value,
  onChange,
  disabled,
}: {
  id: string
  label: string
  placeholder: string
  accounts: AccountSummary[]
  value: string
  onChange: (value: string) => void
  disabled: boolean
}) {
  return (
    <div className="space-y-2">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>

      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        className="h-10 w-full rounded-lg border border-input bg-transparent px-3 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <option value="" disabled>
          {placeholder}
        </option>

        {accounts.map((account) => (
          <option
            key={account.accountId}
            value={account.accountId}
          >
            {getAccountLabel(account)}
            {" · "}
            {formatMoney(
              account.balance,
              account.currency,
            )}
          </option>
        ))}
      </select>
    </div>
  )
}

function AccountInformation({
  account,
  label,
}: {
  account: AccountSummary
  label: string
}) {
  return (
    <div className="rounded-lg bg-muted/50 p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">
            {label}
          </p>

          <p className="mt-1 text-lg font-semibold tabular-nums">
            {formatMoney(account.balance, account.currency)}
          </p>
        </div>

        <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
          Aktif
        </span>
      </div>

      <p className="mt-3 break-all font-mono text-xs text-muted-foreground">
        {account.accountNumber ?? account.accountId}
      </p>
    </div>
  )
}

export function InternalTransferPage() {
  const accountsQuery = useAccounts(0, ACCOUNT_PAGE_SIZE)
  const createTransferMutation = useCreateInternalTransfer()
  const submissionInFlight = useRef(false)

  const [sourceAccountId, setSourceAccountId] = useState("")
  const [destinationAccountId, setDestinationAccountId] = useState("")
  const [amount, setAmount] = useState("")

  const [createdTransfer, setCreatedTransfer] =
    useState<InternalTransferResponse | null>(null)

  const [ambiguousRequest, setAmbiguousRequest] =
    useState<CreateInternalTransferRequest | null>(null)

  const [submissionError, setSubmissionError] =
    useState<unknown>(null)

  const [isAmbiguousOutcome, setIsAmbiguousOutcome] =
    useState(false)

  const accounts = accountsQuery.data?.content ?? []
  const eligibleAccounts = accounts.filter(isEligibleAccount)

  const selectedSourceAccount = eligibleAccounts.find(
    (account) => account.accountId === sourceAccountId,
  )

  const sourceCurrency = normalizeCurrency(
    selectedSourceAccount?.currency,
  )

  const destinationAccounts = eligibleAccounts.filter(
    (account) =>
      account.accountId !== sourceAccountId &&
      normalizeCurrency(account.currency) === sourceCurrency,
  )

  const selectedDestinationAccount = destinationAccounts.find(
    (account) => account.accountId === destinationAccountId,
  )

  const normalizedAmount = amount.trim().replace(",", ".")
  const amountPattern = /^\d{1,15}(?:\.\d{1,4})?$/
  const parsedAmount = Number(normalizedAmount)

  const isAmountValid =
    amountPattern.test(normalizedAmount) &&
    Number.isFinite(parsedAmount) &&
    parsedAmount > 0

  const sourceBalance =
    typeof selectedSourceAccount?.balance === "number" &&
    Number.isFinite(selectedSourceAccount.balance)
      ? selectedSourceAccount.balance
      : undefined

  const hasKnownSourceBalance = sourceBalance !== undefined

  const hasSufficientBalance =
    sourceBalance !== undefined &&
    isAmountValid &&
    parsedAmount <= sourceBalance

  const isFormLocked =
    isAmbiguousOutcome || createdTransfer !== null

  const isTransferReady =
    Boolean(selectedSourceAccount?.accountId) &&
    Boolean(selectedDestinationAccount?.accountId) &&
    Boolean(sourceCurrency) &&
    isAmountValid &&
    hasKnownSourceBalance &&
    hasSufficientBalance &&
    !isFormLocked

  async function sendTransferRequest(
    request: CreateInternalTransferRequest,
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

      if (isAmbiguousTransferError(error)) {
        // Request body and idempotency key are retained together.
        setAmbiguousRequest(request)
        setIsAmbiguousOutcome(true)
      } else {
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
      !selectedSourceAccount?.accountId ||
      !selectedDestinationAccount?.accountId ||
      !sourceCurrency ||
      isFormLocked ||
      createTransferMutation.isPending
    ) {
      return
    }

    const request: CreateInternalTransferRequest = {
      amount: parsedAmount,
      currency: sourceCurrency,
      fromAccountId: selectedSourceAccount.accountId,
      toAccountId: selectedDestinationAccount.accountId,
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

    // Yeni UUID üretilmez. İlk POST isteğinin aynısı gönderilir.
    void sendTransferRequest(ambiguousRequest)
  }

  function handlePrepareAnotherTransfer() {
    setSourceAccountId("")
    setDestinationAccountId("")
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
          HESAP İŞLEMLERİ
        </p>

        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Hesaplar arası transfer
        </h1>

        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
          Kendi Bank A hesaplarınız arasında güvenli para aktarımı
          gerçekleştirin. Kaynak ve hedef hesapların size ait, aktif
          ve aynı para biriminde olması gerekir.
        </p>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          <Card className="shadow-none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <WalletCards className="size-5 text-primary" />
                Transfer hesapları
              </CardTitle>

              <CardDescription>
                Transferin yapılacağı iki Bank A hesabını seçin.
                Hesaplar yalnızca oturum açan kullanıcıya ait hesaplar
                arasından listelenir.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-5">
              {accountsQuery.isPending ? (
                <AccountLoadingState />
              ) : accountsQuery.isError ? (
                <div className="space-y-3">
                  <p role="alert" className="text-sm text-destructive">
                    {getAccountErrorMessage(accountsQuery.error)}
                  </p>

                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => void accountsQuery.refetch()}
                    disabled={accountsQuery.isFetching}
                  >
                    <RefreshCw
                      className={`size-4 ${
                        accountsQuery.isFetching ? "animate-spin" : ""
                      }`}
                    />
                    Tekrar Dene
                  </Button>
                </div>
              ) : eligibleAccounts.length < 2 ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted-foreground">
                    İç transfer yapabilmek için en az iki aktif Bank A
                    hesabınız bulunmalıdır.
                  </p>

                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    render={<Link to="/accounts" />}
                  >
                    Hesaplarımı Görüntüle
                  </Button>
                </div>
              ) : (
                <div className="space-y-5">
                  <AccountSelect
                    id="internal-transfer-source"
                    label="Gönderen hesap"
                    placeholder="Kaynak hesap seçin"
                    accounts={eligibleAccounts}
                    value={sourceAccountId}
                    onChange={(value) => {
                      setSourceAccountId(value)
                      setDestinationAccountId("")
                      setSubmissionError(null)
                    }}
                    disabled={isFormLocked}
                  />

                  {selectedSourceAccount && (
                    <AccountInformation
                      account={selectedSourceAccount}
                      label="Gönderen hesabın kullanılabilir bakiyesi"
                    />
                  )}

                  <div className="flex justify-center">
                    <div className="flex size-9 items-center justify-center rounded-full border border-border bg-muted/40">
                      <ArrowDownLeft className="size-4 text-muted-foreground" />
                    </div>
                  </div>

                  {selectedSourceAccount ? (
                    destinationAccounts.length === 0 ? (
                      <div className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                        Kaynak hesapla aynı para biriminde başka aktif
                        bir hesabınız bulunmuyor.
                      </div>
                    ) : (
                      <>
                        <AccountSelect
                          id="internal-transfer-destination"
                          label="Alıcı hesap"
                          placeholder="Hedef hesap seçin"
                          accounts={destinationAccounts}
                          value={destinationAccountId}
                          onChange={(value) => {
                            setDestinationAccountId(value)
                            setSubmissionError(null)
                          }}
                          disabled={isFormLocked}
                        />

                        {selectedDestinationAccount && (
                          <AccountInformation
                            account={selectedDestinationAccount}
                            label="Alıcı hesabın mevcut bakiyesi"
                          />
                        )}
                      </>
                    )
                  ) : (
                    <div className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                      Önce gönderen hesabı seçin. Uygun alıcı hesaplar
                      daha sonra listelenecek.
                    </div>
                  )}
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
                Seçilen hesapların para birimi:{" "}
                <span className="font-medium text-foreground">
                  {sourceCurrency ?? "Henüz seçilmedi"}
                </span>
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-3">
              <label
                htmlFor="internal-transfer-amount"
                className="text-sm font-medium"
              >
                Gönderilecek tutar
              </label>

              <div className="relative">
                <Input
                  id="internal-transfer-amount"
                  type="text"
                  inputMode="decimal"
                  value={amount}
                  onChange={(event) => {
                    setAmount(event.target.value)
                    setSubmissionError(null)
                  }}
                  placeholder="0,00"
                  autoComplete="off"
                  disabled={!selectedSourceAccount || isFormLocked}
                  aria-invalid={
                    amount.length > 0 && !isAmountValid
                  }
                />

                {sourceCurrency && (
                  <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                    {sourceCurrency}
                  </span>
                )}
              </div>

              {amount.length > 0 && !isAmountValid && (
                <p className="text-xs text-destructive">
                  Sıfırdan büyük, en fazla 15 tam ve 4 ondalık basamak
                  içeren geçerli bir tutar girin.
                </p>
              )}

              {isAmountValid && !hasKnownSourceBalance && (
                <p className="text-xs text-destructive">
                  Kaynak hesabın bakiyesi alınamadığı için tutar
                  doğrulanamıyor.
                </p>
              )}

              {isAmountValid &&
                hasKnownSourceBalance &&
                !hasSufficientBalance && (
                  <p className="text-xs text-destructive">
                    Girilen tutar kaynak hesabın kullanılabilir
                    bakiyesini aşıyor.
                  </p>
                )}

              {isAmountValid &&
                hasSufficientBalance &&
                sourceBalance !== undefined && (
                  <p className="text-xs text-muted-foreground">
                    Transfer sonrası tahmini bakiye:{" "}
                    <span className="font-semibold tabular-nums">
                      {formatMoney(
                        sourceBalance - parsedAmount,
                        sourceCurrency,
                      )}
                    </span>
                  </p>
                )}

              <p className="text-xs leading-5 text-muted-foreground">
                Transfer iki hesabın bakiyesini, transfer kaydını ve
                ledger hareketlerini tek bir veritabanı transaction'ı
                içerisinde günceller.
              </p>
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
                İşlemi göndermeden önce bilgileri kontrol edin.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">
                  Gönderen hesap
                </p>

                <p className="break-all text-sm font-medium">
                  {selectedSourceAccount
                    ? getAccountLabel(selectedSourceAccount)
                    : "Hesap seçilmedi"}
                </p>
              </div>

              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">
                  Alıcı hesap
                </p>

                <p className="break-all text-sm font-medium">
                  {selectedDestinationAccount
                    ? getAccountLabel(selectedDestinationAccount)
                    : "Hesap seçilmedi"}
                </p>
              </div>

              <div className="border-t border-border/70 pt-4">
                <p className="text-xs text-muted-foreground">
                  Transfer tutarı
                </p>

                <p className="mt-1 text-2xl font-semibold tabular-nums">
                  {isAmountValid
                    ? formatMoney(parsedAmount, sourceCurrency)
                    : "—"}
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
                    Hesap ve tutar kontrolleri tamamlandı. Transfer
                    gönderilebilir.
                  </span>
                ) : (
                  <span className="flex items-start gap-2">
                    <AlertCircle className="mt-0.5 size-4 shrink-0" />
                    Devam etmek için iki farklı aktif hesap ve geçerli
                    bir transfer tutarı seçin.
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
              {getTransferErrorMessage(submissionError)}
            </div>
          )}

          {isAmbiguousOutcome && ambiguousRequest && (
            <Card className="shadow-none">
              <CardHeader>
                <CardTitle className="text-base">
                  Transfer sonucu doğrulanamadı
                </CardTitle>

                <CardDescription>
                  İstek sunucuya ulaşmış ve transfer tamamlanmış olabilir.
                  Güvenlik nedeniyle form kilitlendi. Aynı isteği aynı
                  idempotency anahtarıyla tekrar göndererek sonucu
                  doğrulayın.
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
                      Sonuç kontrol ediliyor...
                    </>
                  ) : (
                    "Aynı İsteği Tekrar Gönder"
                  )}
                </Button>
              </CardContent>
            </Card>
          )}

          {createdTransfer && (
            <Card className="shadow-none">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <CheckCircle2 className="size-5 text-primary" />
                  İç transfer tamamlandı
                </CardTitle>

                <CardDescription>
                  Bank A hesaplarınız arasındaki para hareketi
                  tamamlandı ve kayıt altına alındı.
                </CardDescription>
              </CardHeader>

              <CardContent className="space-y-4">
                <div className="rounded-lg bg-primary/10 p-3">
                  <p className="text-xs text-muted-foreground">
                    Transfer durumu
                  </p>

                  <p className="mt-1 text-sm font-semibold text-primary">
                    {createdTransfer.status ?? "COMPLETED"}
                  </p>

                  {createdTransfer.replayed && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      Daha önce tamamlanmış transfer kaydı döndürüldü.
                    </p>
                  )}
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Transfer ID
                  </p>

                  <p className="mt-1 break-all font-mono text-xs">
                    {createdTransfer.transferId ?? "—"}
                  </p>
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Correlation ID
                  </p>

                  <p className="mt-1 break-all font-mono text-xs">
                    {createdTransfer.correlationId ?? "—"}
                  </p>
                </div>

                <div>
                  <p className="text-xs text-muted-foreground">
                    Tutar
                  </p>

                  <p className="mt-1 text-lg font-semibold tabular-nums">
                    {formatMoney(
                      createdTransfer.amount,
                      createdTransfer.currency,
                    )}
                  </p>
                </div>

                <div className="rounded-lg bg-muted p-3 text-xs leading-5 text-muted-foreground">
                  Gönderen hesabın hareketlerinde bir DEBIT, alıcı
                  hesabın hareketlerinde bir CREDIT kaydı oluştu.
                  Her iki kayıt da aynı transfer ve korelasyon
                  kimliklerini kullanır.
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
                  Transfer gerçekleştiriliyor...
                </>
              ) : (
                <>
                  <ArrowLeftRight className="size-4" />
                  Transferi Gerçekleştir
                </>
              )}
            </Button>
          )}

          <div className="space-y-2">
            <p className="text-xs leading-5 text-muted-foreground">
              Transfer tamamlandıktan sonra her iki hesabın hareket
              kayıtlarını ayrı ayrı inceleyebilirsiniz.
            </p>

            <Button
              type="button"
              variant="outline"
              className="w-full"
              render={<Link to="/accounts" />}
            >
              Hesaplarıma Dön
            </Button>
          </div>
        </aside>
      </div>

      {accountsQuery.data &&
        (accountsQuery.data.totalElements ?? 0) > accounts.length && (
          <p role="status" className="text-xs text-muted-foreground">
            Hesap seçimi için ilk {ACCOUNT_PAGE_SIZE} hesabınız yüklendi.
            Görüntülenen hesap sayısı toplam hesap sayısından az.
          </p>
        )}
    </div>
  )
}
