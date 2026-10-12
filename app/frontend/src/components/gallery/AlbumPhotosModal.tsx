import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowDown, ArrowUp, Check, Star, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Input'
import {
  fetchAlbumPhotos, fetchGalleries, fetchGalleriesForBooking, fetchGalleryPhotos, fetchSelectedPhotos,
  fetchSelectionsForGallery, setAlbumCover, setAlbumPhotos, updateAlbumPhoto,
  type Album, type AlbumPhoto, type GalleryPhoto, type PhotoSelectionInfo,
} from '@/lib/api'
import type { Gallery } from '@/lib/types'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'

const LOCKED = ['APPROVED', 'PRINTING', 'DELIVERED']

/** Move one item in a list; returns a new array (unchanged if the move is out of range). */
export function moveItem<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return list
  const copy = list.slice()
  const [item] = copy.splice(from, 1)
  copy.splice(to, 0, item)
  return copy
}

/** Choose, order and annotate the photos that go into an album. */
export function AlbumPhotosModal({ album, open, onClose, onSaved }: {
  album: Album
  open: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const push = useToastStore((s) => s.push)
  const locked = LOCKED.includes(album.status)
  const [chosen, setChosen] = useState<AlbumPhoto[]>([])
  const [galleries, setGalleries] = useState<Gallery[]>([])
  const [galleryId, setGalleryId] = useState('')
  const [available, setAvailable] = useState<GalleryPhoto[]>([])
  const [selections, setSelections] = useState<PhotoSelectionInfo[]>([])
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)
  const [dirty, setDirty] = useState(false)
  /** Where the gallery list came from, so staff understand why they are seeing these galleries. */
  const [source, setSource] = useState<'booking' | 'client' | 'all'>('booking')
  const [galleriesLoading, setGalleriesLoading] = useState(false)
  const [photosLoading, setPhotosLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true); setDirty(false)
    fetchAlbumPhotos(album.id).then(setChosen).catch(() => setChosen([])).finally(() => setLoading(false))
    // Photos live in galleries, and a gallery is often not linked to the booking the album belongs to
    // (it was created from the client, or before the booking). So look at the booking first, then the
    // client, then everything, instead of giving up with "no gallery found" at the first miss.
    setGalleriesLoading(true)
    ;(async () => {
      let list: Gallery[] = []
      let from: 'booking' | 'client' | 'all' = 'booking'
      if (album.bookingId) list = await fetchGalleriesForBooking(album.bookingId).catch(() => [] as Gallery[])
      if (list.length === 0 && album.clientName) {
        list = await fetchGalleries({ search: album.clientName, size: 50 }).then((r) => r.content).catch(() => [] as Gallery[])
        from = 'client'
      }
      if (list.length === 0) {
        list = await fetchGalleries({ size: 50 }).then((r) => r.content).catch(() => [] as Gallery[])
        from = 'all'
      }
      setGalleries(list); setSource(from); setGalleryId(list[0]?.id ?? '')
    })().finally(() => setGalleriesLoading(false))
  }, [open, album.id, album.bookingId, album.clientName])

  useEffect(() => {
    if (!galleryId) { setAvailable([]); setSelections([]); return }
    let cancelled = false
    setPhotosLoading(true)
    fetchGalleryPhotos(galleryId)
      .then((r) => { if (!cancelled) setAvailable(r) })
      .catch((err) => { if (!cancelled) { setAvailable([]); push(err instanceof ApiError ? err.message : 'Could not load that gallery’s photos.', 'error') } })
      .finally(() => { if (!cancelled) setPhotosLoading(false) })
    fetchSelectionsForGallery(galleryId).then((r) => { if (!cancelled) setSelections(r) }).catch(() => { if (!cancelled) setSelections([]) })
    return () => { cancelled = true }
  }, [galleryId, push])

  async function showAllGalleries() {
    setGalleriesLoading(true)
    try {
      const list = await fetchGalleries({ size: 100 }).then((r) => r.content)
      setGalleries(list); setSource('all'); setGalleryId(list[0]?.id ?? '')
    } catch (err) { push(err instanceof ApiError ? err.message : 'Could not load galleries.', 'error') }
    finally { setGalleriesLoading(false) }
  }

  const chosenIds = useMemo(() => new Set(chosen.map((c) => c.photoId)), [chosen])

  const toRow = useCallback((p: GalleryPhoto, position: number): AlbumPhoto => ({
    id: `new-${p.id}`, photoId: p.id, position, filename: p.originalFilename, thumbnailUrl: p.thumbUrl || p.url, cover: false,
  }), [])

  function toggle(p: GalleryPhoto) {
    if (locked) return
    setDirty(true)
    setChosen((cur) => cur.some((c) => c.photoId === p.id) ? cur.filter((c) => c.photoId !== p.id) : [...cur, toRow(p, cur.length)])
  }

  function addAll() {
    if (locked) return
    setDirty(true)
    setChosen((cur) => {
      const have = new Set(cur.map((c) => c.photoId))
      return [...cur, ...available.filter((p) => !have.has(p.id)).map((p, i) => toRow(p, cur.length + i))]
    })
  }

  async function addSelection(sel: PhotoSelectionInfo) {
    try {
      const picked = await fetchSelectedPhotos(sel.id)
      const byId = new Map(available.map((p) => [p.id, p]))
      setChosen((cur) => {
        const have = new Set(cur.map((c) => c.photoId))
        const extra = picked.filter((s) => !have.has(s.photoId) && byId.has(s.photoId)).map((s, i) => toRow(byId.get(s.photoId)!, cur.length + i))
        return [...cur, ...extra]
      })
      setDirty(true)
    } catch (err) { push(err instanceof ApiError ? err.message : 'Could not load that selection.', 'error') }
  }

  async function save() {
    setSaving(true)
    try {
      const saved = await setAlbumPhotos(album.id, chosen.map((c) => c.photoId))
      // Page numbers / notes edited locally are saved per photo
      const prev = new Map(chosen.map((c) => [c.photoId, c]))
      for (const row of saved) {
        const local = prev.get(row.photoId)
        if (local && (local.pageNumber !== row.pageNumber || (local.note ?? '') !== (row.note ?? ''))) {
          await updateAlbumPhoto(album.id, row.photoId, { pageNumber: local.pageNumber ?? null, note: local.note ?? null })
        }
      }
      const cover = chosen.find((c) => c.cover)
      if (cover) await setAlbumCover(album.id, cover.photoId)
      push(`Album photos saved (${saved.length})`)
      onSaved(); onClose()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not save the album photos.', 'error')
    } finally { setSaving(false) }
  }

  const over = album.pageCount > 0 && chosen.length > album.pageCount * 2

  return (
    <Modal
      open={open} onClose={onClose} size="lg" title={`Photos for “${album.name}”`}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Close</Button>
        {!locked && <Button onClick={save} loading={saving} disabled={!dirty}>Save photos</Button>}
      </>}
    >
      {locked && <p role="status" className="mb-3 rounded-[var(--radius-sm)] bg-warning-bg px-3 py-2 text-sm text-warning">This album is {album.status.toLowerCase()}, so its photos are locked. Request a revision to change them.</p>}
      {loading ? <p className="text-sm text-ink-muted">Loading…</p> : (
        <div className="space-y-5">
          <section aria-labelledby="in-album">
            <h4 id="in-album" className="text-sm font-medium text-ink">In this album ({chosen.length}){album.pageCount ? ` · ${album.pageCount} pages` : ''}</h4>
            {over && <p className="mt-1 text-xs text-warning">That is more than two photos per page. Consider trimming the set.</p>}
            {chosen.length === 0 ? <p className="mt-2 text-sm text-ink-faint">No photos yet. Pick from a gallery below{selections.length ? ' or add what the client selected' : ''}.</p> : (
              <ol className="mt-2 max-h-64 space-y-1.5 overflow-y-auto pr-1">
                {chosen.map((c, i) => (
                  <li key={c.photoId} className="flex items-center gap-2 rounded-[var(--radius-sm)] border border-border p-1.5 text-xs">
                    <span className="w-6 shrink-0 text-center text-ink-faint">{i + 1}</span>
                    {c.thumbnailUrl ? <img src={c.thumbnailUrl} alt="" loading="lazy" className="h-10 w-10 shrink-0 rounded object-cover" /> : <span className="h-10 w-10 shrink-0 rounded bg-surface-2" />}
                    <span className="min-w-0 flex-1 truncate text-ink" title={c.filename}>{c.filename}</span>
                    <label className="flex shrink-0 items-center gap-1 text-ink-muted">
                      <span className="sr-only">Page number for {c.filename}</span>
                      <input
                        type="number" min={1} placeholder="Page" disabled={locked} value={c.pageNumber ?? ''}
                        onChange={(e) => { setDirty(true); setChosen((cur) => cur.map((x) => x.photoId === c.photoId ? { ...x, pageNumber: e.target.value ? Number(e.target.value) : undefined } : x)) }}
                        className="h-7 w-16 rounded border border-border bg-surface-2 px-1.5 text-ink"
                      />
                    </label>
                    {!locked && <>
                      <button type="button" aria-label={`Use ${c.filename} as cover`} aria-pressed={c.cover || album.coverPhotoId === c.photoId}
                        onClick={() => { setDirty(true); setChosen((cur) => cur.map((x) => ({ ...x, cover: x.photoId === c.photoId }))) }}
                        className="rounded p-1.5 text-ink-muted hover:bg-surface-2 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
                        <Star className={`h-4 w-4 ${c.cover || album.coverPhotoId === c.photoId ? 'fill-accent text-accent' : ''}`} />
                      </button>
                      <button type="button" aria-label={`Move ${c.filename} up`} disabled={i === 0} onClick={() => { setDirty(true); setChosen((cur) => moveItem(cur, i, i - 1)) }} className="rounded p-1.5 text-ink-muted hover:bg-surface-2 disabled:opacity-30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"><ArrowUp className="h-4 w-4" /></button>
                      <button type="button" aria-label={`Move ${c.filename} down`} disabled={i === chosen.length - 1} onClick={() => { setDirty(true); setChosen((cur) => moveItem(cur, i, i + 1)) }} className="rounded p-1.5 text-ink-muted hover:bg-surface-2 disabled:opacity-30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"><ArrowDown className="h-4 w-4" /></button>
                      <button type="button" aria-label={`Remove ${c.filename}`} onClick={() => { setDirty(true); setChosen((cur) => cur.filter((x) => x.photoId !== c.photoId)) }} className="rounded p-1.5 text-ink-muted hover:bg-danger-bg hover:text-danger focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"><X className="h-4 w-4" /></button>
                    </>}
                  </li>
                ))}
              </ol>
            )}
          </section>

          {!locked && (
            <section aria-labelledby="from-gallery">
              <div className="flex flex-wrap items-end justify-between gap-3">
                <h4 id="from-gallery" className="text-sm font-medium text-ink">Add from a gallery</h4>
                <div className="flex flex-wrap items-end gap-2">
                  {galleries.length > 0 && (
                    <Select id="album-gallery" aria-label="Gallery" value={galleryId} onChange={(e) => setGalleryId(e.target.value)} className="h-8 w-56 text-xs">
                      {galleries.map((g) => <option key={g.id} value={g.id}>{g.name}{g.clientName ? ` · ${g.clientName}` : ''}</option>)}
                    </Select>
                  )}
                  {selections.map((s) => (
                    <Button key={s.id} size="sm" variant="secondary" onClick={() => addSelection(s)}>Add client’s selection ({s.selectedCount})</Button>
                  ))}
                  {available.length > 0 && <Button size="sm" variant="ghost" onClick={addAll}>Add all ({available.length})</Button>}
                </div>
              </div>
              {galleries.length > 0 && source !== 'booking' && (
                <p className="mt-2 text-xs text-ink-faint">
                  {source === 'client' ? `No gallery is linked to this booking, so these are ${album.clientName}’s galleries.` : 'No gallery found for this booking or client, so every gallery is listed.'}
                  {source !== 'all' && <> <button type="button" onClick={showAllGalleries} className="underline hover:text-ink">Show all galleries</button></>}
                </p>
              )}
              {galleriesLoading ? <p className="mt-2 text-sm text-ink-faint">Finding galleries…</p>
                : galleries.length === 0 ? (
                  <p className="mt-2 text-sm text-ink-faint">
                    There are no galleries yet. <Link to="/admin/galleries" className="underline hover:text-ink" onClick={onClose}>Create a gallery and upload photos</Link>, then come back here.
                  </p>
                ) : photosLoading ? <p className="mt-3 text-sm text-ink-faint">Loading photos…</p>
                : available.length === 0 ? (
                  <p className="mt-3 text-sm text-ink-faint">This gallery has no processed photos yet. Pick another gallery above, or wait for uploads to finish processing.</p>
                ) : (
                <ul className="mt-3 grid max-h-72 grid-cols-3 gap-2 overflow-y-auto sm:grid-cols-5 lg:grid-cols-6">
                  {available.map((p) => {
                    const on = chosenIds.has(p.id)
                    return (
                      <li key={p.id}>
                        <button
                          type="button" onClick={() => toggle(p)} aria-pressed={on} aria-label={`${on ? 'Remove' : 'Add'} ${p.originalFilename}`}
                          className={`relative block aspect-square w-full overflow-hidden rounded-[var(--radius-sm)] border-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent ${on ? 'border-accent' : 'border-transparent'}`}
                        >
                          <img src={p.thumbUrl || p.url} alt="" loading="lazy" className="h-full w-full object-cover" />
                          {on && <span className="absolute right-1 top-1 rounded-full bg-accent p-0.5 text-accent-ink"><Check className="h-3 w-3" /></span>}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          )}
        </div>
      )}
    </Modal>
  )
}
