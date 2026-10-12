import { useEffect, useRef, useState } from 'react'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ApiError } from '@/lib/apiClient'
import { fetchDriveImportConfigured, fetchUploadStatus, startDriveImport, type ImportProgress } from '@/lib/galleryUpload'

/** Imports every image in a shared Google Drive folder. The server does the work in the
 *  background; this modal shows how far along it is. */
export function DriveImportModal({ open, galleryId, onClose, onFinished }: {
  open: boolean
  galleryId: string
  onClose: () => void
  onFinished: () => void
}) {
  const [configured, setConfigured] = useState<boolean | null>(null)
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [progress, setProgress] = useState<ImportProgress | null>(null)
  const finishedRef = useRef(onFinished)
  finishedRef.current = onFinished

  useEffect(() => {
    if (!open) return
    setError(null); setProgress(null); setUrl('')
    fetchDriveImportConfigured().then(setConfigured).catch(() => setConfigured(false))
  }, [open])

  // Poll while an import is running
  useEffect(() => {
    if (!open || !progress?.running) return
    const t = setInterval(() => {
      fetchUploadStatus(galleryId).then((s) => {
        if (!s.driveImport) return
        setProgress(s.driveImport)
        if (!s.driveImport.running) finishedRef.current()
      }).catch(() => undefined)
    }, 2000)
    return () => clearInterval(t)
  }, [open, progress?.running, galleryId])

  async function start() {
    setError(null)
    if (!url.trim()) { setError('Paste the Drive folder link first.'); return }
    setBusy(true)
    try {
      const { filesQueued } = await startDriveImport(galleryId, url.trim())
      setProgress({ running: true, total: filesQueued, done: 0, failed: 0 })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the import.')
    } finally { setBusy(false) }
  }

  const pct = progress && progress.total > 0 ? Math.round(((progress.done + progress.failed + (progress.skipped ?? 0)) / progress.total) * 100) : 0

  return (
    <Modal
      open={open} onClose={onClose} title="Import from Google Drive"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{progress ? 'Close' : 'Cancel'}</Button>
        {!progress && configured && <Button onClick={start} loading={busy}>Start import</Button>}
      </>}
    >
      {configured === null ? <p className="text-sm text-ink-muted">Checking…</p>
        : !configured ? (
          <p className="text-sm text-ink-muted">
            Google Drive import isn’t set up yet. Ask your administrator to add a <code className="rounded bg-surface-2 px-1">GOOGLE_DRIVE_API_KEY</code> to the server, then reopen this dialog.
          </p>
        ) : (
          <div className="space-y-4">
            {!progress && (
              <>
                <Input id="drive-url" label="Drive folder link" placeholder="https://drive.google.com/drive/folders/…" value={url} onChange={(e) => setUrl(e.target.value)} />
                <p className="text-xs text-ink-faint">Share the folder as “Anyone with the link can view”. Only images directly inside the folder are imported, not subfolders.</p>
              </>
            )}
            {progress && (
              <div aria-live="polite">
                <p className="text-sm text-ink">
                  {progress.running ? 'Importing in the background…' : 'Import finished.'} {progress.done} of {progress.total} imported{progress.skipped ? `, ${progress.skipped} already in this gallery` : ''}{progress.failed > 0 ? `, ${progress.failed} failed` : ''}.
                </p>
                <div role="progressbar" aria-label="Drive import progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} className="mt-2 h-2 overflow-hidden rounded-full bg-surface-3">
                  <div className="h-full bg-accent transition-all" style={{ width: `${pct}%` }} />
                </div>
                <p className="mt-2 text-xs text-ink-faint">You can close this window; photos appear in the gallery as they finish processing.</p>
              </div>
            )}
            {error && <p role="alert" className="text-sm text-danger">{error}</p>}
          </div>
        )}
    </Modal>
  )
}
