import { useEffect, useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { fetchProductCategories, updateProduct } from '@/lib/api'
import type { CatalogCategory, CatalogProduct } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

/** Edit a product's details, price, stock and whether it is active. The SKU is shown but fixed. */
export function EditProductModal({ product, onClose, onSaved }: { product: CatalogProduct | null; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const [categories, setCategories] = useState<CatalogCategory[]>([])
  const [form, setForm] = useState({ name: '', description: '', categoryId: '', price: '', gstRate: '', stockQuantity: '', imageUrl: '', active: true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!product) return
    setError(null)
    setForm({
      name: product.name, description: product.description ?? '', categoryId: product.categoryId ?? '',
      price: String(product.price), gstRate: product.gstRate != null ? String(product.gstRate) : '',
      stockQuantity: String(product.stockQuantity), imageUrl: product.imageUrl ?? '', active: product.active,
    })
    fetchProductCategories().then(setCategories).catch(() => setCategories([]))
  }, [product])

  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }))

  async function save() {
    if (!product) return
    const price = Number(form.price), stock = Number(form.stockQuantity)
    if (!form.name.trim()) { setError('Give the product a name.'); return }
    if (form.price === '' || !Number.isFinite(price) || price < 0) { setError('Enter a price of 0 or more.'); return }
    if (!Number.isInteger(stock) || stock < 0) { setError('Stock must be a whole number, 0 or more.'); return }
    const gst = form.gstRate === '' ? undefined : Number(form.gstRate)
    if (gst !== undefined && (!Number.isFinite(gst) || gst < 0 || gst > 100)) { setError('GST rate must be between 0 and 100.'); return }
    setSaving(true); setError(null)
    try {
      await updateProduct(product.id, {
        name: form.name.trim(), description: form.description.trim(), categoryId: form.categoryId,
        price, gstRate: gst, stockQuantity: stock, active: form.active, imageUrl: form.imageUrl.trim(),
      })
      push('Product updated')
      onSaved()
      onClose()
    } catch (e) {
      setError(e instanceof ApiError && e.message ? e.message : 'Could not save the product. Please try again.')
    } finally { setSaving(false) }
  }

  return (
    <Modal open={!!product} onClose={onClose} title="Edit product"
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={saving} onClick={save}>Save changes</Button></>}>
      <div className="grid grid-cols-1 gap-4">
        <Input label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input label="Price (₹)" type="number" min="0" value={form.price} onChange={(e) => set('price', e.target.value)} />
          <Input label="GST %" type="number" min="0" max="100" value={form.gstRate} onChange={(e) => set('gstRate', e.target.value)} />
          <Input label="Stock" type="number" min="0" value={form.stockQuantity} onChange={(e) => set('stockQuantity', e.target.value)} />
        </div>
        <Select label="Category" value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
          <option value="">Uncategorized</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
        <Input label="Image URL (optional)" type="url" value={form.imageUrl} onChange={(e) => set('imageUrl', e.target.value)} />
        <Textarea label="Description" rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} />
        <p className="text-xs text-ink-faint">SKU: <span className="font-mono">{product?.sku ?? 'none'}</span> (can't be changed)</p>
        <label className="flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
          Active — available to add to bookings
        </label>
      </div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
    </Modal>
  )
}
