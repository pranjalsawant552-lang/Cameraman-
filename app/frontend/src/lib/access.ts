import type { Role } from './types'

/**
 * Which staff roles see which admin menu items. This only decides what is SHOWN: the backend's
 * @PreAuthorize rules are the real enforcement, so a hidden page that is opened by URL still gets
 * a 403 from the API. (The login response carries roles, not permissions, so menus are role-based.)
 */
const ALL: Role[] = ['SUPER_ADMIN', 'ADMIN']

export const NAV_ROLES: Record<string, Role[]> = {
  '/admin': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR', 'ACCOUNTANT', 'INVENTORY_MANAGER'],
  '/admin/bookings': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR'],
  '/admin/my-schedule': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR', 'ACCOUNTANT', 'INVENTORY_MANAGER'],
  '/admin/calendar': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR'],
  '/admin/studio-sessions': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER'],
  '/admin/outdoor-shoots': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER'],
  '/admin/clients': [...ALL, 'MANAGER', 'RECEPTIONIST', 'ACCOUNTANT'],
  '/admin/galleries': [...ALL, 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR'],
  '/admin/selections': [...ALL, 'MANAGER', 'PHOTOGRAPHER', 'EDITOR'],
  '/admin/invoices': [...ALL, 'MANAGER', 'RECEPTIONIST', 'ACCOUNTANT'],
  '/admin/payments': [...ALL, 'MANAGER', 'RECEPTIONIST', 'ACCOUNTANT'],
  '/admin/contact-inquiries': [...ALL, 'MANAGER', 'RECEPTIONIST'],
  '/admin/reports': [...ALL, 'MANAGER', 'ACCOUNTANT'],
  '/admin/albums': [...ALL, 'MANAGER', 'EDITOR'],
  '/admin/engagement': [...ALL, 'MANAGER'],
  '/admin/broadcast': [...ALL, 'MANAGER'],
  '/admin/recommendations': [...ALL, 'MANAGER'],
  '/admin/inventory': [...ALL, 'MANAGER', 'INVENTORY_MANAGER'],
  '/admin/catalog': [...ALL, 'MANAGER'],
  '/admin/staff': [...ALL, 'MANAGER', 'RECEPTIONIST'],
  '/admin/roles': ALL,
  '/admin/intake-questions': [...ALL, 'MANAGER'],
  '/admin/attendance': [...ALL, 'MANAGER', 'RECEPTIONIST'], // the attendance API is limited to these roles
  '/admin/cms': [...ALL, 'MANAGER'],
  '/admin/audit-logs': ALL,
}

/**
 * Menu items a CUSTOM role can reach, and the permission that unlocks each (the same
 * MODULE:ACTION the backend checks). Built-in roles ignore this and use NAV_ROLES.
 * Pages that are purely about administering users/roles stay with ADMIN and SUPER_ADMIN.
 */
export const NAV_PERMISSIONS: Record<string, string> = {
  '/admin': 'DASHBOARD:VIEW',
  '/admin/my-schedule': 'ATTENDANCE:SELF',
  '/admin/calendar': 'BOOKING:VIEW',
  '/admin/bookings': 'BOOKING:VIEW',
  '/admin/studio-sessions': 'STUDIO:VIEW',
  '/admin/outdoor-shoots': 'OUTDOOR:VIEW',
  '/admin/clients': 'BOOKING:VIEW',
  '/admin/galleries': 'GALLERY:VIEW',
  '/admin/selections': 'SELECTION:VIEW',
  '/admin/invoices': 'INVOICE:VIEW',
  '/admin/payments': 'PAYMENT:VIEW',
  '/admin/contact-inquiries': 'CONTACT:VIEW',
  '/admin/reports': 'REPORT:VIEW',
  '/admin/albums': 'ALBUM:VIEW',
  '/admin/engagement': 'ENGAGEMENT:VIEW',
  '/admin/broadcast': 'BROADCAST:VIEW',
  '/admin/recommendations': 'ENGAGEMENT:VIEW',
  '/admin/inventory': 'INVENTORY:VIEW',
  '/admin/catalog': 'PACKAGE:VIEW',
  '/admin/staff': 'STAFF:VIEW',
  '/admin/intake-questions': 'INTAKE:VIEW',
  '/admin/attendance': 'ATTENDANCE:VIEW',
  '/admin/cms': 'CMS:VIEW',
  '/admin/audit-logs': 'AUDIT:VIEW',
}

/** True when the permission list (from custom roles) grants `code`. */
export function hasPermission(permissions: string[] | undefined, code: string): boolean {
  return (permissions ?? []).includes(code)
}

export function canSee(path: string, roles: Role[] | undefined, permissions?: string[]): boolean {
  const allowed = NAV_ROLES[path]
  if (!allowed) return true // unknown items are shown rather than silently lost
  if ((roles ?? []).some((r) => allowed.includes(r))) return true
  const needed = NAV_PERMISSIONS[path]
  return needed !== undefined && hasPermission(permissions, needed)
}

export const isAdmin = (roles: Role[] | undefined) => (roles ?? []).some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN')
export const isSuperAdmin = (roles: Role[] | undefined) => (roles ?? []).includes('SUPER_ADMIN')
