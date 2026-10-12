import { useEffect, useState } from 'react'
import { Package, Sparkles } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { fetchRecommendationsForBooking } from '@/lib/api'
import type { PackageUpgradeSuggestion, ProductSuggestion } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'

export default function PortalRecommended() {
  const [addOns, setAddOns] = useState<ProductSuggestion[]>([])
  const [upgrades, setUpgrades] = useState<PackageUpgradeSuggestion[]>([])
  const push = useToastStore((s) => s.push)

  useEffect(() => { fetchRecommendationsForBooking('b1').then((r) => { setAddOns(r.productAddOns); setUpgrades(r.packageUpgrades) }) }, [])

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-ink-muted">
        <Sparkles className="h-4 w-4 text-accent" />
        <p className="text-sm">Picked based on the kind of session you booked with us.</p>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        {addOns.map((p) => (
          <Card key={p.productId} className="overflow-hidden p-0">
            <img src={p.imageUrl} alt={p.productName} className="h-44 w-full object-cover" />
            <CardContent className="flex items-center justify-between p-4">
              <div>
                <p className="font-medium text-ink">{p.productName}</p>
                <p className="text-sm text-ink-muted">{formatCurrency(p.price)}</p>
              </div>
              <Button size="sm" onClick={() => push(`Added ${p.productName} to your request`)}>Add</Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {upgrades.length > 0 && (
        <div>
          <p className="mb-3 text-sm font-medium text-ink">Package upgrades</p>
          {upgrades.map((u) => (
            <Card key={u.packageId} className="flex items-center justify-between border-dashed border-accent/40 bg-accent/5 p-5">
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-[var(--radius-md)] bg-accent/15 text-accent"><Package className="h-5 w-5" /></div>
                <div>
                  <p className="font-medium text-ink">{u.packageName}</p>
                  <p className="text-sm text-ink-muted">Upgrade for +{formatCurrency(u.priceDifference)}</p>
                </div>
              </div>
              <Button variant="secondary" onClick={() => push('Upgrade request sent to the studio')}>Request upgrade</Button>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
