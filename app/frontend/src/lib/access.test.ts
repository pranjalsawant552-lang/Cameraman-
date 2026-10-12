import { describe, expect, it } from 'vitest'
import { canSee, hasPermission, isAdmin, isSuperAdmin } from './access'

describe('canSee', () => {
  it('uses the built-in role lists', () => {
    expect(canSee('/admin/roles', ['ADMIN'])).toBe(true)
    expect(canSee('/admin/roles', ['MANAGER'])).toBe(false)
    expect(canSee('/admin/attendance', ['RECEPTIONIST'])).toBe(true)
    expect(canSee('/admin/inventory', ['PHOTOGRAPHER'])).toBe(false)
  })
  it('lets a custom role in through its permission', () => {
    expect(canSee('/admin/staff', ['CUSTOM_ROLE' as never], ['STAFF:VIEW'])).toBe(true)
    expect(canSee('/admin/staff', ['CUSTOM_ROLE' as never], ['BOOKING:VIEW'])).toBe(false)
    expect(canSee('/admin/staff', ['CUSTOM_ROLE' as never], undefined)).toBe(false)
  })
  it('shows unknown paths rather than hiding them', () => {
    expect(canSee('/admin/something-new', [])).toBe(true)
  })
})

describe('role helpers', () => {
  it('hasPermission handles missing lists', () => {
    expect(hasPermission(undefined, 'A:B')).toBe(false)
    expect(hasPermission(['A:B'], 'A:B')).toBe(true)
  })
  it('isAdmin / isSuperAdmin', () => {
    expect(isAdmin(['SUPER_ADMIN'])).toBe(true)
    expect(isAdmin(['MANAGER'])).toBe(false)
    expect(isSuperAdmin(['ADMIN'])).toBe(false)
    expect(isSuperAdmin(undefined)).toBe(false)
  })
})
