import { useEffect, useState } from 'react'
import { Users, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/primitives'
import { ClientCombobox } from '@/components/shared/ClientCombobox'
import { fetchGalleryViewers, saveGalleryViewers, type GalleryViewer } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'

/**
 * Staff choose who can open this gallery and who takes part in photo selection. Each person who can select
 * gets their own selection, so two family members can pick independently. The gallery's own client is always
 * included and can't be removed here.
 */
export function GalleryAccessCard({ galleryId, onChanged }: { galleryId: string; onChanged?: () => void }) {
  const push = useToastStore((s) => s.push)
  const [rows, setRows] = useState<GalleryViewer[] | null>(null)
  const [search, setSearch] = useState('')
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => { fetchGalleryViewers(galleryId).then(setRows).catch(() => setRows([])) }, [galleryId])

  function update(clientId: string, patch: Partial<GalleryViewer>) {
    setRows((r) => r && r.map((x) => (x.clientId === clientId ? { ...x, ...patch } : x))); setDirty(true)
  }

  async function save() {
    if (!rows) return
    setSaving(true)
    try {
      const saved = await saveGalleryViewers(galleryId, rows.filter((r) => !r.primary).map((r) => ({ clientId: r.clientId, canSelect: r.canSelect })))
      setRows(saved); setDirty(false); push('Gallery sharing updated'); onChanged?.()
    } catch (e) { push(e instanceof ApiError && e.message ? e.message : 'Could not update sharing.') }
    finally { setSaving(false) }
  }

  return (
    <div className="space-y-3 rounded-[var(--radius-md)] border border-border p-3 text-sm">
      <div className="flex items-center gap-2 font-medium text-ink"><Users className="h-4 w-4 text-accent" /> Who can see this gallery</div>
      {rows === null ? <p className="text-ink-faint">Loading…</p> : rows.length === 0 ? <p className="text-ink-muted">No client is linked to this gallery yet. Add one below.</p> : (
        <ul className="divide-y divide-border">
          {rows.map((r) => (
            <li key={r.clientId} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span className="min-w-0">
                <span className="font-medium text-ink">{r.name}</span>{' '}
                {r.primary && <Badge tone="info">Gallery client</Badge>}
                <span className="block text-xs text-ink-faint">{r.mobile || r.email}{r.selectionStatus ? ` · selection ${r.selectionStatus.toLowerCase()} (${r.selectedCount} picked)` : ''}</span>
              </span>
              <span className="flex items-center gap-3">
                {r.primary ? <span className="text-xs text-ink-muted">Can view &amp; select</span> : (
                  <>
                    <label className="flex items-center gap-1.5 text-xs text-ink">
                      <input type="checkbox" checked={r.canSelect} onChange={(e) => update(r.clientId, { canSelect: e.target.checked })} /> Can select photos
                    </label>
                    <button type="button" aria-label={`Remove ${r.name}`} className="text-ink-faint hover:text-danger"
                      onClick={() => { setRows((x) => x && x.filter((y) => y.clientId !== r.clientId)); setDirty(true) }}><X className="h-4 w-4" /></button>
                  </>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
      <ClientCombobox label="Add another client" value={search} onChange={setSearch} placeholder="Search by name, mobile or email"
        onSelect={(c) => {
          setSearch('')
          if (rows?.some((r) => r.clientId === c.id)) return
          setRows((r) => [...(r ?? []), { clientId: c.id, name: `${c.firstName} ${c.lastName ?? ''}`.trim(), email: c.email, mobile: c.mobile, canSelect: true, primary: false, selectedCount: 0 }])
          setDirty(true)
        }} />
      <div className="flex justify-end"><Button size="sm" loading={saving} disabled={!dirty} onClick={save}>Save sharing</Button></div>
    </div>
  )
}
