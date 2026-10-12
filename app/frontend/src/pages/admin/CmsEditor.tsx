import { useEffect, useState } from 'react'
import { Image, MessageSquareQuote, FileText, Plus, Trash2, Star, Pencil } from 'lucide-react'
import { Card, Badge, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Textarea, Select } from '@/components/ui/Input'
import { Tabs } from '@/components/ui/Tabs'
import { Modal } from '@/components/ui/Modal'
import {
  fetchAdminSlides, createCmsSlide, updateCmsSlide, deleteCmsSlide,
  fetchAdminTestimonials, createTestimonial, updateTestimonial,
  fetchAdminCmsPages, saveCmsPage,
  type CmsSlide, type CmsTestimonial, type CmsPage,
} from '@/lib/api'
import { ImageField } from '@/components/shared/ImageField'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

type Tab = 'slides' | 'testimonials' | 'pages'

export default function CmsEditor() {
  const [tab, setTab] = useState<Tab>('slides')
  const push = useToastStore((s) => s.push)

  const [slides, setSlides] = useState<CmsSlide[]>([])
  const [testimonials, setTestimonials] = useState<CmsTestimonial[]>([])
  const [pages, setPages] = useState<CmsPage[]>([])
  const [showNewSlide, setShowNewSlide] = useState(false)
  const [editingSlide, setEditingSlide] = useState<CmsSlide | null>(null)
  const [showNewTestimonial, setShowNewTestimonial] = useState(false)
  const [editingTestimonial, setEditingTestimonial] = useState<CmsTestimonial | null>(null)
  const [editingPage, setEditingPage] = useState<CmsPage | null>(null)

  function reload() {
    fetchAdminSlides().then(setSlides)
    fetchAdminTestimonials().then(setTestimonials)
    fetchAdminCmsPages().then((res) => {
      // Ensure home/about/contact always show even if the backend hasn't
      // returned rows for them yet (fresh install, no admin edits made).
      const keys = ['home', 'about', 'contact']
      const byKey = new Map(res.map((p) => [p.pageKey, p]))
      setPages(keys.map((k) => byKey.get(k) ?? { pageKey: k, title: k[0].toUpperCase() + k.slice(1), content: '' }))
    })
  }
  useEffect(reload, [])

  async function handleDeleteSlide(id: string) {
    try { await deleteCmsSlide(id); push('Slide removed'); reload() }
    catch (err) { push(err instanceof ApiError ? err.message : 'Could not remove the slide.') }
  }

  return (
    <div className="space-y-5">
      <p className="max-w-2xl text-sm text-ink-muted">
        Content shown on the public site (Home, About, Contact) — the hero slide, client testimonials, and page text
        editable here are the same ones those pages read from live.
      </p>
      <Tabs tabs={[{ id: 'slides', label: 'Hero slides' }, { id: 'testimonials', label: 'Testimonials' }, { id: 'pages', label: 'Page content' }]} active={tab} onChange={setTab} />

      {tab === 'slides' && (
        <div className="space-y-4">
          <Button onClick={() => setShowNewSlide(true)}><Plus className="h-4 w-4" /> New slide</Button>
          {slides.length === 0 ? (
            <Card><EmptyState icon={<Image className="h-10 w-10" />} title="No hero slides yet" description="The public home page will show its built-in fallback copy until you add one." /></Card>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {slides.map((s) => (
                <Card key={s.id} className="overflow-hidden p-0">
                  {s.imageUrl && <img src={s.imageUrl} alt={s.title} className="h-32 w-full object-cover" />}
                  <div className="flex items-start justify-between gap-2 p-4">
                    <div>
                      <p className="font-medium text-ink">{s.title}</p>
                      {s.subtitle && <p className="mt-1 text-sm text-ink-muted">{s.subtitle}</p>}
                    </div>
                    <div className="flex shrink-0 gap-1">
                      <button onClick={() => setEditingSlide(s)} className="text-ink-faint hover:text-ink"><Pencil className="h-4 w-4" /></button>
                      <button onClick={() => handleDeleteSlide(s.id)} className="text-ink-faint hover:text-danger"><Trash2 className="h-4 w-4" /></button>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'testimonials' && (
        <div className="space-y-4">
          <Button onClick={() => setShowNewTestimonial(true)}><Plus className="h-4 w-4" /> New testimonial</Button>
          {testimonials.length === 0 ? (
            <Card><EmptyState icon={<MessageSquareQuote className="h-10 w-10" />} title="No testimonials yet" description="The public home page will show its built-in fallback reviews until you add one." /></Card>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {testimonials.map((t) => (
                <Card key={t.id} className="p-4">
                  <div className="flex gap-0.5">{Array.from({ length: t.rating }).map((_, i) => <Star key={i} className="h-3.5 w-3.5 fill-accent text-accent" />)}</div>
                  <p className="mt-2 text-sm text-ink-muted">"{t.review}"</p>
                  <p className="mt-2 text-sm font-medium text-ink">{t.clientName}{t.packageType ? ` · ${t.packageType}` : ''}</p>
                  <Button size="sm" variant="secondary" className="mt-3" onClick={() => setEditingTestimonial(t)}>Edit</Button>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'pages' && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {pages.map((p) => (
            <Card key={p.pageKey} className="p-5">
              <div className="flex items-center gap-2"><FileText className="h-4 w-4 text-ink-faint" /><p className="font-medium text-ink">{p.title}</p></div>
              <p className="mt-2 line-clamp-3 text-sm text-ink-muted">{p.content || 'No content yet — the public page shows its built-in fallback copy.'}</p>
              <Button size="sm" variant="secondary" className="mt-3" onClick={() => setEditingPage(p)}>Edit</Button>
            </Card>
          ))}
        </div>
      )}

      <NewSlideModal open={showNewSlide} onClose={() => setShowNewSlide(false)} onCreated={reload} />
      <EditSlideModal slide={editingSlide} onClose={() => setEditingSlide(null)} onSaved={reload} />
      <EditTestimonialModal testimonial={editingTestimonial} onClose={() => setEditingTestimonial(null)} onSaved={reload} />
      <NewTestimonialModal open={showNewTestimonial} onClose={() => setShowNewTestimonial(false)} onCreated={reload} />
      <EditPageModal page={editingPage} onClose={() => setEditingPage(null)} onSaved={reload} />
    </div>
  )
}

function NewSlideModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState({ title: '', subtitle: '', imageUrl: '', ctaText: '', ctaUrl: '/register' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setError(null)
    if (!form.title.trim()) { setError('Title is required.'); return }
    setSubmitting(true)
    try {
      await createCmsSlide(form)
      push('Slide added')
      onCreated()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add the slide.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New hero slide" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Adding…' : 'Add slide'}</Button></>}>
      <div className="space-y-4">
        <Input label="Title" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
        <Textarea label="Subtitle" value={form.subtitle} onChange={(e) => setForm((f) => ({ ...f, subtitle: e.target.value }))} rows={2} />
        <ImageField label="Image" value={form.imageUrl} onChange={(url) => setForm((f) => ({ ...f, imageUrl: url }))} />
        <Input label="CTA button text" value={form.ctaText} onChange={(e) => setForm((f) => ({ ...f, ctaText: e.target.value }))} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}

function EditSlideModal({ slide, onClose, onSaved }: { slide: CmsSlide | null; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState({ title: '', subtitle: '', imageUrl: '', ctaText: '', ctaUrl: '/register' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (slide) setForm({ title: slide.title, subtitle: slide.subtitle ?? '', imageUrl: slide.imageUrl ?? '', ctaText: slide.ctaText ?? '', ctaUrl: slide.ctaUrl ?? '/register' })
  }, [slide])

  if (!slide) return null

  async function handleSubmit() {
    setError(null)
    if (!form.title.trim()) { setError('Title is required.'); return }
    setSubmitting(true)
    try {
      await updateCmsSlide(slide!.id, form)
      push('Slide updated')
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update the slide.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={!!slide} onClose={onClose} title="Edit hero slide" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</Button></>}>
      <div className="space-y-4">
        <Input label="Title" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
        <Textarea label="Subtitle" value={form.subtitle} onChange={(e) => setForm((f) => ({ ...f, subtitle: e.target.value }))} rows={2} />
        <ImageField label="Image" value={form.imageUrl} onChange={(url) => setForm((f) => ({ ...f, imageUrl: url }))} />
        <Input label="CTA button text" value={form.ctaText} onChange={(e) => setForm((f) => ({ ...f, ctaText: e.target.value }))} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}

function NewTestimonialModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState({ clientName: '', review: '', rating: '5', packageType: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setError(null)
    if (!form.clientName.trim() || !form.review.trim()) { setError('Client name and review are required.'); return }
    setSubmitting(true)
    try {
      await createTestimonial({ clientName: form.clientName.trim(), review: form.review.trim(), rating: Number(form.rating), packageType: form.packageType.trim() || undefined })
      push('Testimonial added')
      onCreated()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add the testimonial.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New testimonial" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Adding…' : 'Add testimonial'}</Button></>}>
      <div className="space-y-4">
        <Input label="Client name" value={form.clientName} onChange={(e) => setForm((f) => ({ ...f, clientName: e.target.value }))} />
        <Textarea label="Review" value={form.review} onChange={(e) => setForm((f) => ({ ...f, review: e.target.value }))} rows={3} />
        <div className="grid grid-cols-2 gap-4">
          <Select label="Rating" value={form.rating} onChange={(e) => setForm((f) => ({ ...f, rating: e.target.value }))}>
            {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} stars</option>)}
          </Select>
          <Input label="Package (optional)" value={form.packageType} onChange={(e) => setForm((f) => ({ ...f, packageType: e.target.value }))} placeholder="e.g. Wedding" />
        </div>
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}

function EditPageModal({ page, onClose, onSaved }: { page: CmsPage | null; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { if (page) { setTitle(page.title); setContent(page.content ?? '') } }, [page])

  if (!page) return null

  async function handleSubmit() {
    setError(null)
    setSubmitting(true)
    try {
      await saveCmsPage(page!.pageKey, { title, content, active: true })
      push(`"${page!.title}" page updated`)
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save this page.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={!!page} onClose={onClose} title={`Edit ${page.title} page`} size="lg" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</Button></>}>
      <div className="space-y-4">
        <Input label="Page title" value={title} onChange={(e) => setTitle(e.target.value)} />
        <Textarea label="Content (one paragraph per line)" value={content} onChange={(e) => setContent(e.target.value)} rows={8} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}

function EditTestimonialModal({ testimonial, onClose, onSaved }: { testimonial: CmsTestimonial | null; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState({ clientName: '', review: '', rating: '5', packageType: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!testimonial) return
    setError(null)
    setForm({ clientName: testimonial.clientName, review: testimonial.review, rating: String(testimonial.rating), packageType: testimonial.packageType ?? '' })
  }, [testimonial])

  async function save() {
    if (!testimonial) return
    setError(null)
    if (!form.clientName.trim() || !form.review.trim()) return setError('Client name and review are required.')
    setSaving(true)
    try {
      await updateTestimonial(testimonial.id, { clientName: form.clientName.trim(), review: form.review.trim(), rating: Number(form.rating), packageType: form.packageType.trim() || undefined })
      push('Testimonial updated'); onSaved(); onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the testimonial.')
    } finally { setSaving(false) }
  }

  return (
    <Modal open={!!testimonial} onClose={onClose} title="Edit testimonial" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</Button></>}>
      <div className="space-y-4">
        <Input label="Client name" value={form.clientName} maxLength={100} onChange={(e) => setForm((f) => ({ ...f, clientName: e.target.value }))} />
        <Textarea label="Review" value={form.review} maxLength={1000} rows={4} onChange={(e) => setForm((f) => ({ ...f, review: e.target.value }))} />
        <div className="grid grid-cols-2 gap-4">
          <Select label="Rating" value={form.rating} onChange={(e) => setForm((f) => ({ ...f, rating: e.target.value }))}>
            {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} stars</option>)}
          </Select>
          <Input label="Package (optional)" value={form.packageType} onChange={(e) => setForm((f) => ({ ...f, packageType: e.target.value }))} />
        </div>
        {error && <p role="alert" className="text-sm text-danger">{error}</p>}
      </div>
    </Modal>
  )
}
