import { useEffect, useState } from 'react'
import { Users } from 'lucide-react'
import { Card, Avatar, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { ClientDrawer } from '@/components/clients/ClientDrawer'
import { fetchClients, type PagedResponse } from '@/lib/api'
import { hasPermission } from '@/lib/access'
import type { Client } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useAuthStore } from '@/store/useAuthStore'

export default function Clients() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<Client> | null>(null)
  const [loading, setLoading] = useState(true)
  const [openId, setOpenId] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const user = useAuthStore((s) => s.user)
  const canEdit = !!user && (user.roles.some((r) => ['ADMIN', 'SUPER_ADMIN', 'MANAGER', 'RECEPTIONIST'].includes(r)) || hasPermission(user.permissions, 'BOOKING:UPDATE'))

  useEffect(() => {
    let live = true
    setLoading(true)
    fetchClients({ search, page, size: 10 }).then((res) => { if (live) { setData(res); setLoading(false) } }).catch(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [search, page, reloadKey])

  function onSearch(v: string) { setSearch(v); setPage(0) }

  return (
    <div className="space-y-5">
      <div className="w-full max-w-xs"><SearchInput value={search} onChange={onSearch} placeholder="Search clients…" /></div>

      {loading ? (
        <div className="space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-14 rounded-[var(--radius-md)]" />)}</div>
      ) : !data || data.content.length === 0 ? (
        <Card><EmptyState icon={<Users className="h-10 w-10" />} title="No clients found" description="Try a different search term." /></Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <caption className="sr-only">Clients. Select a name to see their details.</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Client</th>
                  <th scope="col" className="hidden px-4 py-3 sm:table-cell">Contact</th>
                  <th scope="col" className="px-4 py-3 text-right">Bookings</th>
                  <th scope="col" className="px-4 py-3 text-right">Spend</th>
                  <th scope="col" className="hidden px-4 py-3 text-right md:table-cell">Since</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((c) => {
                  const name = `${c.firstName} ${c.lastName}`.trim()
                  return (
                    <tr key={c.id} className="transition-colors hover:bg-surface-2">
                      <th scope="row" className="px-6 py-3 text-left font-normal">
                        <div className="flex items-center gap-3">
                          <Avatar src={c.avatarUrl} name={name} size={36} />
                          <button type="button" onClick={() => setOpenId(c.id)}
                            className="truncate text-left font-medium text-ink hover:text-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
                            {name}
                          </button>
                        </div>
                      </th>
                      <td className="hidden px-4 py-3 text-ink-muted sm:table-cell"><p className="truncate">{c.email}</p><p className="text-xs text-ink-faint">{c.mobile}</p></td>
                      <td className="px-4 py-3 text-right text-ink">{c.totalBookings}</td>
                      <td className="px-4 py-3 text-right text-ink">{formatCurrency(c.totalSpend)}</td>
                      <td className="hidden px-4 py-3 text-right text-ink-muted md:table-cell">{formatDate(c.createdAt, { year: '2-digit' })}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
        </Card>
      )}

      <ClientDrawer clientId={openId} canEdit={canEdit} onClose={() => setOpenId(null)} onChanged={() => setReloadKey((k) => k + 1)} />
    </div>
  )
}
