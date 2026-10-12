import { useEffect, useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createProduct, fetchProductCategories, createProductCategory } from '@/lib/api'
import type { CatalogCategory } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export function NewProductModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [categories, setCategories] = useState<CatalogCategory[]>([])
  const [newCategoryName, setNewCategoryName] = useState('')
  const [addingCategory, setAddingCategory] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', description: '', sku: '', price: '', stockQuantity: '0', categoryId: '' })

  useEffect(() => { if (open) fetchProductCategories().then(setCategories) }, [open])

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }
  function reset() {
    setForm({ name: '', description: '', sku: '', price: '', stockQuantity: '0', categoryId: '' })
    setNewCategoryName(''); setAddingCategory(false); setError(null)
  }

  async function handleAddCategory() {
    if (!newCategoryName.trim()) return
    const category = await createProductCategory(newCategoryName.trim())
    setCategories((prev) => [...prev, category])
    set('categoryId', category.id)
    setNewCategoryName(''); setAddingCategory(false)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.name.trim() || !form.price) { setError('Name and price are required.'); return }
    setSubmitting(true)
    try {
      await createProduct({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        categoryId: form.categoryId || undefined,
        sku: form.sku.trim() || undefined,
        price: Number(form.price),
        stockQuantity: Number(form.stockQuantity) || 0,
      })
      push('Product created')
      onCreated()
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the product. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New product"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : 'Create product'}</Button>
      </>}
    >
      <div className="grid grid-cols-1 gap-4">
        <Input label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="e.g. 4x6 Photo Print" />
        <div className="grid grid-cols-2 gap-4">
          <Input label="SKU (optional)" value={form.sku} onChange={(e) => set('sku', e.target.value)} />
          <Input label="Price (₹)" type="number" min="0" value={form.price} onChange={(e) => set('price', e.target.value)} />
        </div>
        <Input label="Starting stock" type="number" min="0" value={form.stockQuantity} onChange={(e) => set('stockQuantity', e.target.value)} />
        {addingCategory ? (
          <div className="flex items-end gap-2">
            <Input label="New category name" value={newCategoryName} onChange={(e) => setNewCategoryName(e.target.value)} className="flex-1" />
            <Button variant="secondary" onClick={handleAddCategory}>Add</Button>
            <Button variant="ghost" onClick={() => setAddingCategory(false)}>Cancel</Button>
          </div>
        ) : (
          <div className="flex items-end gap-2">
            <Select label="Category (optional)" value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)} className="flex-1">
              <option value="">Uncategorized</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
            <Button variant="ghost" size="sm" onClick={() => setAddingCategory(true)}>+ New category</Button>
          </div>
        )}
        <Textarea label="Description (optional)" value={form.description} onChange={(e) => set('description', e.target.value)} rows={2} />
      </div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
