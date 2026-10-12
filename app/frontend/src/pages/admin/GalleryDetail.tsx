import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AlertTriangle, ArrowLeft, CloudDownload, Copy, Download, Eye, Heart, ImagePlus, Loader2, MessageCircle, Send, Trash2 } from 'lucide-react'
import { Badge } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { createSelection, downloadAllGalleryPhotos, fetchGalleryById, fetchGalleryPhotos, fetchSelectionsForGallery, publishGallery, type GalleryPhoto, type PhotoSelectionInfo } from '@/lib/api'
import type { Gallery } from '@/lib/types'
import { GALLERY_STATUS_TONE } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { GalleryAccessCard } from '@/components/gallery/GalleryAccessCard'
import { DownloadLockCard } from '@/components/gallery/DownloadLockCard'
import { UploadPanel } from '@/components/gallery/UploadPanel'
import { DriveImportModal } from '@/components/gallery/DriveImportModal'
import { OriginalsExpiryCard } from '@/components/gallery/OriginalsExpiryCard'
import { deleteGalleryPhoto, fetchUploadStatus, retryFailedPhotos, uploadFilesDirect, validateImageFile, type UploadItem, type UploadStatus } from '@/lib/galleryUpload'

export default function GalleryDetail() {
  const { id } = useParams()
  const [gallery, setGallery] = useState<Gallery | null>(null)
  const [photos, setPhotos] = useState<GalleryPhoto[]>([])
  const [publishing, setPublishing] = useState(false)
  const [uploadItems, setUploadItems] = useState<UploadItem[]>([])
  const [uploadActive, setUploadActive] = useState(false)
  const [status, setStatus] = useState<UploadStatus | null>(null)
  const [driveOpen, setDriveOpen] = useState(false)
  const [dragging, setDragging] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [selections, setSelections] = useState<PhotoSelectionInfo[]>([])
  const [creatingSel, setCreatingSel] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const push = useToastStore((s) => s.push)

  const load = useCallback(() => {
    if (!id) return
    fetchGalleryById(id).then((g) => setGallery(g ?? null))
    fetchGalleryPhotos(id, true).then(setPhotos)
    fetchSelectionsForGallery(id).then(setSelections).catch(() => setSelections([]))
  }, [id])
  useEffect(load, [load])

  // While photos are still being processed (or a Drive import runs) keep checking, and
  // refresh the grid whenever the number of finished photos changes.
  const pending = (status?.processing ?? 0) + (status?.uploading ?? 0) > 0 || status?.driveImport?.running === true
  const readyCount = status?.ready
  useEffect(() => {
    if (!id) return
    fetchUploadStatus(id).then(setStatus).catch(() => undefined)
  }, [id])
  useEffect(() => {
    if (!id || !pending) return
    const t = setInterval(() => { fetchUploadStatus(id).then(setStatus).catch(() => undefined) }, 3000)
    return () => clearInterval(t)
  }, [id, pending])
  useEffect(() => {
    if (readyCount !== undefined) load()
  }, [readyCount, load])

  async function handlePublish() {
    if (!gallery) return
    setPublishing(true)
    try {
      const updated = await publishGallery(gallery)
      setGallery(updated)
      push(`"${gallery.name}" published — the client can now view it`)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not publish the gallery. Please try again.')
    } finally {
      setPublishing(false)
    }
  }

  async function startUpload(files: File[]) {
    if (!gallery || files.length === 0) return
    const rejected: string[] = []
    const items: UploadItem[] = []
    for (const file of files) {
      const problem = validateImageFile(file)
      if (problem) rejected.push(`${file.name}: ${problem}`)
      else items.push({ id: `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2, 7)}`, file, state: 'queued', progress: 0 })
    }
    if (rejected.length > 0) push(`${rejected.length} file${rejected.length === 1 ? '' : 's'} skipped. ${rejected[0]}`, 'error')
    if (items.length === 0) return
    await runUpload(items, true)
  }

  async function runUpload(items: UploadItem[], append: boolean) {
    if (!gallery) return
    const controller = new AbortController()
    abortRef.current = controller
    setUploadActive(true)
    setUploadItems((prev) => (append ? [...prev.filter((i) => i.state !== 'done'), ...items] : prev))
    const patch = (itemId: string, change: Partial<UploadItem>) =>
      setUploadItems((prev) => prev.map((i) => (i.id === itemId ? { ...i, ...change } : i)))
    const { succeeded, failed, duplicates } = await uploadFilesDirect(gallery.id, items, patch, { signal: controller.signal })
    setUploadActive(false)
    abortRef.current = null
    if (succeeded > 0) push(`${succeeded} photo${succeeded === 1 ? '' : 's'} uploaded. Thumbnails are being prepared`)
    if (duplicates > 0) push(`${duplicates} photo${duplicates === 1 ? ' was' : 's were'} already in this gallery and skipped`)
    if (failed > 0) push(`${failed} photo${failed === 1 ? '' : 's'} failed to upload`, 'error')
    fetchUploadStatus(gallery.id).then(setStatus).catch(() => undefined)
    load()
  }

  function handleFilesSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (fileInputRef.current) fileInputRef.current.value = ''
    void startUpload(files)
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    void startUpload(Array.from(e.dataTransfer.files))
  }

  function retryUploads() {
    const failedItems = uploadItems.filter((i) => i.state === 'error' || i.state === 'cancelled')
    if (failedItems.length === 0) return
    const reset = failedItems.map((i) => ({ ...i, state: 'queued' as const, progress: 0, error: undefined }))
    setUploadItems((prev) => prev.map((i) => reset.find((r) => r.id === i.id) ?? i))
    void runUpload(reset, false)
  }

  async function handleRetryProcessing() {
    if (!gallery) return
    try {
      const n = await retryFailedPhotos(gallery.id)
      push(`Retrying ${n} photo${n === 1 ? '' : 's'}`)
      fetchUploadStatus(gallery.id).then(setStatus).catch(() => undefined)
    } catch (err) { push(err instanceof ApiError ? err.message : 'Could not retry.', 'error') }
  }

  async function handleDeletePhoto(photo: GalleryPhoto) {
    if (!window.confirm(`Delete ${photo.originalFilename}? This cannot be undone.`)) return
    try {
      await deleteGalleryPhoto(photo.id)
      push('Photo deleted')
      load()
    } catch (err) { push(err instanceof ApiError ? err.message : 'Could not delete the photo.', 'error') }
  }

  async function handleDownloadAll() {
    if (!gallery || photos.length === 0) return
    setDownloading(true)
    try {
      await downloadAllGalleryPhotos(gallery.id, gallery.name)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not download the gallery. Please try again.')
    } finally {
      setDownloading(false)
    }
  }

  const shareUrl = gallery?.accessToken ? `${window.location.origin}/g/${gallery.accessToken}` : ''

  async function copyLink() {
    try { await navigator.clipboard.writeText(shareUrl); push('Share link copied') }
    catch { push('Could not copy. Select and copy the link manually.') }
  }

  function whatsappLink() {
    const text = encodeURIComponent(`Hi ${gallery?.clientName ?? ''}, your photos are ready: ${shareUrl}\nSign in to view them and pick your favourites.`)
    window.open(`https://wa.me/?text=${text}`, '_blank', 'noopener')
  }

  async function handleCreateSelection() {
    if (!gallery) return
    setCreatingSel(true)
    try {
      await createSelection({
        galleryId: gallery.id, bookingId: gallery.bookingId, clientId: gallery.clientId, clientName: gallery.clientName,
        selectionLimit: gallery.selectionLimit,
      })
      push('Selection opened. The client can now pick photos')
      load()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not open the selection.')
    } finally { setCreatingSel(false) }
  }

  if (!gallery) return <div className="text-sm text-ink-muted">Loading…</div>

  return (
    <div className="space-y-5">
      <Link to="/admin/galleries" className="flex items-center gap-1.5 text-sm font-medium text-ink-muted hover:text-ink">
        <ArrowLeft className="h-4 w-4" /> Back to galleries
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-display text-2xl text-ink">{gallery.name}</h2>
            <Badge tone={GALLERY_STATUS_TONE[gallery.status] as any}>{gallery.status}</Badge>
          </div>
          <p className="mt-1 flex items-center gap-3 text-sm text-ink-muted">
            <span>{gallery.clientName}</span><span>·</span><span>{photos.length} photos</span>
            <span className="flex items-center gap-1"><Eye className="h-3.5 w-3.5" /> {gallery.viewCount} views</span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/tiff,image/heic,.heic,.heif,.tif,.tiff" multiple className="hidden" aria-label="Choose photos to upload" onChange={handleFilesSelected} />
          {photos.length > 0 && (
            <Button variant="outline" onClick={handleDownloadAll} disabled={downloading}>
              <Download className="h-4 w-4" /> {downloading ? 'Preparing ZIP…' : 'Download all'}
            </Button>
          )}
          <Button variant="outline" onClick={() => setDriveOpen(true)}>
            <CloudDownload className="h-4 w-4" /> Import from Drive
          </Button>
          <Button variant="outline" onClick={() => fileInputRef.current?.click()} disabled={uploadActive}>
            <ImagePlus className="h-4 w-4" /> {uploadActive ? 'Uploading…' : 'Upload photos'}
          </Button>
          {gallery.status !== 'PUBLISHED' && (
            <Button onClick={handlePublish} disabled={publishing || photos.length === 0}>
              <Send className="h-4 w-4" /> {publishing ? 'Publishing…' : 'Publish gallery'}
            </Button>
          )}
        </div>
      </div>

      {gallery.status === 'PUBLISHED' && shareUrl && (
        <div className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border p-3 sm:flex-row sm:items-center">
          <input readOnly value={shareUrl} aria-label="Share link" onFocus={(e) => e.currentTarget.select()} className="min-w-0 flex-1 rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3 py-2 text-sm text-ink" />
          <Button variant="outline" size="sm" onClick={copyLink}><Copy className="h-3.5 w-3.5" /> Copy link</Button>
          <Button variant="outline" size="sm" onClick={whatsappLink}><MessageCircle className="h-3.5 w-3.5" /> WhatsApp</Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3 rounded-[var(--radius-md)] border border-border p-3 text-sm">
        <Heart className="h-4 w-4 text-accent" />
        {selections.length === 0 ? <span className="text-ink-muted">No photo selection yet.</span> : selections.map((s) => (
          <Link key={s.id} to="/admin/selections" className="text-ink hover:underline">{s.status} · {s.selectedCount}{s.selectionLimit != null ? `/${s.selectionLimit}` : ''} photos</Link>
        ))}
        {gallery.status === 'PUBLISHED' && !selections.some((s) => s.status === 'OPEN' || s.status === 'LOCKED') && gallery.clientId && (
          <Button size="sm" variant="secondary" onClick={handleCreateSelection} loading={creatingSel}>Ask client to pick photos</Button>
        )}
      </div>

      <DownloadLockCard gallery={gallery} onChanged={setGallery} />
      <GalleryAccessCard galleryId={gallery.id} onChanged={load} />

      <UploadPanel
        items={uploadItems} active={uploadActive}
        onCancel={() => abortRef.current?.abort()} onRetryFailed={retryUploads} onClear={() => setUploadItems([])}
      />

      {(pending || (status?.failed ?? 0) > 0) && (
        <div className="flex flex-wrap items-center gap-3 rounded-[var(--radius-md)] border border-border p-3 text-sm" role="status" aria-live="polite">
          {pending && (
            <span className="flex items-center gap-2 text-ink-muted">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              Preparing {(status?.processing ?? 0) + (status?.uploading ?? 0)} photo{(status?.processing ?? 0) + (status?.uploading ?? 0) === 1 ? '' : 's'} (thumbnails and watermark)…
              {status?.driveImport?.running && ` Drive import: ${status.driveImport.done + status.driveImport.failed}/${status.driveImport.total}.`}
            </span>
          )}
          {(status?.failed ?? 0) > 0 && (
            <span className="flex items-center gap-2 text-danger">
              <AlertTriangle className="h-4 w-4" aria-hidden="true" /> {status?.failed} photo{status?.failed === 1 ? '' : 's'} could not be processed.
              <Button size="sm" variant="outline" onClick={handleRetryProcessing}>Retry</Button>
            </span>
          )}
        </div>
      )}

      <OriginalsExpiryCard
        key={`${gallery.originalsExpiryDays ?? 'none'}-${gallery.originalsExpiredAt ?? ''}`}
        gallery={gallery}
        selectedCount={selections.reduce((n, s) => n + (s.selectedCount ?? 0), 0)}
        onChanged={load}
      />

      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={dragging ? 'rounded-[var(--radius-lg)] ring-2 ring-accent' : ''}
      >
      {photos.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 rounded-[var(--radius-lg)] border border-dashed border-border py-16 text-center text-sm text-ink-faint">
          <p>No photos uploaded yet.</p>
          <Button size="sm" variant="secondary" onClick={() => fileInputRef.current?.click()}>Upload the first batch</Button>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {photos.map((photo, i) => (
            <motion.div
              key={photo.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: i * 0.03 }}
              className="group relative aspect-[4/5] overflow-hidden rounded-[var(--radius-md)] bg-surface-2"
            >
              {photo.thumbUrl || photo.url ? (
                <img src={photo.thumbUrl || photo.url} alt={photo.originalFilename} loading="lazy" className="h-full w-full object-cover transition-transform duration-400 group-hover:scale-105" />
              ) : (
                <div className="flex h-full w-full flex-col items-center justify-center gap-1 px-2 text-center text-xs text-ink-faint">
                  {photo.status === 'FAILED' ? <AlertTriangle className="h-5 w-5 text-danger" aria-hidden="true" /> : <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />}
                  <span title={photo.failureReason}>{photo.status === 'FAILED' ? (photo.failureReason ?? 'Processing failed') : 'Processing…'}</span>
                  <span className="truncate max-w-full">{photo.originalFilename}</span>
                </div>
              )}
              {photo.originalPurged && (
                <span className="absolute right-1.5 top-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white" title="Full-size original was deleted to save storage">Preview only</span>
              )}
              <button
                type="button" onClick={() => handleDeletePhoto(photo)} aria-label={`Delete ${photo.originalFilename}`}
                className="absolute bottom-1.5 right-1.5 rounded-full bg-black/60 p-1.5 text-white opacity-0 transition-opacity hover:bg-danger focus-visible:opacity-100 group-hover:opacity-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
              <div className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/20" />
              <span className="absolute left-1.5 top-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] font-medium text-white">
                #{photo.displayNumber}
              </span>
              <span className="absolute bottom-1.5 left-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white opacity-0 transition-opacity group-hover:opacity-100">
                {photo.originalFilename}
              </span>
            </motion.div>
          ))}
        </div>
      )}
      </div>

      <DriveImportModal open={driveOpen} galleryId={gallery.id} onClose={() => setDriveOpen(false)} onFinished={() => { fetchUploadStatus(gallery.id).then(setStatus).catch(() => undefined); load() }} />
    </div>
  )
}
