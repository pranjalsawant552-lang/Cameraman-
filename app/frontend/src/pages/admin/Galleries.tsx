import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Eye, Images, Lock, Plus } from 'lucide-react'
import { motion } from 'framer-motion'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Tabs } from '@/components/ui/Tabs'
import { Button } from '@/components/ui/Button'
import { fetchGalleries } from '@/lib/api'
import type { Gallery, GalleryStatus } from '@/lib/types'
import { GALLERY_STATUS_TONE } from '@/lib/statusTone'
import { NewGalleryModal } from '@/components/gallery/NewGalleryModal'

const TABS: { id: GalleryStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' }, { id: 'DRAFT', label: 'Draft' }, { id: 'PUBLISHED', label: 'Published' }, { id: 'ARCHIVED', label: 'Archived' },
]

export default function Galleries() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<GalleryStatus | 'ALL'>('ALL')
  const [items, setItems] = useState<Gallery[]>([])
  const [loading, setLoading] = useState(true)
  const [showNewGallery, setShowNewGallery] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    setLoading(true)
    fetchGalleries({ search, status: status === 'ALL' ? '' : status, size: 20 }).then((res) => { setItems(res.content); setLoading(false) })
  }, [search, status, refreshKey])

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search galleries…" /></div>
        <div className="flex items-center gap-3">
          <Tabs tabs={TABS} active={status} onChange={setStatus} />
          <Button onClick={() => setShowNewGallery(true)}><Plus className="h-4 w-4" /> New gallery</Button>
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="aspect-[4/3] rounded-[var(--radius-lg)]" />)}
        </div>
      ) : items.length === 0 ? (
        <Card><EmptyState icon={<Images className="h-10 w-10" />} title="No galleries found" description="Try a different search term or filter." /></Card>
      ) : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((g, i) => (
            <motion.div
              key={g.id}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.35, delay: i * 0.04, ease: [0.16, 1, 0.3, 1] }}
            >
              <Link to={`/admin/galleries/${g.id}`}>
                <Card className="group overflow-hidden p-0 transition-transform duration-300 hover:-translate-y-1">
                  <div className="relative aspect-[4/3] overflow-hidden">
                    <img src={g.coverPhotoUrl} alt={g.name} className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/0 to-black/0" />
                    <div className="absolute right-3 top-3"><Badge tone={GALLERY_STATUS_TONE[g.status] as any}>{g.status}</Badge></div>
                    <div className="absolute bottom-3 left-3 right-3 text-white">
                      <p className="font-display text-lg leading-tight">{g.name}</p>
                      <p className="text-xs text-white/70">{g.clientName}</p>
                    </div>
                  </div>
                  <div className="flex items-center justify-between px-4 py-3 text-xs text-ink-faint">
                    <span>{g.photoCount} photos</span>
                    <span className="flex items-center gap-3">
                      {!g.downloadAllowed && <Lock className="h-3.5 w-3.5" />}
                      <span className="flex items-center gap-1"><Eye className="h-3.5 w-3.5" /> {g.viewCount}</span>
                    </span>
                  </div>
                </Card>
              </Link>
            </motion.div>
          ))}
        </div>
      )}

      <NewGalleryModal
        open={showNewGallery}
        onClose={() => setShowNewGallery(false)}
        onCreated={() => setRefreshKey((k) => k + 1)}
      />
    </div>
  )
}
