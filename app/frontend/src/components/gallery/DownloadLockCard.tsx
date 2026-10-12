import { useState } from 'react'
import { Lock, Unlock } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/primitives'
import { ApiError } from '@/lib/apiClient'
import { setGalleryDownloads } from '@/lib/api'
import type { Gallery } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'

/**
 * Payment lock for a gallery. While locked the client sees watermarked photos and cannot download.
 * It unlocks by itself when the booking's invoice is fully paid; this card is the manual override for
 * the cases the system cannot know about (cash paid outside the app, a goodwill release, or taking the
 * photos back after a refund).
 */
export function DownloadLockCard({ gallery, onChanged }: { gallery: Gallery; onChanged: (g: Gallery) => void }) {
  const push = useToastStore((s) => s.push)
  const [busy, setBusy] = useState(false)
  const unlocked = gallery.downloadAllowed

  async function set(allowed: boolean) {
    setBusy(true)
    try {
      onChanged(await setGalleryDownloads(gallery.id, allowed))
      push(allowed ? 'Unlocked: the client now sees clean photos and can download.' : 'Locked: the client sees watermarked photos and cannot download.')
    } catch (e) { push(e instanceof ApiError && e.message ? e.message : 'Could not change the lock.') }
    finally { setBusy(false) }
  }

  return (
    <div className="space-y-2 rounded-[var(--radius-md)] border border-border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 font-medium text-ink">
          {unlocked ? <Unlock className="h-4 w-4 text-success" /> : <Lock className="h-4 w-4 text-accent" />}
          Watermark &amp; downloads
          <Badge tone={unlocked ? 'success' : 'warning'}>{unlocked ? 'Unlocked' : 'Locked'}</Badge>
        </div>
        <Button size="sm" variant={unlocked ? 'outline' : 'primary'} loading={busy} onClick={() => set(!unlocked)}>
          {unlocked ? 'Lock again' : 'Unlock now'}
        </Button>
      </div>
      <p className="text-xs text-ink-muted">
        {unlocked
          ? 'The client sees photos without a watermark and can download them in full quality.'
          : 'The client sees watermarked photos and cannot download. This unlocks automatically once the invoice is fully paid, or you can unlock it manually here.'}
      </p>
    </div>
  )
}
