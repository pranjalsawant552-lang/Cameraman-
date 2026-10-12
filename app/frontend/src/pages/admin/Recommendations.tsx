import { useEffect, useState } from 'react'
import { Gift, Plus, Trash2 } from 'lucide-react'
import { Card, Badge, Switch, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { fetchRecommendationRules, toggleRecommendationRule, deleteRecommendationRule } from '@/lib/api'
import type { CategoryRecommendationRule } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { CATEGORY_TONE } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'
import { AddRecommendationRuleModal } from '@/components/recommendations/AddRecommendationRuleModal'
import { ApiError } from '@/lib/apiClient'

export default function Recommendations() {
  const [rules, setRules] = useState<CategoryRecommendationRule[]>([])
  const [showAddRule, setShowAddRule] = useState(false)
  const push = useToastStore((s) => s.push)

  function reload() {
    fetchRecommendationRules().then(setRules)
  }
  useEffect(reload, [])

  async function handleToggle(rule: CategoryRecommendationRule) {
    const nextActive = !rule.active
    setRules((prev) => prev.map((r) => (r.id === rule.id ? { ...r, active: nextActive } : r))) // optimistic
    try {
      await toggleRecommendationRule(rule, nextActive)
    } catch (err) {
      setRules((prev) => prev.map((r) => (r.id === rule.id ? { ...r, active: rule.active } : r))) // roll back
      push(err instanceof ApiError ? err.message : 'Could not update the rule. Please try again.')
    }
  }

  async function handleDelete(rule: CategoryRecommendationRule) {
    setRules((prev) => prev.filter((r) => r.id !== rule.id)) // optimistic
    try {
      await deleteRecommendationRule(rule.id)
      push('Rule removed')
    } catch (err) {
      setRules((prev) => [...prev, rule]) // roll back
      push(err instanceof ApiError ? err.message : 'Could not remove the rule. Please try again.')
    }
  }

  const grouped = rules.reduce<Record<string, CategoryRecommendationRule[]>>((acc, r) => {
    (acc[r.category] ??= []).push(r)
    return acc
  }, {})

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="max-w-2xl text-sm text-ink-muted">
          When staff open a booking, these products are suggested as add-ons for its shoot category — a leather album for weddings,
          a milestone frame for newborns. Package upgrade suggestions are computed automatically from your live pricing, no setup needed here.
        </p>
        <Button onClick={() => setShowAddRule(true)}><Plus className="h-4 w-4" /> Add rule</Button>
      </div>

      {rules.length === 0 ? (
        <Card><EmptyState icon={<Gift className="h-10 w-10" />} title="No recommendation rules yet" description="Add a product to a shoot category to start suggesting it as an upsell." /></Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {Object.entries(grouped).map(([category, items]) => (
            <Card key={category} className="p-5">
              <Badge tone={CATEGORY_TONE[category] as any} className="mb-3">{category.replace('_', ' ')}</Badge>
              <div className="space-y-2">
                {items.sort((a, b) => a.priority - b.priority).map((r) => (
                  <div key={r.id} className="flex items-center justify-between gap-3 rounded-[var(--radius-md)] border border-border p-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">{r.productName}</p>
                      <p className="text-xs text-ink-faint">{formatCurrency(r.productPrice)} · priority {r.priority}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Switch checked={r.active} onChange={() => handleToggle(r)} />
                      <button className="text-ink-faint transition-colors hover:text-danger" onClick={() => handleDelete(r)}>
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          ))}
        </div>
      )}

      <AddRecommendationRuleModal open={showAddRule} onClose={() => setShowAddRule(false)} onCreated={reload} />
    </div>
  )
}
