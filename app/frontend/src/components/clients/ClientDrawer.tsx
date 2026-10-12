import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, MapPin, Pencil, Phone } from 'lucide-react'
import { Drawer } from '@/components/ui/Drawer'
import { Avatar, Badge, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { fetchClientOverview, invoiceForSource, updateClient, type ClientOverview, type InvoiceSourceType } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'

/** Admin client detail: contact, money, bookings, invoices, galleries — and an edit form for staff who may change it. */
export function ClientDrawer({ clientId, canEdit, onClose, onChanged }: {
  clientId: string | null
  canEdit: boolean
  onClose: () => void
  onChanged: () => void
}) {
  const push = useToastStore((s) => s.push)
  const [data, setData] = useState<ClientOverview | null>(null)
  const [error, setError] = useState(false)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', mobile: '', address: '', notes: '', marketingOptIn: false })
  const [saving, setSaving] = useState(false)
  const [creatingFor, setCreatingFor] = useState<string | null>(null)

  async function createInvoice(type: InvoiceSourceType, sourceId: string) {
    setCreatingFor(sourceId)
    try {
      await invoiceForSource(type, sourceId)
      if (clientId) setData(await fetchClientOverview(clientId))
      push('Invoice created')
      onChanged()
    } catch (e) { push(e instanceof ApiError && e.message ? e.message : 'Could not create the invoice.') }
    finally { setCreatingFor(null) }
  }

  useEffect(() => {
    if (!clientId) return
    let live = true
    setData(null); setError(false); setEditing(false)
    fetchClientOverview(clientId).then((d) => { if (live) setData(d) }).catch(() => { if (live) setError(true) })
    return () => { live = false }
  }, [clientId])

  function startEdit() {
    if (!data) return
    setForm({
      firstName: data.firstName, lastName: data.lastName ?? '', email: data.email, mobile: data.mobile ?? '',
      address: data.address ?? '', notes: data.notes ?? '', marketingOptIn: data.marketingOptIn,
    })
    setEditing(true)
  }

  async function save() {
    if (!clientId) return
    setSaving(true)
    try {
      setData(await updateClient(clientId, form))
      setEditing(false)
      push('Client updated')
      onChanged()
    } catch (e) {
      push(e instanceof ApiError && e.message ? e.message : 'Could not save the changes.')
    } finally { setSaving(false) }
  }

  const name = data ? `${data.firstName} ${data.lastName ?? ''}`.trim() : 'Client'

  return (
    <Drawer open={!!clientId} onClose={onClose} title={name} subtitle={data ? `Client since ${formatDate(data.createdAt)}` : undefined}
      footer={editing
        ? <><Button variant="outline" onClick={() => setEditing(false)}>Cancel</Button><Button loading={saving} disabled={!form.firstName.trim()} onClick={save}>Save changes</Button></>
        : canEdit && data ? <Button variant="outline" onClick={startEdit}><Pencil className="h-3.5 w-3.5" /> Edit details</Button> : undefined}>
      {error && <p className="text-sm text-danger">Could not load this client.</p>}
      {!data && !error && <div className="space-y-3"><Skeleton className="h-16" /><Skeleton className="h-32" /><Skeleton className="h-32" /></div>}

      {data && editing && (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Input label="First name" value={form.firstName} maxLength={60} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
            <Input label="Last name" value={form.lastName} maxLength={60} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          </div>
          <Input label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Input label="Mobile" type="tel" value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
          <Textarea label="Address" value={form.address} maxLength={2000} onChange={(e) => setForm({ ...form, address: e.target.value })} />
          <Textarea label="Private notes" value={form.notes} maxLength={4000} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={form.marketingOptIn} onChange={(e) => setForm({ ...form, marketingOptIn: e.target.checked })} />
            Happy to receive offers and updates
          </label>
        </div>
      )}

      {data && !editing && (
        <div className="space-y-6">
          <div className="flex items-center gap-3">
            <Avatar name={name} size={48} />
            <div className="flex flex-wrap gap-1.5">
              <Badge tone={data.status === 'ACTIVE' ? 'success' : 'neutral'}>{data.status}</Badge>
              <Badge tone={data.marketingOptIn ? 'success' : 'neutral'}>{data.marketingOptIn ? 'Opted in' : 'Opted out'}</Badge>
              {!data.emailVerified && <Badge tone="warning">Email not verified</Badge>}
            </div>
          </div>

          <ul className="space-y-1.5 text-sm text-ink-muted">
            <li className="flex items-center gap-2"><Mail className="h-4 w-4 text-ink-faint" /> <a className="hover:text-accent" href={`mailto:${data.email}`}>{data.email}</a></li>
            {data.mobile && <li className="flex items-center gap-2"><Phone className="h-4 w-4 text-ink-faint" /> <a className="hover:text-accent" href={`tel:${data.mobile}`}>{data.mobile}</a></li>}
            {data.address && <li className="flex items-start gap-2"><MapPin className="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" /> <span className="whitespace-pre-line">{data.address}</span></li>}
          </ul>

          <dl className="grid grid-cols-2 gap-3 text-sm">
            <Stat label="Bookings" value={`${data.bookingCount}`} hint={`${data.completedBookings} done · ${data.cancelledBookings} cancelled`} />
            <Stat label="Billed" value={formatCurrency(data.totalBilled)} hint={`${formatCurrency(data.totalPaid)} paid`} />
            <Stat label="Balance due" value={formatCurrency(data.balanceDue)} tone={data.balanceDue > 0 ? 'warn' : undefined} />
            <Stat label="Last shoot" value={data.lastBookingDate ? formatDate(data.lastBookingDate) : '—'} />
          </dl>

          {data.notes && (
            <section>
              <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-faint">Private notes</h3>
              <p className="whitespace-pre-line rounded-[var(--radius-md)] bg-surface-2 p-3 text-sm text-ink-muted">{data.notes}</p>
            </section>
          )}

          <Section title={`Bookings (${data.recentBookings.length})`} empty="No bookings yet." items={data.recentBookings.map((b) => (
            <div key={b.id} className="flex items-center justify-between gap-3 py-2 text-sm">
              <Link to={`/admin/bookings/${b.id}`} className="min-w-0 hover:text-accent">
                <span className="font-medium text-ink">{b.bookingNumber}</span> <span className="text-ink-faint">· {formatDate(b.bookingDate)} · {b.packageName ?? b.shootCategory ?? ''}{b.locationType ? ` · ${b.locationType === 'INDOOR' ? 'Indoor' : 'Outdoor'}` : ''}</span>
              </Link>
              <span className="flex shrink-0 items-center gap-2">
                {b.balanceAmount > 0 && <span className="text-xs text-warning">{formatCurrency(b.balanceAmount)} due</span>}
                <Badge tone="neutral">{b.status.replace('_', ' ')}</Badge>
                {b.invoiceId ? <Link to={`/admin/invoices/${b.invoiceId}`} className="text-xs text-accent hover:underline">Invoice</Link> : b.status !== 'CANCELLED' && <button type="button" disabled={creatingFor === b.id} onClick={() => createInvoice('BOOKING', b.id)} className="text-xs text-accent hover:underline disabled:opacity-50">{creatingFor === b.id ? 'Creating…' : 'Create invoice'}</button>}
              </span>
            </div>
          ))} />
          {(data.studioSessionsAndShoots?.length ?? 0) > 0 && (
            <Section title={`Studio sessions & outdoor shoots (${data.studioSessionsAndShoots!.length})`} empty="" items={data.studioSessionsAndShoots!.map((v) => (
              <div key={v.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <span className="min-w-0"><span className="font-medium text-ink">{v.number}</span> <span className="text-ink-faint">· {formatDate(v.date)} · {v.type === 'STUDIO_SESSION' ? 'Studio' : 'Outdoor'}{v.packageName ? ` · ${v.packageName}` : ''}</span></span>
                <span className="flex shrink-0 items-center gap-2">
                  {v.balanceAmount > 0 && <span className="text-xs text-warning">{formatCurrency(v.balanceAmount)} due</span>}
                  <Badge tone="neutral">{v.status.replace('_', ' ')}</Badge>
                  {v.invoiceId ? <Link to={`/admin/invoices/${v.invoiceId}`} className="text-xs text-accent hover:underline">Invoice</Link> : v.status !== 'CANCELLED' && <button type="button" disabled={creatingFor === v.id} onClick={() => createInvoice(v.type as InvoiceSourceType, v.id)} className="text-xs text-accent hover:underline disabled:opacity-50">{creatingFor === v.id ? 'Creating…' : 'Create invoice'}</button>}
                </span>
              </div>
            ))} />
          )}
          <Section title={`Invoices (${data.recentInvoices.length})`} empty="No invoices yet." items={data.recentInvoices.map((i) => (
            <Link key={i.id} to={`/admin/invoices/${i.id}`} className="flex items-center justify-between gap-3 py-2 text-sm hover:text-accent">
              <span><span className="font-medium text-ink">{i.invoiceNumber}</span> <span className="text-ink-faint">· {formatDate(i.invoiceDate)}</span></span>
              <span className="text-ink-muted">{formatCurrency(i.totalAmount)} <Badge tone={i.balanceDue > 0 ? 'warning' : 'success'}>{i.balanceDue > 0 ? `${formatCurrency(i.balanceDue)} due` : i.status}</Badge></span>
            </Link>
          ))} />
          <Section title={`Galleries (${data.galleries.length})`} empty="No galleries yet." items={data.galleries.map((g) => (
            <Link key={g.id} to={`/admin/galleries/${g.id}`} className="flex items-center justify-between gap-3 py-2 text-sm hover:text-accent">
              <span className="font-medium text-ink">{g.name}</span>
              <span className="text-xs text-ink-faint">{g.photoCount} photos · {g.status}</span>
            </Link>
          ))} />
          {(data.selections?.length ?? 0) > 0 && (
            <Section title="Photo selections" empty="" items={data.selections!.map((sel) => (
              <Link key={sel.id} to={`/admin/galleries/${sel.galleryId}`} className="flex items-center justify-between gap-3 py-2 text-sm hover:text-accent">
                <span className="font-medium text-ink">{sel.galleryName}</span>
                <span className="text-xs text-ink-faint">{sel.selectedCount}{sel.selectionLimit ? `/${sel.selectionLimit}` : ''} picked · {sel.status}</span>
              </Link>
            ))} />
          )}
        </div>
      )}
    </Drawer>
  )
}

function Stat({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: 'warn' }) {
  return (
    <div className="rounded-[var(--radius-md)] bg-surface-2 p-3">
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className={tone === 'warn' ? 'font-medium text-warning' : 'font-medium text-ink'}>{value}</dd>
      {hint && <dd className="text-xs text-ink-faint">{hint}</dd>}
    </div>
  )
}

function Section({ title, items, empty }: { title: string; items: React.ReactNode[]; empty: string }) {
  return (
    <section>
      <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-faint">{title}</h3>
      {items.length === 0 ? <p className="text-sm text-ink-faint">{empty}</p> : <div className="divide-y divide-border">{items}</div>}
    </section>
  )
}
