import { useEffect, useState } from 'react'
import { Mail, MessageCircle, Phone, StickyNote } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { Select, Textarea } from '@/components/ui/Input'
import { fetchInquiryReplies, replyToInquiry, type ContactInquiry, type InquiryReply, type ReplyChannel } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'

const CHANNEL_ICON: Record<ReplyChannel, typeof Mail> = { EMAIL: Mail, PHONE: Phone, WHATSAPP: MessageCircle, OTHER: StickyNote }
const CHANNEL_LABEL: Record<ReplyChannel, string> = { EMAIL: 'Email', PHONE: 'Phone call', WHATSAPP: 'WhatsApp', OTHER: 'Other' }

/** The conversation log for one inquiry: what the visitor wrote, every follow-up, and a form to add or email a reply. */
export function InquiryRepliesModal({ inquiry, onClose, onUpdated }: {
  inquiry: ContactInquiry | null
  onClose: () => void
  onUpdated: (i: ContactInquiry) => void
}) {
  const push = useToastStore((s) => s.push)
  const [replies, setReplies] = useState<InquiryReply[] | null>(null)
  const [channel, setChannel] = useState<ReplyChannel>('EMAIL')
  const [message, setMessage] = useState('')
  const [sendEmail, setSendEmail] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!inquiry) return
    let live = true
    setReplies(null); setMessage(''); setChannel('EMAIL'); setSendEmail(true); setError(null)
    fetchInquiryReplies(inquiry.id).then((r) => { if (live) setReplies(r) }).catch(() => { if (live) setReplies([]) })
    return () => { live = false }
  }, [inquiry?.id])

  async function submit() {
    if (!inquiry || message.trim().length < 2) return
    setBusy(true); setError(null)
    try {
      const emailing = channel === 'EMAIL' && sendEmail
      const updated = await replyToInquiry(inquiry.id, { channel, message: message.trim(), sendEmail: emailing })
      onUpdated(updated)
      setReplies(await fetchInquiryReplies(inquiry.id))
      setMessage('')
      push(emailing ? 'Email sent and logged' : 'Follow-up logged')
    } catch (e) {
      setError(e instanceof ApiError && e.message ? e.message : 'Could not save the reply.')
    } finally { setBusy(false) }
  }

  return (
    <Modal open={!!inquiry} onClose={onClose} size="lg" title={inquiry ? `Replies · ${inquiry.name}` : 'Replies'}>
      {inquiry && (
        <div className="space-y-5">
          <blockquote className="rounded-[var(--radius-md)] bg-surface-2 p-3 text-sm text-ink-muted">
            <p className="mb-1 text-xs text-ink-faint">{inquiry.name} wrote on {formatDate(inquiry.createdAt)}</p>
            <p className="whitespace-pre-line">{inquiry.message}</p>
          </blockquote>

          <section aria-label="Follow-ups so far">
            {replies === null ? <p className="text-sm text-ink-muted">Loading…</p> : replies.length === 0 ? (
              <p className="text-sm text-ink-faint">No follow-up yet.</p>
            ) : (
              <ol className="space-y-3">
                {replies.map((r) => {
                  const Icon = CHANNEL_ICON[r.channel] ?? StickyNote
                  return (
                    <li key={r.id} className="flex gap-3 text-sm">
                      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
                      <div>
                        <p className="text-xs text-ink-faint">
                          {CHANNEL_LABEL[r.channel]}{r.emailed ? ' · emailed to them' : ''} · {r.repliedByName ?? 'Staff'} · {formatDate(r.createdAt, { hour: 'numeric', minute: '2-digit' })}
                        </p>
                        <p className="whitespace-pre-line text-ink">{r.message}</p>
                      </div>
                    </li>
                  )
                })}
              </ol>
            )}
          </section>

          <form className="space-y-3 border-t border-border pt-4" onSubmit={(e) => { e.preventDefault(); submit() }}>
            <Select label="How did you reply?" value={channel} onChange={(e) => setChannel(e.target.value as ReplyChannel)}>
              {(Object.keys(CHANNEL_LABEL) as ReplyChannel[]).map((c) => <option key={c} value={c}>{CHANNEL_LABEL[c]}</option>)}
            </Select>
            <Textarea label={channel === 'EMAIL' && sendEmail ? 'Email to send' : 'What was said'} rows={4} maxLength={4000}
              value={message} onChange={(e) => setMessage(e.target.value)} />
            {channel === 'EMAIL' && (
              <label className="flex items-center gap-2 text-sm text-ink">
                <input type="checkbox" checked={sendEmail} onChange={(e) => setSendEmail(e.target.checked)} />
                Send this to {inquiry.email} now (untick to only log an email you sent elsewhere)
              </label>
            )}
            {error && <p role="alert" className="rounded-[var(--radius-md)] bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
            <div className="flex justify-end">
              <Button type="submit" loading={busy} disabled={message.trim().length < 2}>{channel === 'EMAIL' && sendEmail ? 'Send & log' : 'Log follow-up'}</Button>
            </div>
          </form>
        </div>
      )}
    </Modal>
  )
}
