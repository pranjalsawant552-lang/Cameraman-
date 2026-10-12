import { useEffect, useState } from 'react'
import { AlertTriangle, Boxes, PackagePlus, Pencil, Plus, Printer, ScanBarcode } from 'lucide-react'
import { Card, Badge, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { fetchInventory, lookupInventoryByBarcode, scanInventory, scanProduct } from '@/lib/api'
import { printLabels } from '@/lib/barcode'
import { Input } from '@/components/ui/Input'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import type { InventoryItem } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { AdjustStockModal } from '@/components/inventory/AdjustStockModal'
import { InventoryItemModal } from '@/components/inventory/InventoryItemModal'

export default function Inventory() {
  const [items, setItems] = useState<InventoryItem[]>([])
  const [adjustingItem, setAdjustingItem] = useState<InventoryItem | null>(null)
  const [editingItem, setEditingItem] = useState<InventoryItem | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [barcode, setBarcode] = useState('')
  const [scanning, setScanning] = useState(false)
  // 'find' opens the adjust dialog; 'out' / 'in' change stock by one per scan with no dialog (fast, scanner-friendly).
  const [mode, setMode] = useState<'find' | 'out' | 'in'>('find')
  const [recent, setRecent] = useState<{ name: string; delta: string; left: number }[]>([])
  const push = useToastStore((st) => st.push)

  async function scan(e: React.FormEvent) {
    e.preventDefault()
    const code = barcode.trim()
    if (!code) return
    setScanning(true)
    try {
      if (mode === 'find') {
        const item = await lookupInventoryByBarcode(code)
        setAdjustingItem(item) // jump straight to adjusting the scanned item's stock
      } else {
        const dir = mode === 'out' ? 'OUT' : 'IN'
        let name: string, left: number
        try {
          const it = await scanInventory(code, dir)
          setItems((prev) => prev.map((x) => (x.id === it.id ? it : x)))
          name = it.name; left = it.quantityOnHand
        } catch (err) {
          if (!(err instanceof ApiError && err.status === 404)) throw err
          const p = await scanProduct(code, dir) // catalogue products share the same labels
          name = p.name; left = p.stockQuantity
        }
        setRecent((r) => [{ name, delta: mode === 'out' ? '−1' : '+1', left }, ...r].slice(0, 6))
        push(`${name}: ${mode === 'out' ? 'out' : 'in'} · ${left} left`)
      }
      setBarcode('')
    } catch (err) {
      push(err instanceof ApiError && err.status === 404 ? `No item has barcode ${code}.` : err instanceof ApiError ? err.message : 'Lookup failed.')
    } finally { setScanning(false) }
  }

  function reload() { fetchInventory().then(setItems) }
  useEffect(reload, [])

  const lowStock = items.filter((i) => i.quantityOnHand <= i.reorderLevel)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <form onSubmit={scan} className="flex items-end gap-2">
          <Input label="Scan or type a barcode" value={barcode} autoComplete="off" maxLength={64} onChange={(e) => setBarcode(e.target.value)} placeholder="Focus here and scan" />
          <Button type="submit" variant="outline" loading={scanning} disabled={!barcode.trim()}><ScanBarcode className="h-4 w-4" /> Find</Button>
        </form>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => printLabels(items.map((i) => ({ name: i.name, code: i.barcode || i.sku, price: formatCurrency(i.unitPrice) })))} disabled={items.length === 0}>
            <Printer className="h-4 w-4" /> Print all labels
          </Button>
          <Button onClick={() => setShowAdd(true)}><Plus className="h-4 w-4" /> New item</Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm" role="radiogroup" aria-label="Scan action">
        <span className="text-ink-muted">When I scan:</span>
        {([['find', 'Open item'], ['out', 'Take 1 out'], ['in', 'Put 1 back in']] as const).map(([m, label]) => (
          <button key={m} type="button" role="radio" aria-checked={mode === m} onClick={() => setMode(m)}
            className={cn('rounded-full border px-3 py-1', mode === m ? 'border-accent bg-accent text-white' : 'border-border text-ink-muted hover:bg-surface-2')}>{label}</button>
        ))}
      </div>
      {recent.length > 0 && (
        <Card className="p-3 text-sm">
          {recent.map((r, idx) => <div key={idx} className="flex justify-between"><span>{r.name}</span><span className="text-ink-muted">{r.delta} · {r.left} left</span></div>)}
        </Card>
      )}

      {lowStock.length > 0 && (
        <Card className="border-warning/30 bg-warning-bg/40 p-4">
          <div className="flex items-center gap-2.5 text-sm text-warning">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span><strong>{lowStock.length} item{lowStock.length > 1 ? 's' : ''}</strong> at or below reorder level — restock soon to avoid delays.</span>
          </div>
        </Card>
      )}

      {items.length === 0 ? (
        <Card><EmptyState icon={<Boxes className="h-10 w-10" />} title="No inventory items yet" /></Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-6 py-3">Item</th>
                <th scope="col" className="px-4 py-3">Barcode</th>
                <th scope="col" className="px-4 py-3">Category</th>
                <th scope="col" className="px-4 py-3">On hand</th>
                <th scope="col" className="px-4 py-3">Reorder level</th>
                <th scope="col" className="px-4 py-3">Unit price</th>
                <th scope="col" className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {items.map((i) => {
                const low = i.quantityOnHand <= i.reorderLevel
                const out = i.quantityOnHand === 0
                return (
                  <tr key={i.id} className={cn('transition-colors hover:bg-surface-2', low && 'bg-warning-bg/20')}>
                    <td className="px-6 py-3.5">
                      <div className="flex items-center gap-3">
                        {i.imageUrl && <img src={i.imageUrl} alt="" className="h-9 w-9 rounded-[var(--radius-sm)] object-cover" />}
                        <span className="font-medium text-ink">{i.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5 font-mono text-xs text-ink-muted">{i.barcode || i.sku}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{i.category.replace(/_/g, " ").toLowerCase().replace(/^./, (c) => c.toUpperCase())}</td>
                    <td className="px-4 py-3.5">
                      <span className={cn('font-medium', out ? 'text-danger' : low ? 'text-warning' : 'text-ink')}>{i.quantityOnHand}</span>
                      {out && <Badge tone="danger" className="ml-2">Out of stock</Badge>}
                      {low && !out && <Badge tone="warning" className="ml-2">Low</Badge>}
                    </td>
                    <td className="px-4 py-3.5 text-ink-muted">{i.reorderLevel}</td>
                    <td className="px-4 py-3.5 text-ink">{formatCurrency(i.unitPrice)}</td>
                    <td className="px-4 py-3.5 text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="ghost" aria-label={`Print barcode for ${i.name}`} onClick={() => printLabels([{ name: i.name, code: i.barcode || i.sku, price: formatCurrency(i.unitPrice) }])}><Printer className="h-3.5 w-3.5" /></Button>
                        <Button size="sm" variant="ghost" onClick={() => setEditingItem(i)}><Pencil className="h-3.5 w-3.5" /></Button>
                        <Button size="sm" variant="secondary" onClick={() => setAdjustingItem(i)}>
                          <PackagePlus className="h-3.5 w-3.5" /> Adjust
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
</div>
        </Card>
      )}

      <AdjustStockModal
        item={adjustingItem}
        onClose={() => setAdjustingItem(null)}
        onAdjusted={(updated) => setItems((prev) => prev.map((it) => (it.id === updated.id ? updated : it)))}
      />
      <InventoryItemModal open={showAdd} editing={null} onClose={() => setShowAdd(false)} onSaved={reload} />
      <InventoryItemModal open={!!editingItem} editing={editingItem} onClose={() => setEditingItem(null)} onSaved={reload} />
    </div>
  )
}
