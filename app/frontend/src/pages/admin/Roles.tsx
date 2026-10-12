import { useCallback, useEffect, useMemo, useState } from 'react'
import { Lock, Plus, ShieldCheck } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton, Switch } from '@/components/ui/primitives'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { assignRolePermissions, createRole, fetchPermissions, fetchRoles, updateRole, type PermissionInfo, type RoleInfo } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import { cn } from '@/lib/utils'

const LOCKED = new Set(['SUPER_ADMIN'])
const BUILT_IN = new Set(['SUPER_ADMIN', 'ADMIN', 'CLIENT'])

export default function Roles() {
  const push = useToastStore((s) => s.push)
  const [roles, setRoles] = useState<RoleInfo[]>([])
  const [perms, setPerms] = useState<PermissionInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [draft, setDraft] = useState<Set<string>>(new Set())
  const [saving, setSaving] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [creating, setCreating] = useState(false)
  const [newError, setNewError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoading(true); setError(null)
    Promise.all([fetchRoles(), fetchPermissions()])
      .then(([r, p]) => { setRoles(r); setPerms(p) })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load roles.'))
      .finally(() => setLoading(false))
  }, [])
  useEffect(load, [load])

  const selected = roles.find((r) => r.id === selectedId) ?? null
  useEffect(() => { setDraft(new Set(selected?.permissions.map((p) => p.id) ?? [])) }, [selected?.id, selected?.permissions]) // eslint-disable-line react-hooks/exhaustive-deps

  const grouped = useMemo(() => {
    const m = new Map<string, PermissionInfo[]>()
    perms.filter((p) => p.active).forEach((p) => m.set(p.module, [...(m.get(p.module) ?? []), p]))
    return Array.from(m.entries()).sort(([a], [b]) => a.localeCompare(b))
  }, [perms])

  const dirty = selected ? draft.size !== selected.permissions.length || selected.permissions.some((p) => !draft.has(p.id)) : false
  const locked = selected ? LOCKED.has(selected.name) : false

  function toggle(id: string) {
    setDraft((d) => { const n = new Set(d); if (n.has(id)) n.delete(id); else n.add(id); return n })
  }
  function toggleModule(items: PermissionInfo[], on: boolean) {
    setDraft((d) => { const n = new Set(d); items.forEach((p) => (on ? n.add(p.id) : n.delete(p.id))); return n })
  }

  async function save() {
    if (!selected) return
    setSaving(true)
    try {
      const updated = await assignRolePermissions(selected.id, Array.from(draft))
      setRoles((rs) => rs.map((r) => (r.id === updated.id ? updated : r)))
      push('Permissions saved')
    } catch (e) { push(e instanceof ApiError ? e.message : 'Could not save permissions.') }
    finally { setSaving(false) }
  }

  async function setActive(r: RoleInfo, active: boolean) {
    try {
      const updated = await updateRole(r.id, { active })
      setRoles((rs) => rs.map((x) => (x.id === updated.id ? updated : x)))
      push(active ? 'Role activated' : 'Role deactivated')
    } catch (e) { push(e instanceof ApiError ? e.message : 'Could not update the role.') }
  }

  async function submitNew() {
    setNewError(null)
    if (newName.trim().length < 2) return setNewError('Enter a role name (letters, numbers, spaces).')
    setCreating(true)
    try {
      const r = await createRole(newName.trim(), newDesc.trim() || undefined)
      setRoles((rs) => [...rs, r]); setSelectedId(r.id)
      setShowNew(false); setNewName(''); setNewDesc('')
      push('Role created. Now choose its permissions')
    } catch (e) { setNewError(e instanceof ApiError ? e.message : 'Could not create the role.') }
    finally { setCreating(false) }
  }

  if (loading) return <div className="grid gap-4 md:grid-cols-3"><Skeleton className="h-64" /><Skeleton className="h-64 md:col-span-2" /></div>
  if (error) return <Card><EmptyState icon={<ShieldCheck className="h-10 w-10" />} title="Couldn't load roles" description={error} action={<Button onClick={load}>Try again</Button>} /></Card>

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-xl text-sm text-ink-muted">Roles group permissions. Assign a role to staff from the Staff page. Menus follow the role; the server enforces every action.</p>
        <Button onClick={() => setShowNew(true)}><Plus className="h-4 w-4" /> New role</Button>
      </div>

      <div className="grid gap-5 lg:grid-cols-[280px_1fr]">
        <Card className="p-2">
          <ul className="space-y-1">
            {roles.map((r) => (
              <li key={r.id}>
                <button type="button" onClick={() => setSelectedId(r.id)}
                  className={cn('flex w-full items-center justify-between gap-2 rounded-[var(--radius-md)] px-3 py-2.5 text-left text-sm transition-colors',
                    selectedId === r.id ? 'bg-accent text-accent-ink' : 'text-ink hover:bg-surface-2')}>
                  <span className="min-w-0 truncate">{r.name.replace(/_/g, ' ')}</span>
                  <span className="flex shrink-0 items-center gap-1.5 text-xs opacity-80">
                    {LOCKED.has(r.name) && <Lock className="h-3 w-3" />}{!r.active && 'off'}{r.permissions.length}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Card>

        <Card className="p-5">
          {!selected ? (
            <EmptyState icon={<ShieldCheck className="h-10 w-10" />} title="Pick a role" description="Select a role on the left to see and edit its permissions." />
          ) : (
            <div className="space-y-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-xl text-ink">{selected.name.replace(/_/g, ' ')}</h2>
                  {selected.description && <p className="text-sm text-ink-muted">{selected.description}</p>}
                  {locked && <Badge tone="info" className="mt-2"><Lock className="mr-1 h-3 w-3" /> Built in: always has every permission and can't be edited</Badge>}
                </div>
                {!BUILT_IN.has(selected.name) && (
                  <label className="flex items-center gap-2 text-sm text-ink-muted"><Switch checked={selected.active} onChange={(v) => setActive(selected, v)} label="Role active" /> Active</label>
                )}
              </div>

              {grouped.length === 0 ? <p className="text-sm text-ink-muted">No permissions are defined yet.</p> : (
                <div className="space-y-4">
                  {grouped.map(([module, items]) => {
                    const all = items.every((p) => draft.has(p.id))
                    return (
                      <fieldset key={module} className="rounded-[var(--radius-md)] border border-border p-3">
                        <legend className="px-1 text-xs font-medium uppercase tracking-wide text-ink-faint">{module.replace(/_/g, ' ')}</legend>
                        <label className="mb-2 flex items-center gap-2 text-xs text-ink-muted">
                          <input type="checkbox" disabled={locked} checked={all} onChange={(e) => toggleModule(items, e.target.checked)} /> Select all
                        </label>
                        <div className="grid gap-1.5 sm:grid-cols-2">
                          {items.map((p) => (
                            <label key={p.id} className="flex items-start gap-2 text-sm text-ink" title={p.description}>
                              <input type="checkbox" className="mt-1" disabled={locked} checked={draft.has(p.id)} onChange={() => toggle(p.id)} />
                              <span>{p.action.replace(/_/g, ' ').toLowerCase()}</span>
                            </label>
                          ))}
                        </div>
                      </fieldset>
                    )
                  })}
                </div>
              )}

              {!locked && <div className="flex justify-end gap-2"><Button variant="outline" disabled={!dirty} onClick={() => setDraft(new Set(selected.permissions.map((p) => p.id)))}>Reset</Button><Button onClick={save} disabled={!dirty} loading={saving}>Save permissions</Button></div>}
            </div>
          )}
        </Card>
      </div>

      <Modal open={showNew} onClose={() => setShowNew(false)} title="New role"
        footer={<><Button variant="outline" onClick={() => setShowNew(false)}>Cancel</Button><Button onClick={submitNew} loading={creating}>Create role</Button></>}>
        <div className="space-y-4">
          <Input label="Role name" value={newName} maxLength={50} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. Front desk" />
          <Input label="Description (optional)" value={newDesc} maxLength={200} onChange={(e) => setNewDesc(e.target.value)} />
          {newError && <p role="alert" className="text-sm text-danger">{newError}</p>}
        </div>
      </Modal>
    </div>
  )
}
