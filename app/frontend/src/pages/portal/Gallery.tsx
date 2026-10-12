import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, CheckCircle2, ChevronLeft, ChevronRight, Download, Heart, Images, Lock, Maximize2, Save, Unlock, X } from 'lucide-react'
import { Card, EmptyState, Skeleton, Badge } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import {
  fetchMyGalleries, fetchGalleryPhotos, downloadAllGalleryPhotos, fetchMySelections, fetchSelectedPhotos,
  saveSelectionDraft, lockSelection, downloadPhotos, fetchGalleryByToken,
  type GalleryPhoto, type PhotoSelectionInfo,
} from '@/lib/api'
import type { Gallery } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { cn } from '@/lib/utils'

export default function PortalGallery() {
  const [params, setParams] = useSearchParams()
  const linkToken = params.get('token')

  const [galleries, setGalleries] = useState<Gallery[]>([])
  const [selections, setSelections] = useState<PhotoSelectionInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [photos, setPhotos] = useState<GalleryPhoto[]>([])
  const [photosLoading, setPhotosLoading] = useState(false)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set())   // what the server has
  const [saving, setSaving] = useState(false)
  const [filter, setFilter] = useState<'all' | 'selected'>('all')
  const [lightbox, setLightbox] = useState<number | null>(null)       // index into `visible`
  const [downloading, setDownloading] = useState(false)
  const [reviewOpen, setReviewOpen] = useState(false)
  const [notes, setNotes] = useState('')
  const [locking, setLocking] = useState(false)
  const push = useToastStore((s) => s.push)

  const load = useCallback(async () => {
    setLoadError(null)
    try {
      const [g, s] = await Promise.all([fetchMyGalleries(), fetchMySelections().catch(() => [] as PhotoSelectionInfo[])])
      let list = g
      // Arrived via a share link (/g/:token): open that gallery even if it isn't in "mine".
      if (linkToken) {
        try {
          const linked = await fetchGalleryByToken(linkToken)
          if (!list.some((x) => x.id === linked.id)) list = [linked, ...list]
          setActiveId(linked.id)
        } catch (e) {
          push(e instanceof ApiError ? e.message : 'That gallery link is not valid for your account.')
        }
        setParams({}, { replace: true })
      }
      setGalleries(list); setSelections(s)
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : 'Could not load your galleries.')
    } finally { setLoading(false) }
  }, [linkToken, push, setParams])

  useEffect(() => { load() }, [load])

  const active = galleries.find((g) => g.id === activeId)
  const selection = useMemo(() => selections.find((s) => s.galleryId === activeId && s.status !== 'EXPIRED'), [selections, activeId])
  const canPick = selection?.status === 'OPEN'
  const limit = selection?.selectionLimit

  useEffect(() => {
    if (!activeId) return
    let cancelled = false
    setPhotosLoading(true)
    fetchGalleryPhotos(activeId)
      .then((res) => { if (!cancelled) setPhotos(res) })
      .catch((e) => { if (!cancelled) push(e instanceof ApiError ? e.message : 'Could not load photos.') })
      .finally(() => { if (!cancelled) setPhotosLoading(false) })
    return () => { cancelled = true }
  }, [activeId, push])

  // Restore which photos are already picked (survives reloads and shows read-only state after lock).
  useEffect(() => {
    if (!selection) { setPicked(new Set()); return }
    fetchSelectedPhotos(selection.id)
      .then((r) => { const ids = new Set(r.map((x) => x.photoId)); setPicked(ids); setSavedIds(ids) })
      .catch(() => { setPicked(new Set()); setSavedIds(new Set()) })
    setNotes(selection.clientNotes ?? '')
  }, [selection?.id, selection?.status]) // eslint-disable-line react-hooks/exhaustive-deps

  function replaceSelection(updated: PhotoSelectionInfo) {
    setSelections((all) => all.map((s) => (s.id === updated.id ? updated : s)))
  }

  const dirty = useMemo(() => {
    if (picked.size !== savedIds.size) return true
    for (const id of picked) if (!savedIds.has(id)) return true
    return false
  }, [picked, savedIds])

  /** Picking is instant and local; it is saved to the server as a draft (auto after a pause, or with the button). */
  function toggle(photoId: string) {
    if (!selection || !canPick) return
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(photoId)) { next.delete(photoId); return next }
      if (limit != null && next.size >= limit) {
        push(`You can choose up to ${limit} photos. Remove one to pick another.`)
        return prev
      }
      next.add(photoId)
      return next
    })
  }

  const pickedRef = useRef(picked); pickedRef.current = picked
  const notesRef = useRef(notes); notesRef.current = notes
  const savingRef = useRef(false)

  /** Returns true when the server has the latest picks. */
  const saveDraft = useCallback(async (quiet = false): Promise<boolean> => {
    if (!selection || savingRef.current) return false
    const ids = new Set(pickedRef.current)
    savingRef.current = true; setSaving(true)
    try {
      const updated = await saveSelectionDraft(selection.accessToken, Array.from(ids), notesRef.current.trim() || undefined)
      setSelections((all) => all.map((x) => (x.id === updated.id ? updated : x)))
      setSavedIds(ids)
      if (!quiet) push('Draft saved. You can come back and continue any time.')
      return true
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'Could not save your draft. Check your connection and try again.')
      return false
    } finally { savingRef.current = false; setSaving(false) }
  }, [selection, push])

  // Auto-save a couple of seconds after the last change, so a closed tab never loses a long session of picking.
  useEffect(() => {
    if (!canPick || !dirty) return
    const t = setTimeout(() => { void saveDraft(true) }, 2500)
    return () => clearTimeout(t)
  }, [picked, dirty, canPick, saveDraft])

  // Warn before closing the tab with unsaved picks.
  useEffect(() => {
    if (!dirty || !canPick) return
    const h = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', h)
    return () => window.removeEventListener('beforeunload', h)
  }, [dirty, canPick])

  async function leaveGallery() {
    if (dirty && canPick) await saveDraft(true)
    setLightbox(null); setFilter('all'); setActiveId(null)
  }

  async function submitSelection() {
    if (!selection) return
    setLocking(true)
    try {
      if (dirty && !(await saveDraft(true))) return
      replaceSelection(await lockSelection(selection.accessToken, notes.trim() || undefined))
      setReviewOpen(false)
      push('Selection submitted. We will take it from here!')
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'Could not submit your selection.')
    } finally { setLocking(false) }
  }

  async function handleDownloadAll() {
    if (!active) return
    setDownloading(true)
    try { await downloadAllGalleryPhotos(active.id, active.name) }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not download the gallery.') }
    finally { setDownloading(false) }
  }

  async function handleDownloadSelected() {
    setDownloading(true)
    try { await downloadPhotos(Array.from(picked)) }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not download your selected photos.') }
    finally { setDownloading(false) }
  }

  if (loading) {
    return <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="aspect-[16/10] rounded-[var(--radius-lg)]" />)}</div>
  }
  if (loadError) {
    return <Card><EmptyState icon={<Images className="h-10 w-10" />} title="Couldn't load your galleries" description={loadError} action={<Button onClick={() => { setLoading(true); load() }}>Try again</Button>} /></Card>
  }
  if (galleries.length === 0) {
    return <Card><EmptyState icon={<Images className="h-10 w-10" />} title="No galleries yet" description="We'll notify you the moment your photos are ready to view." /></Card>
  }

  if (active) {
    const statusLabel = selection?.status === 'LOCKED' ? 'Submitted: awaiting studio approval' : selection?.status === 'APPROVED' ? 'Approved by the studio' : null
    const visible = filter === 'selected' ? photos.filter((p) => picked.has(p.id)) : photos
    const savedAt = selection?.lastSavedAt ? new Date(selection.lastSavedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : null
    const lbPhoto = lightbox != null ? visible[lightbox] : undefined
    return (
      <div className="space-y-5 pb-28">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <button onClick={leaveGallery} className="flex items-center gap-1.5 text-sm font-medium text-ink-muted hover:text-ink">
            <ArrowLeft className="h-4 w-4" /> All galleries
          </button>
          <div className="flex gap-2">
            {picked.size > 0 && active.downloadAllowed && (
              <Button variant="outline" size="sm" onClick={handleDownloadSelected} disabled={downloading}><Download className="h-3.5 w-3.5" /> Download selected ({picked.size})</Button>
            )}
            {active.downloadAllowed && (
              <Button variant="outline" size="sm" onClick={handleDownloadAll} disabled={downloading || photos.length === 0}><Download className="h-3.5 w-3.5" /> {downloading ? 'Downloading…' : 'Download all'}</Button>
            )}
          </div>
        </div>
        <div>
          <h2 className="font-display text-2xl text-ink">{active.name}</h2>
          <p className="text-sm text-ink-muted">{photos.length} photos</p>
          {active.downloadAllowed ? (
            <p className="mt-1 flex items-center gap-1.5 text-sm text-success"><Unlock className="h-3.5 w-3.5" /> Your photos are unlocked: no watermark, full-quality downloads.</p>
          ) : (
            <p className="mt-1 flex items-center gap-1.5 text-sm text-ink-muted"><Lock className="h-3.5 w-3.5" /> Photos carry a watermark and downloads unlock once your invoice is fully paid.</p>
          )}
          {statusLabel && <Badge tone={selection?.status === 'APPROVED' ? 'success' : 'info'} className="mt-2"><Lock className="mr-1 h-3 w-3" /> {statusLabel}</Badge>}
          {canPick && <p className="mt-2 text-sm text-ink-muted">Tap a photo to view it large. Tap the heart to choose it{limit != null ? ` (up to ${limit})` : ''}. Your picks are saved as a draft, so you can stop and continue later; nothing goes to the studio until you submit.</p>}
        </div>

        {selection && (
          <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Show photos">
            {([['all', `All (${photos.length})`], ['selected', `Selected (${picked.size})`]] as const).map(([k, label]) => (
              <button key={k} role="tab" aria-selected={filter === k} onClick={() => { setFilter(k); setLightbox(null) }}
                className={cn('rounded-full border px-3 py-1 text-sm transition-colors', filter === k ? 'border-accent bg-accent text-accent-ink' : 'border-border text-ink-muted hover:text-ink')}>
                {label}
              </button>
            ))}
          </div>
        )}

        {photosLoading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">{Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="aspect-[4/5] rounded-[var(--radius-md)]" />)}</div>
        ) : photos.length === 0 ? (
          <Card><EmptyState icon={<Images className="h-10 w-10" />} title="No photos yet" description="Photos will appear here once the studio uploads them." /></Card>
        ) : visible.length === 0 ? (
          <Card><EmptyState icon={<Heart className="h-10 w-10" />} title="Nothing selected yet" description="Tap the heart on a photo to add it to your selection." /></Card>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {visible.map((photo, i) => {
              const isPicked = picked.has(photo.id)
              return (
                <div key={photo.id}
                  className={cn('group relative aspect-[4/5] overflow-hidden rounded-[var(--radius-md)] bg-surface-2', isPicked && 'ring-2 ring-accent')}>
                  <button type="button" onClick={() => setLightbox(i)} aria-label={`View photo ${photo.displayNumber} larger`} className="block h-full w-full">
                    {/* Small thumbnail in the grid keeps hundreds of photos fast; the big preview loads in the viewer. */}
                    <img src={photo.thumbUrl || photo.url} alt={photo.originalFilename} loading="lazy" decoding="async" draggable={false}
                      onContextMenu={(e) => { if (!active.downloadAllowed) e.preventDefault() }}
                      className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105" />
                  </button>
                  <span className="pointer-events-none absolute left-1.5 top-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] font-medium text-white">#{photo.displayNumber}</span>
                  <span className="pointer-events-none absolute bottom-1.5 left-1.5 rounded bg-black/50 p-1 text-white opacity-0 transition-opacity group-hover:opacity-100"><Maximize2 className="h-3 w-3" /></span>
                  {selection && (
                    <button type="button" onClick={() => toggle(photo.id)} disabled={!canPick}
                      aria-pressed={isPicked} aria-label={`${isPicked ? 'Remove' : 'Select'} photo ${photo.displayNumber}`}
                      className={cn('absolute right-1.5 top-1.5 flex h-9 w-9 items-center justify-center rounded-full transition-colors',
                        isPicked ? 'bg-accent text-accent-ink' : 'bg-black/50 text-white hover:bg-black/70', !canPick && 'cursor-default')}>
                      <Heart className={cn('h-4 w-4', isPicked && 'fill-current')} />
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}

        {selection && (
          <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-surface/95 px-4 py-3 backdrop-blur">
            <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm text-ink"><span className="font-medium">{picked.size}</span>{limit != null ? ` / ${limit}` : ''} selected</p>
                {canPick && (
                  <p className="text-xs text-ink-muted" aria-live="polite">
                    {saving ? 'Saving draft…' : dirty ? 'Unsaved changes' : savedAt ? `Draft saved at ${savedAt}` : picked.size > 0 ? 'Draft saved' : 'Nothing saved yet'}
                  </p>
                )}
              </div>
              {canPick ? (
                <div className="flex gap-2">
                  <Button variant="outline" onClick={() => saveDraft(false)} disabled={saving || !dirty}><Save className="h-4 w-4" /> Save draft</Button>
                  <Button onClick={() => setReviewOpen(true)} disabled={picked.size === 0}>Review &amp; submit</Button>
                </div>
              ) : (
                <span className="flex items-center gap-1.5 text-sm text-ink-muted"><CheckCircle2 className="h-4 w-4 text-success" /> {statusLabel ?? 'Selection closed'}</span>
              )}
            </div>
          </div>
        )}

        {lbPhoto && lightbox != null && (
          <Lightbox
            photo={lbPhoto} index={lightbox} total={visible.length}
            next={visible[lightbox + 1]} prev={visible[lightbox - 1]}
            picked={picked.has(lbPhoto.id)} canSelect={!!selection} canPick={canPick} locked={!active.downloadAllowed}
            onToggle={() => toggle(lbPhoto.id)}
            onNav={(d) => setLightbox((i) => (i == null ? i : Math.min(Math.max(i + d, 0), visible.length - 1)))}
            onClose={() => setLightbox(null)}
          />
        )}

        <Modal open={reviewOpen} onClose={() => setReviewOpen(false)} title="Submit your selection"
          footer={<><Button variant="outline" onClick={() => setReviewOpen(false)}>Keep choosing</Button><Button onClick={submitSelection} loading={locking}>Submit {picked.size} photos</Button></>}>
          <p className="text-sm text-ink-muted">Once submitted your selection is locked and sent to the studio. To change it afterwards, please contact us. Not ready yet? Close this and use “Save draft” instead.</p>
          <div className="mt-4"><Textarea label="Notes for the editor (optional)" maxLength={1000} rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="e.g. Please crop #12 tighter" /></div>
        </Modal>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
      {galleries.map((g, i) => {
        const sel = selections.find((s) => s.galleryId === g.id && s.status === 'OPEN')
        return (
          <motion.button key={g.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, delay: i * 0.05 }}
            onClick={() => setActiveId(g.id)} className="group text-left">
            <Card className="overflow-hidden p-0 transition-transform duration-300 hover:-translate-y-1">
              <div className="relative aspect-[16/10] overflow-hidden bg-surface-2">
                {g.coverPhotoUrl && <img src={g.coverPhotoUrl} alt={g.name} className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />}
                <div className="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent" />
                {sel && <span className="absolute right-3 top-3 rounded-full bg-accent px-2.5 py-1 text-xs font-medium text-accent-ink">Pick your favourites</span>}
                <div className="absolute bottom-4 left-4 right-4 text-white">
                  <p className="font-display text-xl">{g.name}</p>
                  <p className="text-xs text-white/70">{g.photoCount} photos</p>
                </div>
              </div>
            </Card>
          </motion.button>
        )
      })}
    </div>
  )
}

/** Full-screen viewer: large preview, previous/next (buttons, arrow keys, swipe), and select / unselect without leaving it. */
function Lightbox({ photo, index, total, next, prev, picked, canSelect, canPick, locked, onToggle, onNav, onClose }: {
  photo: GalleryPhoto; index: number; total: number; next?: GalleryPhoto; prev?: GalleryPhoto
  picked: boolean; canSelect: boolean; canPick: boolean; locked: boolean
  onToggle: () => void; onNav: (delta: number) => void; onClose: () => void
}) {
  const [loaded, setLoaded] = useState(false)
  const touchX = useRef<number | null>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => { setLoaded(false) }, [photo.id])
  useEffect(() => {
    closeRef.current?.focus()
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prevOverflow }
  }, [])
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
      else if (e.key === 'ArrowRight') onNav(1)
      else if (e.key === 'ArrowLeft') onNav(-1)
      else if ((e.key === ' ' || e.key.toLowerCase() === 's') && canSelect && canPick) { e.preventDefault(); onToggle() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, onNav, onToggle, canSelect, canPick])

  // Warm the cache for the neighbours so paging feels instant.
  useEffect(() => {
    [next, prev].forEach((p) => { if (p?.url) { const im = new Image(); im.src = p.url } })
  }, [next, prev])

  return (
    <div role="dialog" aria-modal="true" aria-label={`Photo ${photo.displayNumber}`} className="fixed inset-0 z-50 flex flex-col bg-black/95"
      onTouchStart={(e) => { touchX.current = e.touches[0].clientX }}
      onTouchEnd={(e) => {
        if (touchX.current == null) return
        const dx = e.changedTouches[0].clientX - touchX.current
        touchX.current = null
        if (Math.abs(dx) > 60) onNav(dx < 0 ? 1 : -1)
      }}>
      <div className="flex items-center justify-between gap-3 px-4 py-3 text-white">
        <p className="text-sm">#{photo.displayNumber} <span className="text-white/60">· {index + 1} of {total}</span></p>
        <div className="flex items-center gap-2">
          {canSelect && (
            <button type="button" onClick={onToggle} disabled={!canPick} aria-pressed={picked}
              className={cn('flex items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-medium transition-colors',
                picked ? 'bg-accent text-accent-ink' : 'bg-white/15 text-white hover:bg-white/25', !canPick && 'cursor-default opacity-80')}>
              <Heart className={cn('h-4 w-4', picked && 'fill-current')} /> {picked ? 'Selected' : 'Select'}
            </button>
          )}
          <button ref={closeRef} type="button" onClick={onClose} aria-label="Close viewer" className="rounded-full bg-white/15 p-2 hover:bg-white/25"><X className="h-5 w-5" /></button>
        </div>
      </div>

      <div className="relative flex min-h-0 flex-1 items-center justify-center px-2 pb-4 sm:px-14" onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
        {!loaded && <div className="absolute h-8 w-8 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden />}
        <img key={photo.id} src={photo.url} alt={photo.originalFilename} draggable={false}
          onLoad={() => setLoaded(true)}
          onContextMenu={(e) => { if (locked) e.preventDefault() }}
          className={cn('max-h-full max-w-full select-none object-contain transition-opacity duration-200', loaded ? 'opacity-100' : 'opacity-0')} />
        {prev && <button type="button" onClick={() => onNav(-1)} aria-label="Previous photo" className="absolute left-1 top-1/2 -translate-y-1/2 rounded-full bg-white/15 p-2.5 text-white hover:bg-white/25 sm:left-3"><ChevronLeft className="h-6 w-6" /></button>}
        {next && <button type="button" onClick={() => onNav(1)} aria-label="Next photo" className="absolute right-1 top-1/2 -translate-y-1/2 rounded-full bg-white/15 p-2.5 text-white hover:bg-white/25 sm:right-3"><ChevronRight className="h-6 w-6" /></button>}
      </div>
    </div>
  )
}
