import { useEffect, useState } from 'react'
import { Check, MessageCircleMore, Plus, Repeat, Sparkles, Wand2, X } from 'lucide-react'
import { Card, CardContent, Badge, EmptyState, Divider } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Tabs } from '@/components/ui/Tabs'
import { Switch } from '@/components/ui/primitives'
import { Modal } from '@/components/ui/Modal'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { fetchOutreachQueue, fetchSuggestionRules, actOnOutreach, saveSuggestionRule, toggleSuggestionRule } from '@/lib/api'
import type { SuggestedOutreach, SuggestionRule } from '@/lib/types'
import { formatDate, ordinal } from '@/lib/utils'
import { OUTREACH_STATUS_TONE, CATEGORY_TONE } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'

export default function Engagement() {
  const [tab, setTab] = useState<'queue' | 'rules'>('queue')
  const [queue, setQueue] = useState<SuggestedOutreach[]>([])
  const [rules, setRules] = useState<SuggestionRule[]>([])
  const [editingRule, setEditingRule] = useState<SuggestionRule | null>(null)
  const [editingMsg, setEditingMsg] = useState<Record<string, string>>({})
  const push = useToastStore((s) => s.push)

  function reload() {
    fetchOutreachQueue().then(setQueue)
    fetchSuggestionRules().then(setRules)
  }
  useEffect(reload, [])

  async function handleAct(o: SuggestedOutreach, action: 'approve' | 'dismiss') {
    await actOnOutreach(o.id, action, editingMsg[o.id])
    push(action === 'approve' ? `Sent to ${o.clientName} via ${o.channel}` : 'Suggestion dismissed')
    reload()
  }

  const pending = queue.filter((o) => o.status === 'PENDING_REVIEW')
  const history = queue.filter((o) => o.status !== 'PENDING_REVIEW')

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="max-w-xl text-sm text-ink-muted">
          Rules watch every booking and generate a reminder or suggestion automatically — a birthday shoot reminder every year,
          a post-delivery review request, a wedding anniversary nudge. Auto-send rules go straight out; everything else lands here for review first.
        </p>
        <div className="flex items-center gap-3">
          <Tabs tabs={[{ id: 'queue', label: 'Review queue', count: pending.length }, { id: 'rules', label: 'Rules', count: rules.length }]} active={tab} onChange={setTab} />
          {tab === 'rules' && (
            <Button onClick={() => setEditingRule(blankRule())}><Plus className="h-4 w-4" /> New rule</Button>
          )}
        </div>
      </div>

      {tab === 'queue' ? (
        <div className="space-y-5">
          {pending.length === 0 ? (
            <Card><EmptyState icon={<Sparkles className="h-10 w-10" />} title="Queue is empty" description="Nothing needs review right now — auto-sent and future suggestions will appear here." /></Card>
          ) : (
            <div className="space-y-3">
              {pending.map((o) => (
                <Card key={o.id} className="p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-accent/12 text-accent"><MessageCircleMore className="h-5 w-5" /></div>
                      <div>
                        <p className="font-medium text-ink">{o.clientName} <span className="font-normal text-ink-faint">· {o.clientMobile}</span></p>
                        <p className="text-xs text-ink-faint">{o.ruleName} · {o.occurrenceNumber > 1 && `${ordinal(o.occurrenceNumber)} occurrence · `}due {formatDate(o.triggerDate)}</p>
                      </div>
                    </div>
                    <Badge tone="accent">{o.channel}</Badge>
                  </div>
                  <Textarea
                    className="mt-3"
                    defaultValue={o.suggestedMessage}
                    onChange={(e) => setEditingMsg((m) => ({ ...m, [o.id]: e.target.value }))}
                    rows={2}
                  />
                  <div className="mt-3 flex justify-end gap-2">
                    <Button variant="outline" size="sm" onClick={() => handleAct(o, 'dismiss')}><X className="h-3.5 w-3.5" /> Dismiss</Button>
                    <Button size="sm" onClick={() => handleAct(o, 'approve')}><Check className="h-3.5 w-3.5" /> Approve & send</Button>
                  </div>
                </Card>
              ))}
            </div>
          )}

          {history.length > 0 && (
            <div>
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-faint">Recent history</p>
              <Card className="divide-y divide-border p-0">
                {history.map((o) => (
                  <div key={o.id} className="flex items-center justify-between gap-4 px-5 py-3">
                    <div>
                      <p className="text-sm text-ink">{o.clientName} <span className="text-ink-faint">— {o.ruleName}</span></p>
                      <p className="text-xs text-ink-faint">{o.sentAt ? formatDate(o.sentAt) : formatDate(o.triggerDate)}</p>
                    </div>
                    <Badge tone={OUTREACH_STATUS_TONE[o.status] as any}>{o.status.replace(/_/g, ' ')}</Badge>
                  </div>
                ))}
              </Card>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {rules.map((r) => (
            <Card key={r.id} className="p-5">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-ink">{r.name}</p>
                    {r.category && <Badge tone={CATEGORY_TONE[r.category] as any}>{r.category.replace('_', ' ')}</Badge>}
                    {!r.category && <Badge tone="neutral">All categories</Badge>}
                    {r.recurring && <Badge tone="info"><Repeat className="h-3 w-3" /> Recurring</Badge>}
                    {r.autoSend && <Badge tone="success"><Wand2 className="h-3 w-3" /> Auto-send</Badge>}
                  </div>
                  <p className="mt-2 max-w-2xl text-sm text-ink-muted">{r.messageTemplate}</p>
                  <p className="mt-2 text-xs text-ink-faint">
                    Fires {r.offsetDays >= 0 ? `${r.offsetDays} days after` : `${Math.abs(r.offsetDays)} days before`} {r.triggerType === 'RELATIVE_TO_BOOKING_DATE' ? 'the shoot date' : r.triggerType === 'RELATIVE_TO_DELIVERY_DATE' ? 'delivery' : 'the intake answer date'}, via {r.channel}
                    {r.recurring ? ' — every year' : ', once'}
                  </p>
                </div>
                <div className="flex items-center gap-4 sm:flex-col sm:items-end">
                  <Switch checked={r.active} onChange={async (v) => { await toggleSuggestionRule(r.id, v); reload() }} label="Active" />
                  <Button variant="ghost" size="sm" onClick={() => setEditingRule(r)}>Edit</Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <RuleModal
        rule={editingRule}
        onClose={() => setEditingRule(null)}
        onSave={async (r) => { await saveSuggestionRule(r); push('Rule saved'); setEditingRule(null); reload() }}
      />
    </div>
  )
}

function blankRule(): SuggestionRule {
  return {
    id: `r-${Date.now()}`, name: '', category: null, triggerType: 'RELATIVE_TO_BOOKING_DATE',
    offsetDays: -7, messageTemplate: "Hi {clientName}! It's almost {triggerDate} — want to book with {studioName} again?",
    active: true, autoSend: false, channel: 'WHATSAPP', recurring: false,
  }
}

function RuleModal({ rule, onClose, onSave }: { rule: SuggestionRule | null; onClose: () => void; onSave: (r: SuggestionRule) => void }) {
  const [draft, setDraft] = useState<SuggestionRule | null>(rule)
  useEffect(() => setDraft(rule), [rule])
  if (!draft) return null

  return (
    <Modal open={!!rule} onClose={onClose} title={rule?.name ? 'Edit rule' : 'New suggestion rule'} size="lg"
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={() => onSave(draft)}>Save rule</Button></>}>
      <div className="space-y-4">
        <Input label="Rule name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="e.g. Yearly birthday shoot reminder" />

        <div className="grid grid-cols-2 gap-4">
          <Select label="Category" value={draft.category ?? ''} onChange={(e) => setDraft({ ...draft, category: (e.target.value || null) as any })}>
            <option value="">All categories</option>
            {['WEDDING', 'PRE_WEDDING', 'MATERNITY', 'NEWBORN', 'BIRTHDAY', 'FAMILY', 'CORPORATE', 'EVENT'].map((c) => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
          </Select>
          <Select label="Channel" value={draft.channel} onChange={(e) => setDraft({ ...draft, channel: e.target.value as any })}>
            <option value="WHATSAPP">WhatsApp</option>
            <option value="SMS">SMS</option>
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Select label="Trigger" value={draft.triggerType} onChange={(e) => setDraft({ ...draft, triggerType: e.target.value as any })}>
            <option value="RELATIVE_TO_BOOKING_DATE">Relative to shoot date</option>
            <option value="RELATIVE_TO_DELIVERY_DATE">Relative to delivery date</option>
            <option value="RELATIVE_TO_ANSWER_DATE">Relative to an intake answer</option>
          </Select>
          <Input type="number" label="Offset (days, negative = before)" value={draft.offsetDays} onChange={(e) => setDraft({ ...draft, offsetDays: Number(e.target.value) })} />
        </div>

        <Textarea label="Message template" rows={3} value={draft.messageTemplate} onChange={(e) => setDraft({ ...draft, messageTemplate: e.target.value })} />
        <p className="-mt-2 text-xs text-ink-faint">
          Placeholders: <code className="rounded bg-surface-2 px-1">{'{clientName}'}</code> <code className="rounded bg-surface-2 px-1">{'{studioName}'}</code> <code className="rounded bg-surface-2 px-1">{'{triggerDate}'}</code>{' '}
          <code className="rounded bg-surface-2 px-1">{'{occurrenceOrdinal}'}</code>
        </p>

        <Divider />

        <div className="flex items-center justify-between rounded-[var(--radius-md)] border border-border p-3.5">
          <div>
            <p className="flex items-center gap-1.5 text-sm font-medium text-ink"><Repeat className="h-3.5 w-3.5" /> Recurring</p>
            <p className="text-xs text-ink-faint">Keep firing every year instead of stopping after the first suggestion.</p>
          </div>
          <Switch checked={draft.recurring} onChange={(v) => setDraft({ ...draft, recurring: v })} />
        </div>

        <div className="flex items-center justify-between rounded-[var(--radius-md)] border border-border p-3.5">
          <div>
            <p className="flex items-center gap-1.5 text-sm font-medium text-ink"><Wand2 className="h-3.5 w-3.5" /> Auto-send</p>
            <p className="text-xs text-ink-faint">Send immediately (if the client opted in) instead of queuing for review.</p>
          </div>
          <Switch checked={draft.autoSend} onChange={(v) => setDraft({ ...draft, autoSend: v })} />
        </div>
      </div>
    </Modal>
  )
}
