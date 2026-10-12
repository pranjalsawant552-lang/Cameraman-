import { useEffect, useRef, useState } from 'react'
import { ImagePlus, MessageCircleMore, Send, Users, X } from 'lucide-react'
import { Card, Badge, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { fetchAudiencePreview, fetchCampaigns, createCampaign, uploadBroadcastMedia, fetchCampaignRecipients, type BroadcastCampaign, type BroadcastRecipient } from '@/lib/api'
import { Modal } from '@/components/ui/Modal'
import { formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const STATUS_TONE: Record<string, string> = { SENT: 'success', SENDING: 'info', FAILED: 'danger', DRAFT: 'neutral' }

export default function Broadcast() {
  const push = useToastStore((s) => s.push)
  const [audience, setAudience] = useState<{ totalEligible: number; sampleNames: string[] } | null>(null)
  const [campaigns, setCampaigns] = useState<BroadcastCampaign[]>([])
  const [title, setTitle] = useState('')
  const [messageText, setMessageText] = useState('')
  const [media, setMedia] = useState<{ url: string; mediaType: string } | null>(null)
  const [uploadingMedia, setUploadingMedia] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [detail, setDetail] = useState<BroadcastCampaign | null>(null)
  const [recipients, setRecipients] = useState<BroadcastRecipient[] | null>(null)
  const [recipientsError, setRecipientsError] = useState<string | null>(null)

  function openDetail(c: BroadcastCampaign) {
    setDetail(c); setRecipients(null); setRecipientsError(null)
    fetchCampaignRecipients(c.id).then((r) => setRecipients(r.content)).catch((e) => setRecipientsError(e instanceof ApiError ? e.message : 'Could not load recipients.'))
  }

  function reload() {
    fetchAudiencePreview().then(setAudience)
    fetchCampaigns().then(setCampaigns)
  }
  useEffect(reload, [])

  async function handleMediaSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploadingMedia(true)
    try {
      setMedia(await uploadBroadcastMedia(file))
    } catch {
      push('Could not upload the image. Please try again.')
    } finally {
      setUploadingMedia(false)
    }
  }

  async function handleSend() {
    setError(null)
    if (!title.trim() || !messageText.trim()) { setError('Give the campaign a title and a message.'); return }
    if (!audience?.totalEligible) { setError('No opted-in clients to send to yet.'); return }
    setSending(true)
    try {
      const campaign = await createCampaign({
        title: title.trim(), messageText: messageText.trim(),
        mediaUrl: media?.url, mediaType: (media?.mediaType as 'IMAGE' | 'VIDEO') ?? 'NONE',
      })
      push(`"${campaign.title}" sent to ${campaign.totalRecipients} clients`)
      setTitle(''); setMessageText(''); setMedia(null)
      reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send the campaign. Please try again.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_1.2fr]">
      <Card className="h-fit p-5">
        <p className="mb-4 font-display text-lg text-ink">New campaign</p>

        <div className="mb-4 flex items-center gap-2 rounded-[var(--radius-md)] bg-surface-2 px-3.5 py-2.5 text-sm">
          <Users className="h-4 w-4 text-ink-faint" />
          <span className="text-ink-muted">
            {audience ? <>Sending to <span className="font-medium text-ink">{audience.totalEligible}</span> opted-in clients</> : 'Loading audience…'}
          </span>
        </div>

        <div className="space-y-4">
          <Input label="Campaign title (internal)" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Diwali offer 2026" />
          <Textarea label="Message" value={messageText} onChange={(e) => setMessageText(e.target.value)} rows={5} placeholder="Happy Diwali! Enjoy 20% off all portrait sessions this month..." />

          <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={handleMediaSelected} />
          {media ? (
            <div className="relative inline-block">
              <img src={media.url} alt="Attached" className="h-24 w-24 rounded-[var(--radius-md)] object-cover" />
              <button onClick={() => setMedia(null)} className="absolute -right-2 -top-2 rounded-full bg-ink p-1 text-white" aria-label="Remove image"><X className="h-3 w-3" /></button>
            </div>
          ) : (
            <Button variant="outline" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploadingMedia}>
              <ImagePlus className="h-3.5 w-3.5" /> {uploadingMedia ? 'Uploading…' : 'Attach an image (optional)'}
            </Button>
          )}

          {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}

          <Button className="w-full" onClick={handleSend} disabled={sending}>
            <Send className="h-4 w-4" /> {sending ? 'Sending…' : 'Send campaign now'}
          </Button>
          <p className="text-xs text-ink-faint">
            Sent via the official WhatsApp Business API to clients who haven't opted out. Sending can't be undone once started.
          </p>
        </div>
      </Card>

      <div>
        <p className="mb-3 font-display text-lg text-ink">Campaign history</p>
        {campaigns.length === 0 ? (
          <Card><EmptyState icon={<MessageCircleMore className="h-10 w-10" />} title="No campaigns yet" description="Sent campaigns will show delivery status here." /></Card>
        ) : (
          <div className="space-y-3">
            {campaigns.map((c) => (
              <Card key={c.id} className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-medium text-ink">{c.title}</p>
                    <p className="mt-0.5 line-clamp-2 text-sm text-ink-muted">{c.messageText}</p>
                    <p className="mt-1.5 text-xs text-ink-faint">{formatDate(c.createdAt)} · by {c.createdByName}</p>
                  </div>
                  <Badge tone={STATUS_TONE[c.status] as any}>{c.status}</Badge>
                </div>
                <div className="mt-3 flex gap-4 text-xs text-ink-faint">
                  <span>{c.totalRecipients} recipients</span>
                  <span className="text-success">{c.sentCount} sent</span>
                  {c.failedCount > 0 && <span className="text-danger">{c.failedCount} failed</span>}
                  <button type="button" onClick={() => openDetail(c)} className="ml-auto text-accent hover:underline">View recipients</button>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
      <Modal open={!!detail} onClose={() => setDetail(null)} title={detail?.title ?? ''} size="lg">
        {detail && (
          <div className="space-y-3">
            <p className="whitespace-pre-wrap rounded-[var(--radius-md)] bg-surface-2 p-3 text-sm text-ink">{detail.messageText}</p>
            <p className="text-xs text-ink-faint">{detail.totalRecipients} recipients · {detail.sentCount} sent · {detail.failedCount} failed</p>
            {recipientsError && <p role="alert" className="text-sm text-danger">{recipientsError}</p>}
            {!recipients && !recipientsError && <p className="text-sm text-ink-muted">Loading recipients…</p>}
            {recipients && recipients.length === 0 && <p className="text-sm text-ink-muted">No recipients recorded.</p>}
            {recipients && recipients.length > 0 && (
              <div className="max-h-80 overflow-y-auto rounded-[var(--radius-md)] border border-border">
                <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead className="sticky top-0 bg-surface-2 text-left text-xs uppercase tracking-wide text-ink-faint"><tr><th scope="col" className="px-3 py-2">Name</th><th scope="col" className="px-3 py-2">Mobile</th><th scope="col" className="px-3 py-2">Status</th></tr></thead>
                  <tbody className="divide-y divide-border">
                    {recipients.map((r) => (
                      <tr key={r.id}>
                        <td className="px-3 py-2 text-ink">{r.name}</td>
                        <td className="px-3 py-2 text-ink-muted">{r.mobile}</td>
                        <td className="px-3 py-2"><Badge tone={(STATUS_TONE[r.status] ?? 'neutral') as any}>{r.status}</Badge>{r.errorMessage && <span className="ml-2 text-xs text-danger">{r.errorMessage}</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
</div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
