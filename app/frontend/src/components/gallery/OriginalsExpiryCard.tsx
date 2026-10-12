import { useState } from 'react'
import { HardDrive, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { ApiError } from '@/lib/apiClient'
import { forcePurgeOriginals, formatBytes, setOriginalsExpiry } from '@/lib/galleryUpload'
import type { Gallery } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'

/**
 * Storage lifecycle for full-size originals. Clients only browse small watermarked previews,
 * so originals of photos they did NOT pick can be deleted to save storage. Photos the client
 * selected are never touched.
 */
export function OriginalsExpiryCard({ gallery, selectedCount, onChanged }: {
  gallery: Gallery
  /** How many photos clients have selected across all selections of this gallery. */
  selectedCount: number
  onChanged: () => void
}) {
  const push = useToastStore((s) => s.push)
  const [days, setDays] = useState(gallery.originalsExpiryDays != null ? String(gallery.originalsExpiryDays) : '')
  const [saving, setSaving] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [understood, setUnderstood] = useState(false)
  const [purging, setPurging] = useState(false)
  const published = gallery.status === 'PUBLISHED'
  const nothingSelected = selectedCount === 0

  async function save() {
    const trimmed = days.trim()
    let value: number | null = null
    if (trimmed !== '') {
      value = Number(trimmed)
      if (!Number.isInteger(value) || value < 1 || value > 3650) { push('Enter a whole number of days between 1 and 3650, or leave it empty.', 'error'); return }
    }
    setSaving(true)
    try {
      await setOriginalsExpiry(gallery.id, value)
      push(value == null ? 'Originals will be kept until you delete them' : `Unselected originals will be deleted ${value} days after publishing`)
      onChanged()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not save the expiry setting.', 'error')
    } finally { setSaving(false) }
  }

  async function purge() {
    setPurging(true)
    try {
      const r = await forcePurgeOriginals(gallery.id, nothingSelected && understood)
      push(`${r.purgedCount} unselected original${r.purgedCount === 1 ? '' : 's'} deleted`)
      setConfirmOpen(false); setUnderstood(false)
      onChanged()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not delete the originals.', 'error')
    } finally { setPurging(false) }
  }

  return (
    <section aria-labelledby="originals-heading" className="rounded-[var(--radius-md)] border border-border p-4">
      <div className="flex items-start gap-3">
        <HardDrive className="mt-0.5 h-4 w-4 shrink-0 text-ink-muted" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <h3 id="originals-heading" className="text-sm font-medium text-ink">Original photo storage</h3>
          <p className="mt-0.5 text-xs text-ink-muted">
            Using {formatBytes(gallery.totalSizeBytes ?? 0)}. Delete the full-size originals of photos the client did not pick to save space. Selected photos are always kept; previews stay visible.
          </p>

          {gallery.originalsExpiredAt && (
            <p className="mt-2 text-xs text-ink-muted">Unselected originals were last cleaned up on {new Date(gallery.originalsExpiredAt).toLocaleDateString()}.</p>
          )}

          <div className="mt-3 flex flex-wrap items-end gap-3">
            <div className="w-56">
              <Input id="originals-days" type="number" min={1} max={3650} inputMode="numeric" label="Auto-delete after (days from publish)" placeholder="Never" value={days} onChange={(e) => setDays(e.target.value)} />
            </div>
            <Button size="sm" variant="secondary" onClick={save} loading={saving}>Save</Button>
            <Button size="sm" variant="danger" onClick={() => setConfirmOpen(true)} disabled={!published}>
              <Trash2 className="h-3.5 w-3.5" /> Delete unselected now
            </Button>
          </div>
          {!published && <p className="mt-2 text-xs text-ink-faint">Available once the gallery is published. The countdown starts at publish.</p>}
          {published && gallery.originalsExpiryDays != null && nothingSelected && (
            <p className="mt-2 text-xs text-warning">Nothing has been selected yet. The automatic clean-up will skip this gallery until the client picks photos.</p>
          )}
        </div>
      </div>

      <Modal
        open={confirmOpen} onClose={() => { setConfirmOpen(false); setUnderstood(false) }} title="Delete unselected originals?" size="sm"
        footer={<>
          <Button variant="ghost" onClick={() => { setConfirmOpen(false); setUnderstood(false) }}>Cancel</Button>
          <Button variant="danger" onClick={purge} loading={purging} disabled={nothingSelected && !understood}>Delete originals</Button>
        </>}
      >
        <div className="space-y-3 text-sm text-ink-muted">
          <p>This permanently deletes the full-size files of every photo the client has <strong className="text-ink">not</strong> selected ({selectedCount} selected will be kept). Previews remain, but these photos can no longer be downloaded in full size. This cannot be undone.</p>
          {nothingSelected && (
            <label className="flex items-start gap-2 text-ink">
              <input type="checkbox" className="mt-0.5" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
              <span>No photos have been selected, so <strong>every</strong> original in this gallery will be deleted. I understand.</span>
            </label>
          )}
        </div>
      </Modal>
    </section>
  )
}
