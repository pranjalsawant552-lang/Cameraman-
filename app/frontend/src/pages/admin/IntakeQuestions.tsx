import { useCallback, useEffect, useMemo, useState } from 'react'
import { ClipboardList, Pencil, Plus } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton, Switch } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import {
  createIntakeQuestion, fetchAllIntakeQuestions, setIntakeQuestionActive, updateIntakeQuestion,
  type IntakeFieldType, type IntakeQuestion, type IntakeQuestionInput,
} from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import { categoryLabel } from '@/lib/bookingUtils'

const CATEGORIES = ['CASUAL', 'WEDDING', 'PRE_WEDDING', 'BIRTHDAY', 'CORPORATE', 'MATERNITY', 'PORTFOLIO', 'NEWBORN', 'EVENT', 'FAMILY', 'PRODUCT', 'OTHER']
const TYPES: IntakeFieldType[] = ['TEXT', 'NUMBER', 'DATE', 'SELECT']
const EMPTY: IntakeQuestionInput = { category: 'WEDDING', questionKey: '', questionText: '', fieldType: 'TEXT', options: '', required: false, sortOrder: 0 }

const slug = (t: string) => t.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 50)

export default function IntakeQuestions() {
  const push = useToastStore((s) => s.push)
  const [rows, setRows] = useState<IntakeQuestion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [category, setCategory] = useState('WEDDING')
  const [editing, setEditing] = useState<IntakeQuestion | null>(null)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<IntakeQuestionInput>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setLoading(true); setError(null)
    fetchAllIntakeQuestions().then(setRows).catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load questions.')).finally(() => setLoading(false))
  }, [])
  useEffect(load, [load])

  const list = useMemo(() => rows.filter((r) => r.category === category).sort((a, b) => a.sortOrder - b.sortOrder), [rows, category])

  function openNew() {
    setEditing(null); setFormError(null)
    setForm({ ...EMPTY, category, sortOrder: (list[list.length - 1]?.sortOrder ?? 0) + 1 })
    setOpen(true)
  }
  function openEdit(q: IntakeQuestion) {
    setEditing(q); setFormError(null)
    setForm({ category: q.category, questionKey: q.questionKey, questionText: q.questionText, fieldType: q.fieldType, options: q.options ?? '', required: q.required, sortOrder: q.sortOrder })
    setOpen(true)
  }

  async function save() {
    setFormError(null)
    const key = form.questionKey.trim() || slug(form.questionText)
    if (!form.questionText.trim()) return setFormError('Write the question text.')
    if (!key) return setFormError('The question needs a key (letters and numbers).')
    if (rows.some((r) => r.category === form.category && r.questionKey === key && r.id !== editing?.id)) return setFormError('Another question in this category already uses that key.')
    const options = form.fieldType === 'SELECT' ? form.options?.split(',').map((o) => o.trim()).filter(Boolean).join(', ') : undefined
    if (form.fieldType === 'SELECT' && !options) return setFormError('List the choices, separated by commas.')
    const payload: IntakeQuestionInput = { ...form, questionKey: key, questionText: form.questionText.trim(), options }
    setSaving(true)
    try {
      if (editing) await updateIntakeQuestion(editing.id, payload); else await createIntakeQuestion(payload)
      push(editing ? 'Question updated' : 'Question added')
      setOpen(false); load()
    } catch (e) { setFormError(e instanceof ApiError ? e.message : 'Could not save the question.') }
    finally { setSaving(false) }
  }

  async function toggle(q: IntakeQuestion, active: boolean) {
    setRows((rs) => rs.map((r) => (r.id === q.id ? { ...r, active } : r)))
    try { await setIntakeQuestionActive(q.id, active) }
    catch (e) { setRows((rs) => rs.map((r) => (r.id === q.id ? { ...r, active: !active } : r))); push(e instanceof ApiError ? e.message : 'Could not update the question.') }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <Select label="Shoot category" value={category} onChange={(e) => setCategory(e.target.value)} className="min-w-48">
            {CATEGORIES.map((c) => <option key={c} value={c}>{categoryLabel(c)}</option>)}
          </Select>
          <p className="max-w-md pb-2 text-sm text-ink-muted">Clients answer these while booking. Turn a question off to hide it without losing past answers.</p>
        </div>
        <Button onClick={openNew}><Plus className="h-4 w-4" /> Add question</Button>
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? <div className="space-y-3 p-6">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
          : error ? <EmptyState icon={<ClipboardList className="h-10 w-10" />} title="Couldn't load questions" description={error} action={<Button onClick={load}>Try again</Button>} />
          : list.length === 0 ? <EmptyState icon={<ClipboardList className="h-10 w-10" />} title="No questions for this category" description="Add one to start collecting details at booking time." />
          : (
            <ul className="divide-y divide-border">
              {list.map((q) => (
                <li key={q.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <span className="w-6 text-xs text-ink-faint">{q.sortOrder}</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-ink">{q.questionText}{q.required && <span className="text-danger"> *</span>}</p>
                    <p className="text-xs text-ink-faint">{q.fieldType.toLowerCase()}{q.fieldType === 'SELECT' && q.options ? `: ${q.options}` : ''} · key {q.questionKey}</p>
                  </div>
                  {!q.active && <Badge tone="neutral">Hidden</Badge>}
                  <Switch checked={q.active} onChange={(v) => toggle(q, v)} label={`Show "${q.questionText}"`} />
                  <Button variant="ghost" size="icon" aria-label="Edit question" onClick={() => openEdit(q)}><Pencil className="h-4 w-4" /></Button>
                </li>
              ))}
            </ul>
          )}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit question' : 'Add question'}
        footer={<><Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button><Button onClick={save} loading={saving}>Save</Button></>}>
        <div className="space-y-4">
          <Input label="Question" value={form.questionText} maxLength={300} onChange={(e) => setForm({ ...form, questionText: e.target.value, questionKey: editing ? form.questionKey : slug(e.target.value) })} />
          <div className="grid grid-cols-2 gap-4">
            <Select label="Answer type" value={form.fieldType} onChange={(e) => setForm({ ...form, fieldType: e.target.value as IntakeFieldType })}>
              {TYPES.map((t) => <option key={t} value={t}>{t.toLowerCase()}</option>)}
            </Select>
            <Input label="Order" type="number" min={0} value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) || 0 })} />
          </div>
          {form.fieldType === 'SELECT' && <Input label="Choices (comma separated)" value={form.options ?? ''} maxLength={500} onChange={(e) => setForm({ ...form, options: e.target.value })} placeholder="Candid, Traditional, Mixed" />}
          <label className="flex items-center gap-2 text-sm text-ink-muted"><input type="checkbox" checked={form.required} onChange={(e) => setForm({ ...form, required: e.target.checked })} /> Required for clients</label>
          {editing && <p className="text-xs text-ink-faint">Key <code>{form.questionKey}</code> is how past answers are stored; it stays fixed when you edit.</p>}
          {formError && <p role="alert" className="text-sm text-danger">{formError}</p>}
        </div>
      </Modal>
    </div>
  )
}
