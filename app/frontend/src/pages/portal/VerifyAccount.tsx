import { useState } from 'react'
import { CheckCircle2, Mail, Phone } from 'lucide-react'
import { Card } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useAuthStore } from '@/store/useAuthStore'
import { sendVerificationOtp, verifyOtp } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'

function VerifyBlock({ label, icon: Icon, identifier, purpose }: { label: string; icon: typeof Mail; identifier?: string; purpose: 'EMAIL_VERIFICATION' | 'MOBILE_VERIFICATION' }) {
  const [sent, setSent] = useState(false)
  const [code, setCode] = useState('')
  const [sending, setSending] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [verified, setVerified] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSend() {
    if (!identifier) return
    setSending(true); setError(null)
    try { await sendVerificationOtp(identifier, purpose); setSent(true) }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Could not send the code.') }
    finally { setSending(false) }
  }

  async function handleVerify() {
    if (!identifier || code.length !== 6) return
    setVerifying(true); setError(null)
    try { await verifyOtp(identifier, code, purpose); setVerified(true) }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Incorrect code. Please try again.') }
    finally { setVerifying(false) }
  }

  return (
    <Card className="p-5">
      <div className="flex items-center gap-2.5"><Icon className="h-4 w-4 text-ink-faint" /><p className="font-medium text-ink">{label}</p></div>
      <p className="mt-1 text-sm text-ink-muted">{identifier ?? 'Not set'}</p>

      {verified ? (
        <div className="mt-4 flex items-center gap-2 text-sm text-success"><CheckCircle2 className="h-4 w-4" /> Verified</div>
      ) : !sent ? (
        <Button size="sm" variant="secondary" className="mt-4" onClick={handleSend} disabled={sending || !identifier}>{sending ? 'Sending…' : 'Send verification code'}</Button>
      ) : (
        <div className="mt-4 flex items-end gap-2">
          <Input label="6-digit code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} className="w-32" />
          <Button size="sm" onClick={handleVerify} disabled={verifying || code.length !== 6}>{verifying ? 'Checking…' : 'Verify'}</Button>
        </div>
      )}
      {error && <p role="alert" className="mt-2 text-xs text-red-500">{error}</p>}
    </Card>
  )
}

export default function VerifyAccount() {
  const { user } = useAuthStore()

  return (
    <div className="mx-auto max-w-lg space-y-5">
      <div>
        <h2 className="font-display text-xl text-ink">Verify your account</h2>
        <p className="mt-1 text-sm text-ink-muted">Confirming your email and mobile helps us reach you about bookings, galleries and invoices.</p>
      </div>
      <VerifyBlock label="Email" icon={Mail} identifier={user?.email} purpose="EMAIL_VERIFICATION" />
      <VerifyBlock label="Mobile" icon={Phone} identifier={user?.mobile} purpose="MOBILE_VERIFICATION" />
    </div>
  )
}
