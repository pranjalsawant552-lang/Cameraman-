import { useEffect, useState } from 'react'
import { Archive, Copy, Package, Pencil, Plus, Printer, ShoppingBag } from 'lucide-react'
import { printLabels } from '@/lib/barcode'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Tabs } from '@/components/ui/Tabs'
import { Button } from '@/components/ui/Button'
import { fetchPackages, fetchProducts, archivePackage, clonePackage } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import type { CatalogPackage, CatalogProduct } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { NewPackageModal } from '@/components/catalog/NewPackageModal'
import { NewProductModal } from '@/components/catalog/NewProductModal'
import { EditProductModal } from '@/components/catalog/EditProductModal'
import { useToastStore } from '@/store/useToastStore'

type Tab = 'packages' | 'products'

export default function Catalog() {
  const [tab, setTab] = useState<Tab>('packages')
  const [packages, setPackages] = useState<CatalogPackage[]>([])
  const [products, setProducts] = useState<CatalogProduct[]>([])
  const [loading, setLoading] = useState(true)
  const [showNewPackage, setShowNewPackage] = useState(false)
  const [showNewProduct, setShowNewProduct] = useState(false)
  const [editingProduct, setEditingProduct] = useState<CatalogProduct | null>(null)
  const push = useToastStore((s) => s.push)

  function reload() {
    setLoading(true)
    Promise.all([fetchPackages(), fetchProducts()]).then(([p, prod]) => {
      setPackages(p); setProducts(prod); setLoading(false)
    })
  }
  useEffect(reload, [])

  async function handleArchive(id: string) {
    try { await archivePackage(id); push('Package archived'); reload() }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not archive the package.') }
  }

  async function handleClone(id: string) {
    try { await clonePackage(id); push('Package duplicated. Edit the copy to rename it'); reload() }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not duplicate the package.') }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs
          tabs={[{ id: 'packages', label: 'Packages', count: packages.length }, { id: 'products', label: 'Products', count: products.length }]}
          active={tab}
          onChange={setTab}
        />
        {tab === 'packages' ? (
          <Button onClick={() => setShowNewPackage(true)}><Plus className="h-4 w-4" /> New package</Button>
        ) : (
          <Button onClick={() => setShowNewProduct(true)}><Plus className="h-4 w-4" /> New product</Button>
        )}
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-[var(--radius-lg)]" />)}
        </div>
      ) : tab === 'packages' ? (
        packages.length === 0 ? (
          <Card><EmptyState icon={<Package className="h-10 w-10" />} title="No packages yet" description="Create your first photography package to start pricing bookings from a real catalog instead of typing amounts by hand." /></Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {packages.map((p) => (
              <Card key={p.id} className="p-5">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-ink">{p.name}</p>
                    <Badge tone="neutral" className="mt-1">{p.category.replace('_', ' ')}</Badge>
                  </div>
                  <button onClick={() => handleClone(p.id)} className="rounded-md p-1.5 text-ink-faint hover:bg-surface-2 hover:text-ink" aria-label="Duplicate package" title="Duplicate">
                    <Copy className="h-4 w-4" />
                  </button>
                  <button onClick={() => handleArchive(p.id)} className="rounded-md p-1.5 text-ink-faint hover:bg-surface-2 hover:text-danger" aria-label="Archive package" title="Archive">
                    <Archive className="h-4 w-4" />
                  </button>
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  {p.discountedPrice && p.discountedPrice < p.basePrice ? (
                    <>
                      <span className="text-lg font-medium text-ink">{formatCurrency(p.discountedPrice)}</span>
                      <span className="text-sm text-ink-faint line-through">{formatCurrency(p.basePrice)}</span>
                    </>
                  ) : (
                    <span className="text-lg font-medium text-ink">{formatCurrency(p.basePrice)}</span>
                  )}
                </div>
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-faint">
                  {p.durationHours != null && <span>{p.durationHours}h shoot</span>}
                  {p.photoCount != null && <span>{p.photoCount} photos</span>}
                  {p.deliveryDays != null && <span>{p.deliveryDays}-day delivery</span>}
                </div>
              </Card>
            ))}
          </div>
        )
      ) : products.length === 0 ? (
        <Card><EmptyState icon={<ShoppingBag className="h-10 w-10" />} title="No products yet" description="Add prints, albums, frames or other studio products with a real, admin-controlled price." /></Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <caption className="sr-only">Products</caption>
            <thead>
              <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-6 py-3">Product</th>
                <th scope="col" className="px-4 py-3">SKU / barcode</th>
                <th scope="col" className="px-4 py-3">Category</th>
                <th scope="col" className="px-4 py-3">Price</th>
                <th scope="col" className="px-4 py-3">Stock</th>
                <th scope="col" className="px-4 py-3"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {products.map((p) => (
                <tr key={p.id} className="transition-colors hover:bg-surface-2">
                  <td className="px-6 py-3.5 font-medium text-ink">{p.name} {!p.active && <Badge tone="neutral">Inactive</Badge>}</td>
                  <td className="px-4 py-3.5 font-mono text-xs text-ink-muted">{p.sku ?? '—'}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{p.categoryName ?? '—'}</td>
                  <td className="px-4 py-3.5 text-ink">{formatCurrency(p.price)}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{p.stockQuantity}</td>
                  <td className="px-4 py-3.5 text-right">
                    <Button variant="ghost" size="icon" aria-label={`Print barcode for ${p.name}`} disabled={!p.sku}
                      onClick={() => printLabels([{ name: p.name, code: p.sku!, price: formatCurrency(p.price) }])}><Printer className="h-4 w-4" /></Button>
                    <Button variant="ghost" size="icon" aria-label={`Edit ${p.name}`} onClick={() => setEditingProduct(p)}><Pencil className="h-4 w-4" /></Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </Card>
      )}

      <NewPackageModal open={showNewPackage} onClose={() => setShowNewPackage(false)} onCreated={reload} />
      <EditProductModal product={editingProduct} onClose={() => setEditingProduct(null)} onSaved={reload} />
      <NewProductModal open={showNewProduct} onClose={() => setShowNewProduct(false)} onCreated={reload} />
    </div>
  )
}
