import { useEffect, useState } from 'react'
import { Mail, MessageSquareReply, Phone } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Button } from '@/components/ui/Button'
import { InquiryRepliesModal } from '@/components/contact/InquiryRepliesModal'
import { fetchContactInquiries, updateContactInquiryStatus, type ContactInquiry, type PagedResponse } from '@/lib/api'
import { formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const STATUS_TONE: Record<ContactInquiry['status'], 'warning' | 'accent' | 'success'> = {
  NEW: 'warning', CONTACTED: 'accent', CLOSED: 'success',
}

export default function ContactInquiries() {
  const push = useToastStore((s) => s.push)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<ContactInquiry> | null>(null)
  const [loading, setLoading] = useState(true)
  const [updatingId, setUpdatingId] = useState<string | null>(null)
  const [replying, setReplying] = useState<ContactInquiry | null>(null)

  function reload() {
    fetchContactInquiries({ page, size: 15 }).then((res) => { setData(res); setLoading(false) })
  }
  useEffect(reload, [page])

  async function handleStatusChange(inquiry: ContactInquiry, status: ContactInquiry['status']) {
    setUpdatingId(inquiry.id)
    try {
      await updateContactInquiryStatus(inquiry.id, status)
      setData((d) => d ? { ...d, content: d.content.map((i) => i.id === inquiry.id ? { ...i, status } : i) } : d)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not update the inquiry. Please try again.')
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <div className="space-y-5">
      <p className="text-sm text-ink-muted">Messages submitted through the public site's Contact page.</p>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="divide-y divide-border">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="p-4"><Skeleton className="h-16 w-full" /></div>)}</div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={<Mail className="h-10 w-10" />} title="No inquiries yet" description="Submissions from the public Contact page will show up here." />
        ) : (
          <>
            <div className="divide-y divide-border">
              {data.content.map((inq) => (
                <div key={inq.id} className="flex flex-col gap-3 p-5 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-ink">{inq.name}</p>
                      <Badge tone={STATUS_TONE[inq.status]}>{inq.status}</Badge>
                      <span className="text-xs text-ink-faint">{formatDate(inq.createdAt)}</span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-ink-muted">
                      <a href={`mailto:${inq.email}`} className="flex items-center gap-1 hover:text-accent"><Mail className="h-3 w-3" /> {inq.email}</a>
                      {inq.mobile && <a href={`tel:${inq.mobile}`} className="flex items-center gap-1 hover:text-accent"><Phone className="h-3 w-3" /> {inq.mobile}</a>}
                    </div>
                    <p className="mt-2 text-sm leading-relaxed text-ink-muted">{inq.message}</p>
                    <p className="mt-2 text-xs text-ink-faint">
                      {inq.replyCount ? `${inq.replyCount} follow-up${inq.replyCount > 1 ? 's' : ''} · last by ${inq.lastRepliedBy ?? 'staff'} on ${formatDate(inq.lastRepliedAt ?? inq.createdAt)}` : 'No reply yet'}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-col gap-2">
                  <Button size="sm" variant="outline" onClick={() => setReplying(inq)}><MessageSquareReply className="h-3.5 w-3.5" /> Reply</Button>
                  <Select aria-label={`Status of ${inq.name}'s inquiry`}
                    value={inq.status}
                    disabled={updatingId === inq.id}
                    onChange={(e) => handleStatusChange(inq, e.target.value as ContactInquiry['status'])}
                    className="w-36"
                  >
                    <option value="NEW">New</option>
                    <option value="CONTACTED">Contacted</option>
                    <option value="CLOSED">Closed</option>
                  </Select>
                  </div>
                </div>
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <InquiryRepliesModal inquiry={replying} onClose={() => setReplying(null)}
        onUpdated={(u) => { setReplying(u); setData((d) => d ? { ...d, content: d.content.map((i) => i.id === u.id ? u : i) } : d) }} />
    </div>
  )
}
