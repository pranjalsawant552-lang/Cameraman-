import { useRef, useState } from 'react'
import { Upload } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { uploadCmsMedia } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

/** Shared image field for any admin form that takes an "image URL" (hero
 *  slides, studio logo, watermark): a URL input plus an upload button that
 *  fills it in with a real hosted URL once the file finishes uploading, so
 *  pasting a link is an option rather than the only option. */
export function ImageField({ label, value, onChange }: { label: string; value: string; onChange: (url: string) => void }) {
  const push = useToastStore((s) => s.push)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const { url } = await uploadCmsMedia(file)
      onChange(url)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not upload the image.')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  return (
    <div>
      <div className="flex items-end gap-2">
        <Input label={label} value={value} onChange={(e) => onChange(e.target.value)} placeholder="https://…" className="flex-1" />
        <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="hidden" onChange={handleFile} />
        <Button type="button" variant="outline" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
          <Upload className="h-3.5 w-3.5" /> {uploading ? 'Uploading…' : 'Upload'}
        </Button>
      </div>
      {value && <img src={value} alt="" className="mt-2 h-24 w-full rounded-[var(--radius-md)] object-cover" />}
    </div>
  )
}
