import { useEffect, useState } from 'react'
import { Select, Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createRecommendationRule, fetchProducts } from '@/lib/api'
import type { CatalogProduct, CategoryRecommendationRule } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const CATEGORIES = ['STUDIO', 'WEDDING', 'PRE_WEDDING', 'MATERNITY', 'NEWBORN', 'BIRTHDAY', 'FAMILY', 'CORPORATE', 'EVENT']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (rule: CategoryRecommendationRule) => void
}

export function AddRecommendationRuleModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [products, setProducts] = useState<CatalogProduct[]>([])
  const [category, setCategory] = useState('WEDDING')
  const [productId, setProductId] = useState('')
  const [priority, setPriority] = useState('1')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { if (open) fetchProducts().then((res) => { setProducts(res); setProductId(res[0]?.id ?? '') }) }, [open])

  function reset() {
    setCategory('WEDDING'); setProductId(''); setPriority('1'); setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!productId) { setError('Add a product in the catalog first, then pick it here.'); return }
    setSubmitting(true)
    try {
      const rule = await createRecommendationRule({ category, productId, priority: Number(priority) || 1 })
      push('Recommendation rule added')
      onCreated(rule)
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add the rule. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="Add recommendation rule"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Adding…' : 'Add rule'}</Button>
      </>}
    >
      <div className="space-y-4">
        <Select label="Shoot category" value={category} onChange={(e) => setCategory(e.target.value)}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
        </Select>
        {products.length === 0 ? (
          <p className="rounded-[var(--radius-md)] bg-surface-2 px-3.5 py-2.5 text-sm text-ink-muted">
            No products in your catalog yet — add one under Packages &amp; Products first.
          </p>
        ) : (
          <Select label="Product to suggest" value={productId} onChange={(e) => setProductId(e.target.value)}>
            {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </Select>
        )}
        <Input label="Priority (lower shows first)" type="number" min="1" value={priority} onChange={(e) => setPriority(e.target.value)} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
