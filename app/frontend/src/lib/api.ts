// API layer this UI is built against — talks to the real Spring Boot
// backend exclusively. There is no mock/demo-data fallback: every function
// below calls a real endpoint, verified against the backend's actual DTOs
// (field names checked against the Java source, not assumed).
//
// If you're looking for the old dual-mode (demo + live) version of this
// file, see git history / STAGE_1 through STAGE_8 reports — the standalone
// demo mode was removed for the production/market release so the app can
// never accidentally show fabricated data to a real user.

import type {
  SlotAvailability, BlockedDate, AppNotification, Booking, Client, DashboardStats, Gallery, Invoice, InventoryItem,
  SuggestedOutreach, SuggestionRule, StaffMember, DaySummary, MonthlyReport,
  DayAttendanceStatus, WorkHoursConfig, WorkShift, StaffScheduleRow, AttendanceHolidayRow, PayrollReport, WebhookConfig, UnmatchedCode,
  StaffLeaveRequest, LeaveStatus, CatalogPackage, CatalogProduct, CatalogCategory,
  CategoryRecommendationRule, ProductSuggestion, PackageUpgradeSuggestion,
} from './types'
import { apiGet, apiPost, apiPut, apiPatch, apiPostForm, apiDelete, apiGetBlob, ApiError } from './apiClient'
import { subscribeToNotificationQueue } from './realtime'

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ---------------- Adapters: backend DTO -> frontend view model ----------------
// Field names below are taken directly from BookingDto.BookingResponse,
// BillingDto.InvoiceResponse and UserDto.UserResponse on the backend, not
// guessed. Where the backend genuinely doesn't return a field the UI wants
// (e.g. per-client totalBookings/totalSpend, which need a separate
// aggregation the backend doesn't expose yet), it's defaulted and called out
// here rather than silently invented.

interface BackendBooking {
  id: string; bookingNumber: string; clientId: string; clientName: string
  clientMobile: string; shootCategory: string; packageName: string
  bookingDate: string; status: string; orderStage?: string
  totalAmount: number; advanceAmount: number; balanceAmount: number
  photographerName?: string; marketingOptIn?: boolean
  startTime?: string; endTime?: string; packageId?: string; venue?: string; notes?: string
  type?: string; clientEmail?: string; cancelledReason?: string; rescheduledCount?: number
  orderStageLabel?: string; expectedDeliveryDate?: string; editorId?: string; editorName?: string
  photographerId?: string; courierProvider?: string; courierTrackingNumber?: string
  deliveredAt?: string; stageHistory?: import('./types').StageHistoryEntry[]; createdAt?: string
  locationType?: string; pickupStatus?: string; readyForPickupAt?: string; pickedUpAt?: string
  pickupReminderCount?: number; lastPickupReminderAt?: string; daysAwaitingPickup?: number
}

function adaptBooking(b: BackendBooking): Booking {
  return {
    id: b.id,
    bookingNumber: b.bookingNumber,
    clientId: b.clientId,
    clientName: b.clientName,
    clientMobile: b.clientMobile,
    shootCategory: b.shootCategory as Booking['shootCategory'],
    packageName: b.packageName,
    bookingDate: b.bookingDate,
    status: b.status as Booking['status'],
    orderStage: (b.orderStage ?? 'BOOKING_CONFIRMED') as Booking['orderStage'],
    totalAmount: b.totalAmount,
    advanceAmount: b.advanceAmount,
    balanceAmount: b.balanceAmount,
    photographerName: b.photographerName,
    marketingOptIn: b.marketingOptIn ?? true,
    createdAt: b.createdAt ?? b.bookingDate,
    startTime: b.startTime, endTime: b.endTime, packageId: b.packageId, venue: b.venue, notes: b.notes,
    type: b.type, clientEmail: b.clientEmail, cancelledReason: b.cancelledReason,
    rescheduledCount: b.rescheduledCount, orderStageLabel: b.orderStageLabel,
    expectedDeliveryDate: b.expectedDeliveryDate, editorId: b.editorId, editorName: b.editorName,
    photographerId: b.photographerId, courierProvider: b.courierProvider,
    courierTrackingNumber: b.courierTrackingNumber, deliveredAt: b.deliveredAt, stageHistory: b.stageHistory,
    locationType: (b.locationType ?? 'INDOOR') as Booking['locationType'],
    pickupStatus: (b.pickupStatus ?? 'NOT_READY') as Booking['pickupStatus'],
    readyForPickupAt: b.readyForPickupAt, pickedUpAt: b.pickedUpAt,
    pickupReminderCount: b.pickupReminderCount ?? 0, lastPickupReminderAt: b.lastPickupReminderAt,
    daysAwaitingPickup: b.daysAwaitingPickup,
  }
}

interface BackendInvoiceItem {
  id: string; description: string; itemType?: string; quantity: number
  unitPrice: number; discountPercent?: number; lineTotal: number
}

interface BackendInvoice {
  id: string; invoiceNumber: string; clientName: string; clientMobile?: string; clientEmail?: string
  invoiceDate: string; subtotal?: number; discountAmount?: number; taxableAmount?: number
  cgstAmount?: number; sgstAmount?: number; igstAmount?: number; totalTax?: number
  totalAmount: number; amountPaid: number; balanceDue: number; status: string; dueDate?: string
  notes?: string; pdfUrl?: string; generatedByName?: string; taxType?: string; items?: BackendInvoiceItem[]
  bookingId?: string; sizeFormat?: string; clientAddress?: string
}

function adaptInvoice(i: BackendInvoice): Invoice {
  return {
    id: i.id,
    invoiceNumber: i.invoiceNumber,
    sourceId: i.bookingId,
    sizeFormat: i.sizeFormat,
    clientAddress: i.clientAddress,
    clientName: i.clientName,
    clientMobile: i.clientMobile,
    clientEmail: i.clientEmail,
    invoiceDate: i.invoiceDate,
    subtotal: i.subtotal,
    discountAmount: i.discountAmount,
    taxableAmount: i.taxableAmount,
    cgstAmount: i.cgstAmount,
    sgstAmount: i.sgstAmount,
    igstAmount: i.igstAmount,
    totalTax: i.totalTax,
    totalAmount: i.totalAmount,
    amountPaid: i.amountPaid,
    balanceDue: i.balanceDue,
    status: i.status as Invoice['status'],
    dueDate: i.dueDate ?? i.invoiceDate,
    notes: i.notes,
    pdfUrl: i.pdfUrl,
    generatedByName: i.generatedByName,
    taxType: i.taxType as Invoice['taxType'],
    items: i.items?.map((it) => ({
      id: it.id, description: it.description, itemType: it.itemType,
      quantity: it.quantity, unitPrice: it.unitPrice, discountPercent: it.discountPercent, lineTotal: it.lineTotal,
    })),
    createdAt: i.invoiceDate,
  }
}

interface BackendClient {
  id: string; firstName: string; lastName?: string; email: string
  mobile: string; profilePhotoUrl?: string; createdAt: string
  totalBookings?: number; totalSpend?: number
}

function adaptClient(c: BackendClient): Client {
  return {
    id: c.id,
    firstName: c.firstName,
    lastName: c.lastName ?? '',
    email: c.email,
    mobile: c.mobile,
    avatarUrl: c.profilePhotoUrl,
    totalBookings: c.totalBookings ?? 0,
    totalSpend: c.totalSpend ?? 0,
    marketingOptIn: true,
    createdAt: c.createdAt,
  }
}

// ---------------- Bookings ----------------
export async function fetchBookings(opts: { search?: string; status?: string; locationType?: string; pickup?: string; page?: number; size?: number } = {}) {
  const { search = '', status = '', locationType = '', pickup = '', page = 0, size = 8 } = opts
  const res = await apiGet<PagedResponse<BackendBooking>>('/bookings', {
    params: { search, status, locationType, pickup, page, size },
  })
  return { ...res, content: res.content.map(adaptBooking) }
}

export async function fetchBookingById(id: string): Promise<Booking | undefined> {
  try {
    return adaptBooking(await apiGet<BackendBooking>(`/bookings/${id}`))
  } catch {
    return undefined
  }
}

// ---------------- Bookings: writes ----------------
// Field names match BookingDto.CreateRequest / UpdateStageRequest /
// RescheduleRequest / CancelRequest exactly (backend/.../booking/dto/BookingDto.java).
export interface CreateBookingInput {
  clientId?: string
  clientName: string
  clientMobile: string
  clientEmail?: string
  bookingDate: string // ISO yyyy-MM-dd
  startTime: string // HH:mm
  endTime: string
  packageId?: string
  packageName: string
  type: 'ONLINE' | 'OFFLINE' | 'WALK_IN'
  shootCategory: string
  photographerId?: string
  venue?: string
  /** INDOOR (in the studio) or OUTDOOR (at a venue — venue is then required). */
  locationType?: 'INDOOR' | 'OUTDOOR'
  notes?: string
  advanceAmount?: number
  totalAmount: number
  marketingOptIn?: boolean
}

export async function createBooking(input: CreateBookingInput): Promise<Booking> {
  return adaptBooking(await apiPost<BackendBooking>('/bookings', input))
}

/** Slots for a day (9-19h, hourly). Server marks each slot available/unavailable. */
export async function fetchSlotAvailability(date: string, photographerId?: string): Promise<SlotAvailability[]> {
  return apiPost<SlotAvailability[]>('/bookings/availability', { date, photographerId })
}

export async function fetchBlockedDates(from?: string, to?: string): Promise<BlockedDate[]> {
  return apiGet<BlockedDate[]>('/bookings/blocked-dates', { params: { from, to } })
}

export async function fetchActivePackages(): Promise<CatalogPackage[]> {
  return apiGet<CatalogPackage[]>('/packages/active')
}

export async function updateBookingStage(id: string, stage: Booking['orderStage'], note?: string): Promise<void> {
  await apiPatch(`/bookings/${id}/stage`, { stage, note })
}

export async function markBookingReadyForPickup(id: string): Promise<Booking> {
  return adaptBooking(await apiPost<BackendBooking>(`/bookings/${id}/ready-for-pickup`))
}

export async function markBookingPickedUp(id: string): Promise<Booking> {
  return adaptBooking(await apiPost<BackendBooking>(`/bookings/${id}/picked-up`))
}

export interface PickupReminderResult { whatsappSent: boolean; emailSent: boolean; reminderCount: number; booking: Booking }

export async function sendPickupReminder(id: string): Promise<PickupReminderResult> {
  const r = await apiPost<{ whatsappSent: boolean; emailSent: boolean; reminderCount: number; booking: BackendBooking }>(`/bookings/${id}/pickup-reminder`)
  return { ...r, booking: adaptBooking(r.booking) }
}

/** Creates (or returns the existing) invoice for a booking; returns the invoice id. */
export async function generateBookingInvoice(id: string): Promise<string> {
  const inv = await apiPost<{ id: string }>(`/bookings/${id}/invoice`)
  return inv.id
}

/** Staff confirm a pending / rescheduled booking. */
export async function confirmBooking(id: string): Promise<void> {
  await apiPost(`/bookings/${id}/confirm`)
}

export async function cancelBooking(id: string, reason: string): Promise<void> {
  await apiPost(`/bookings/${id}/cancel`, { reason })
}

export async function rescheduleBooking(id: string, newDate: string, newStartTime: string, newEndTime: string, reason?: string): Promise<void> {
  await apiPost(`/bookings/${id}/reschedule`, { newDate, newStartTime, newEndTime, reason })
}

export async function fetchMyBookings(clientId: string): Promise<Booking[]> {
  // Server enforces #clientId == authentication.principal.id — a client
  // can only ever get their own bookings back here even if this were
  // called with someone else's ID (verified in BookingController).
  const res = await apiGet<PagedResponse<BackendBooking>>(`/bookings/client/${clientId}`, { params: { page: 0, size: 50 } })
  return res.content.map(adaptBooking)
}

export async function fetchMyInvoices(): Promise<Invoice[]> {
  const res = await apiGet<PagedResponse<BackendInvoice>>('/billing/invoices/my', { params: { page: 0, size: 50 } })
  return res.content.map(adaptInvoice)
}

// ---------------- Clients ----------------
export async function fetchClients(opts: { search?: string; page?: number; size?: number } = {}) {
  const { search = '', page = 0, size = 8 } = opts
  // Real endpoint is GET /users/clients/search (see UserController) — it
  // returns the CLIENT-role subset of /users with the same search param.
  const res = await apiGet<PagedResponse<BackendClient>>('/users/clients/search', {
    params: { search, page, size },
  })
  return { ...res, content: res.content.map(adaptClient) }
}

// ---------------- Galleries ----------------
interface BackendGallery {
  id: string; name: string; bookingId: string; clientId?: string; clientName: string
  status: string; photoCount: number; coverPhotoUrl?: string; publishedAt?: string
  viewCount: number; downloadAllowed: boolean; selectionLimit?: number; watermarkEnabled: boolean
  accessToken?: string; openSelectionToken?: string
  originalsExpiryDays?: number | null; originalsExpiredAt?: string | null; totalSizeBytes?: number
}

function adaptGallery(g: BackendGallery): Gallery {
  return {
    id: g.id, name: g.name, bookingId: g.bookingId, clientName: g.clientName,
    status: g.status as Gallery['status'], photoCount: g.photoCount,
    coverPhotoUrl: g.coverPhotoUrl ?? '', publishedAt: g.publishedAt,
    viewCount: g.viewCount, downloadAllowed: g.downloadAllowed,
    selectionLimit: g.selectionLimit, watermarkEnabled: g.watermarkEnabled,
    clientId: g.clientId, accessToken: g.accessToken, openSelectionToken: g.openSelectionToken,
    originalsExpiryDays: g.originalsExpiryDays ?? null, originalsExpiredAt: g.originalsExpiredAt ?? null,
    totalSizeBytes: g.totalSizeBytes ?? 0,
  }
}

export async function fetchGalleries(opts: { search?: string; status?: string; page?: number; size?: number } = {}) {
  const { search = '', status = '', page = 0, size = 8 } = opts
  const res = await apiGet<PagedResponse<BackendGallery>>('/galleries', { params: { search, status, page, size } })
  return { ...res, content: res.content.map(adaptGallery) }
}

export async function fetchMyGalleries(): Promise<Gallery[]> {
  const res = await apiGet<PagedResponse<BackendGallery>>('/galleries/mine', { params: { page: 0, size: 50 } })
  return res.content.map(adaptGallery)
}

export async function fetchGalleryById(id: string): Promise<Gallery | undefined> {
  try { return adaptGallery(await apiGet<BackendGallery>(`/galleries/${id}`)) } catch { return undefined }
}

export interface GalleryPhoto {
  id: string
  url: string
  originalFilename: string
  /** Small (~400px) image for grids; `url` is the larger preview/watermarked one for the viewer. */
  thumbUrl: string
  failureReason?: string
  favourite: boolean
  /** 1-based display number derived from the backend's sortOrder, for
   *  showing clients/staff a stable "Photo 14 of 82" style reference. */
  displayNumber: number
  /** Processing state. Clients only ever receive READY photos; staff can also see the others. */
  status: PhotoStatus
  originalPurged: boolean
  fileSizeBytes: number
}

export type PhotoStatus = 'UPLOADING' | 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'

interface BackendPhoto {
  id: string; originalFilename: string; previewUrl?: string; thumbnailUrl?: string
  watermarkedUrl?: string; favourite: boolean; sortOrder: number
  status?: string; originalPurged?: boolean; fileSizeBytes?: number; failureReason?: string
}

/** @param includePending staff screens only: also return photos still processing or failed. */
export async function fetchGalleryPhotos(id: string, includePending = false): Promise<GalleryPhoto[]> {
  // GET /galleries/{id}/photos returns a plain array (List<PhotoResponse>),
  // not a paginated envelope — GalleryController#getPhotos has no
  // page/size params, only includeUrls.
  const photos = await apiGet<BackendPhoto[]>(`/galleries/${id}/photos`, { params: { includeUrls: true, includePending } })
  return photos
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((p, i) => ({
      id: p.id,
      // Staff get both URLs and see the watermarked one (as the client would while locked). Clients only get
      // what they are allowed to see: watermarked while locked, the clean preview once unlocked.
      url: p.watermarkedUrl || p.previewUrl || p.thumbnailUrl || '',
      originalFilename: p.originalFilename,
      thumbUrl: p.thumbnailUrl || p.previewUrl || p.watermarkedUrl || '',
      failureReason: p.failureReason,
      favourite: p.favourite,
      displayNumber: i + 1,
      status: (p.status ?? 'READY') as PhotoStatus,
      originalPurged: p.originalPurged ?? false,
      fileSizeBytes: p.fileSizeBytes ?? 0,
    }))
}

export interface CreateGalleryInput {
  name: string
  bookingId?: string
  clientId?: string
  clientName: string
  description?: string
  selectionLimit?: number
  downloadAllowed?: boolean
  watermarkEnabled?: boolean
}

export async function createGallery(input: CreateGalleryInput): Promise<Gallery> {
  return adaptGallery(await apiPost<BackendGallery>('/galleries', input))
}

export async function publishGallery(gallery: Gallery): Promise<Gallery> {
  // POST /galleries/{id}/publish requires a full PublishRequest body
  // (GalleryDto.PublishRequest) — it's not a bare toggle. Re-sends the
  // gallery's own existing settings unchanged rather than silently
  // resetting downloadAllowed/watermarkEnabled/selectionLimit to their
  // Java boolean/null defaults, which is what passing an empty body would
  // have done.
  return adaptGallery(await apiPost<BackendGallery>(`/galleries/${gallery.id}/publish`, {
    selectionLimit: gallery.selectionLimit,
    downloadAllowed: gallery.downloadAllowed,
    watermarkEnabled: gallery.watermarkEnabled ?? false,
  }))
}

export async function uploadGalleryPhotos(id: string, files: File[]): Promise<{ uploaded: number }> {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  const uploaded = await apiPostForm<unknown[]>(`/galleries/${id}/photos/upload`, form)
  return { uploaded: uploaded.length }
}

/** Staff: unlock (clean photos + downloads) or re-lock a gallery regardless of payment. */
export async function setGalleryDownloads(galleryId: string, allowed: boolean): Promise<Gallery> {
  return adaptGallery(await apiPut<BackendGallery>(`/galleries/${galleryId}/downloads`, { allowed }))
}

export async function fetchPhotoDownloadUrl(photoId: string, original = true): Promise<string> {
  return apiGet<string>(`/galleries/photos/${photoId}/download-url`, { params: { original } })
}

/** "Download all" for a gallery — a single ZIP streamed by the backend
 *  (GalleryService#streamGalleryZip), respecting the same
 *  downloadAllowed/watermark/purged-original rules as a single-photo
 *  download. Triggers the browser's normal file-save via an object URL. */
export async function downloadAllGalleryPhotos(galleryId: string, galleryName: string): Promise<void> {
  const blob = await apiGetBlob(`/galleries/${galleryId}/download`)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${galleryName.replace(/[^a-z0-9\-_ ]/gi, '').trim() || 'gallery'}.zip`
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

// ---------------- Invoices ----------------
export async function fetchInvoices(opts: { search?: string; status?: string; page?: number; size?: number } = {}) {
  const { search = '', status = '', page = 0, size = 8 } = opts
  const res = await apiGet<PagedResponse<BackendInvoice>>('/billing/invoices', {
    params: { search, status, page, size },
  })
  return { ...res, content: res.content.map(adaptInvoice) }
}

// ---------------- Invoices: writes ----------------
// Field names match BillingDto.CreateInvoiceRequest / InvoiceItemRequest /
// PaymentRequest exactly (backend/.../billing/dto/BillingDto.java).
export interface InvoiceItemInput {
  description: string
  itemType?: string
  quantity: number
  unitPrice: number
  discountPercent?: number
}

export interface CreateInvoiceInput {
  invoiceType: 'ONLINE' | 'OFFLINE'
  bookingId?: string
  clientId?: string
  clientName: string
  clientMobile?: string
  clientEmail?: string
  discountAmount?: number
  discountPercent?: number
  notes?: string
  /** INTRA_STATE (default: 9% CGST + 9% SGST), INTER_STATE (18% IGST), or
   *  EXEMPT (no tax). See backend BillingDto.CreateInvoiceRequest#taxType. */
  taxType?: 'INTRA_STATE' | 'INTER_STATE' | 'EXEMPT'
  items: InvoiceItemInput[]
}

export async function createInvoice(input: CreateInvoiceInput): Promise<Invoice> {
  return adaptInvoice(await apiPost<BackendInvoice>('/billing/invoices', input))
}

export async function fetchInvoiceById(id: string): Promise<Invoice | undefined> {
  try { return adaptInvoice(await apiGet<BackendInvoice>(`/billing/invoices/${id}`)) } catch { return undefined }
}

export interface BackfillResult { created: number; failed: number; alreadyHadInvoice: number; errors?: string[] }
/** Creates invoices for every booking / studio session / outdoor shoot that has none yet. */
export const backfillInvoices = () => apiPost<BackfillResult>('/billing/invoices/backfill')
export type InvoiceSourceType = 'BOOKING' | 'STUDIO_SESSION' | 'OUTDOOR_SHOOT'
export async function invoiceForSource(type: InvoiceSourceType, id: string): Promise<Invoice> {
  return adaptInvoice(await apiPost<BackendInvoice>(`/billing/invoices/for-source/${type}/${id}`))
}
export async function fetchInvoicesForClient(clientId: string): Promise<Invoice[]> {
  const rows = await apiGet<BackendInvoice[]>(`/billing/invoices/by-client/${clientId}`)
  return rows.map(adaptInvoice)
}
export async function downloadInvoicePdf(id: string): Promise<Blob> {
  return apiGetBlob(`/billing/invoices/${id}/pdf`)
}

// Payment recording now goes exclusively through the Payments module
// (createPaymentOrder + verifyRazorpayPayment for online, recordManualPayment
// for cash/UPI/etc. recorded by staff) — see below. Both paths update the
// invoice via the same billingService.recordPayment() call on the backend,
// so there's one consistent ledger instead of two disconnected ones.

// ---------------- Dashboard ----------------
interface BackendRevenuePoint { month: string; amount: number }
interface BackendStatusBreakdown { status: string; count: number; color: string }
interface BackendStats {
  totalBookings: number; activeBookings: number; pendingBookings: number; completedBookings: number
  totalRevenue: number; pendingRevenue: number; monthlyRevenue: number
  totalGalleries: number; pendingSelections: number; totalClients: number
  upcomingToday: unknown[]
  revenueChart: BackendRevenuePoint[]
  bookingStatusBreakdown: BackendStatusBreakdown[]
}

export async function fetchDashboardStats(): Promise<DashboardStats> {
  // GET /dashboard/stats (DashboardDto.Stats) is deliberately used here
  // instead of the richer-looking GET /dashboard/overview
  // (DashboardDto.OverviewMetrics): overview is restricted to
  // ADMIN/SUPER_ADMIN/MANAGER/ACCOUNTANT, but this Dashboard page is the
  // /admin landing route for every staff role including PHOTOGRAPHER,
  // EDITOR, RECEPTIONIST and INVENTORY_MANAGER — using overview would 403
  // for all of them. /dashboard/stats allows every staff role.
  //
  // Stats has no explicit "today" or "this week" booking counts, or a
  // last-month revenue figure, the way this page's original mock data did
  // — derived below from what Stats does return (upcomingToday.length,
  // pending+active as an approximation of "this week", and the
  // second-to-last point of the 6-month revenueChart) rather than adding
  // fields that don't exist on the backend.
  const [stats, upcoming, lowStockItems, pendingOutreach] = await Promise.all([
    apiGet<BackendStats>('/dashboard/stats'),
    apiGet<PagedResponse<BackendBooking>>('/bookings', { params: { status: 'CONFIRMED', page: 0, size: 5, sortBy: 'bookingDate', sortDir: 'asc' } }),
    fetchLowStockCount(),
    fetchOutreachQueue('PENDING_REVIEW').catch(() => []),
  ])
  const chart = stats.revenueChart ?? []
  return {
    bookingsToday: stats.upcomingToday?.length ?? 0,
    bookingsThisWeek: stats.pendingBookings + stats.activeBookings,
    revenueThisMonth: stats.monthlyRevenue,
    revenueLastMonth: chart.length >= 2 ? chart[chart.length - 2].amount : 0,
    pendingSuggestions: pendingOutreach.length,
    lowStockItems,
    activeGalleries: stats.totalGalleries,
    upcomingShoots: upcoming.content.map(adaptBooking),
    revenueTrend: chart.map((r) => ({ month: r.month, revenue: r.amount })),
    // Backend groups by booking status (PENDING/CONFIRMED/COMPLETED/
    // CANCELLED), not by shoot category — there's no by-category endpoint.
    // Real data, just a different axis than the field name suggests.
    bookingsByCategory: (stats.bookingStatusBreakdown ?? []).map((b) => ({ category: b.status, count: b.count })),
  }
}

// ---------------- Inventory ----------------
interface BackendInventoryItem {
  id: string; name: string; sku: string; barcode?: string; category: string; unit?: string
  quantityOnHand: number; reorderLevel: number; sellingPrice: number
  active: boolean; lowStock: boolean; outOfStock: boolean
}

function adaptInventoryItem(i: BackendInventoryItem): InventoryItem {
  return {
    id: i.id,
    name: i.name,
    sku: i.sku,
    category: i.category,
    quantityOnHand: i.quantityOnHand,
    reorderLevel: i.reorderLevel,
    unitPrice: i.sellingPrice,
    imageUrl: undefined,
    barcode: i.barcode || i.sku,
    unit: i.unit,
  }
}

export async function fetchInventory() {
  // Real endpoint (GET /inventory) is paginated; the Inventory page renders
  // a flat table so we pull a large single page rather than paginating in
  // the UI. Revisit with real pagination if catalogs grow past a few
  // hundred SKUs.
  const res = await apiGet<PagedResponse<BackendInventoryItem>>('/inventory', { params: { page: 0, size: 500 } })
  return res.content.map(adaptInventoryItem)
}

export async function fetchLowStockCount(): Promise<number> {
  const items = await apiGet<BackendInventoryItem[]>('/inventory/low-stock')
  return items.length
}

/** Values the server accepts for POST /inventory/{id}/adjust-stock (see TransactionType). The UI used to send
 *  STOCK_IN / STOCK_OUT / RETURNED / DAMAGED / ADJUSTMENT, which the server rejected with a 500. */
export type StockAdjustmentType = 'PURCHASE' | 'ADJUSTMENT_IN' | 'RETURN' | 'SALE' | 'ADJUSTMENT_OUT' | 'DAMAGE'

export async function adjustStock(itemId: string, transactionType: StockAdjustmentType, quantity: number, notes?: string): Promise<InventoryItem> {
  return adaptInventoryItem(await apiPost<BackendInventoryItem>(`/inventory/${itemId}/adjust-stock`, { transactionType, quantity, notes }))
}

export interface CreateInventoryItemInput {
  name: string; sku: string; category: string; unit?: string
  initialQuantity?: number; reorderLevel?: number; costPrice?: number; sellingPrice: number
}
export async function createInventoryItem(input: CreateInventoryItemInput): Promise<InventoryItem> {
  return adaptInventoryItem(await apiPost<BackendInventoryItem>('/inventory', input))
}
export interface UpdateInventoryItemInput { name?: string; category?: string; reorderLevel?: number; sellingPrice?: number }
export async function updateInventoryItem(id: string, input: UpdateInventoryItemInput): Promise<InventoryItem> {
  return adaptInventoryItem(await apiPut<BackendInventoryItem>(`/inventory/${id}`, input))
}

// ---------------- Engagement / Suggestions ----------------
export async function fetchSuggestionRules(): Promise<SuggestionRule[]> {
  return apiGet<SuggestionRule[]>('/suggestion-rules')
}
export async function saveSuggestionRule(rule: SuggestionRule): Promise<SuggestionRule> {
  // blankRule() (Engagement.tsx) stamps new, unsaved rules with a temporary
  // `r-<timestamp>` id since there's no real one yet — that's the signal
  // used here to pick POST (create) vs PUT (update) against the backend,
  // which has no concept of that temporary id itself.
  const payload = {
    name: rule.name, category: rule.category, triggerType: rule.triggerType,
    offsetDays: rule.offsetDays, messageTemplate: rule.messageTemplate,
    active: rule.active, autoSend: rule.autoSend, channel: rule.channel, recurring: rule.recurring,
  }
  if (rule.id.startsWith('r-')) return apiPost<SuggestionRule>('/suggestion-rules', payload)
  return apiPut<SuggestionRule>(`/suggestion-rules/${rule.id}`, payload)
}
export async function toggleSuggestionRule(id: string, active: boolean) {
  await apiPatch(`/suggestion-rules/${id}/active`, undefined, { params: { active } })
}

export async function fetchOutreachQueue(status?: string): Promise<SuggestedOutreach[]> {
  const res = await apiGet<PagedResponse<SuggestedOutreach>>('/outreach', { params: { status, page: 0, size: 100 } })
  return res.content
}
export async function actOnOutreach(id: string, action: 'approve' | 'dismiss', editedMessage?: string) {
  if (action === 'dismiss') return apiPost<SuggestedOutreach>(`/outreach/${id}/dismiss`, { reason: editedMessage })
  if (editedMessage) return apiPost<SuggestedOutreach>(`/outreach/${id}/send-edited`, { editedMessage })
  return apiPost<SuggestedOutreach>(`/outreach/${id}/approve`)
}

// ---------------- Recommendations ----------------
export async function fetchRecommendationRules(): Promise<CategoryRecommendationRule[]> {
  const res = await apiGet<PagedResponse<CategoryRecommendationRule>>('/recommendations/rules', { params: { page: 0, size: 200 } })
  return res.content
}
export async function createRecommendationRule(input: { category: string; productId: string; priority?: number }): Promise<CategoryRecommendationRule> {
  return apiPost<CategoryRecommendationRule>('/recommendations/rules', { ...input, active: true })
}
export async function toggleRecommendationRule(rule: CategoryRecommendationRule, active: boolean): Promise<void> {
  // PUT requires the full RuleRequest shape (category/productId are
  // @NotBlank server-side) — not just the changed field.
  await apiPut(`/recommendations/rules/${rule.id}`, { category: rule.category, productId: rule.productId, priority: rule.priority, active })
}
export async function deleteRecommendationRule(id: string): Promise<void> {
  await apiDelete(`/recommendations/rules/${id}`)
}

export interface BookingRecommendations {
  productAddOns: ProductSuggestion[]
  packageUpgrades: PackageUpgradeSuggestion[]
}

export async function fetchRecommendationsForBooking(bookingId: string): Promise<BookingRecommendations> {
  // GET /recommendations/booking/{bookingId} — see RecommendationController.
  // This was never actually wired to the frontend before; the page that
  // calls it only ever saw an empty placeholder.
  return apiGet<BookingRecommendations>(`/recommendations/booking/${bookingId}`)
}

// ---------------- Notifications ----------------
export async function fetchNotifications(unreadOnly = false): Promise<AppNotification[]> {
  const res = await apiGet<PagedResponse<AppNotification>>('/notifications', { params: { unreadOnly: unreadOnly || undefined, page: 0, size: 50 } })
  return res.content
}
export async function fetchUnreadNotificationCount(): Promise<number> {
  const res = await apiGet<{ count: number }>('/notifications/unread-count')
  return res.count
}
export async function markNotificationRead(id: string) {
  await apiPost(`/notifications/${id}/read`)
}
export async function markAllNotificationsRead() {
  await apiPost('/notifications/mark-all-read')
}

type Listener = (n: AppNotification) => void

/** Live notification delivery: primarily via the backend's STOMP/WebSocket
 *  push (NotificationPublisher -> /user/queue/notifications, see
 *  lib/realtime.ts), with a slower REST poll running alongside as a safety
 *  net for the time between page load and the socket handshake completing,
 *  and for the rare case a push is missed while the socket reconnects.
 *  Both channels feed into the same de-duplication set so a notification
 *  that arrives on one is never re-emitted by the other. */
export function subscribeToLiveNotifications(listener: Listener, pollIntervalMs = 45000): () => void {
  const seen = new Set<string>()
  let initialized = false
  let stopped = false

  function emitIfNew(n: AppNotification) {
    if (seen.has(n.id)) return
    seen.add(n.id)
    if (initialized) listener(n)
  }

  async function poll() {
    if (stopped) return
    try {
      const items = await fetchNotifications()
      if (!initialized) {
        // Seed with what already exists so opening the app doesn't replay
        // the whole history as "new arrival" toasts.
        items.forEach((n) => seen.add(n.id))
        initialized = true
        return
      }
      // Newest-first from the API — walk oldest-to-newest so toasts appear
      // in the order they actually happened.
      items.slice().reverse().forEach(emitIfNew)
    } catch {
      // Transient network/auth errors shouldn't kill the poll loop.
    }
  }

  poll()
  const pollId = setInterval(poll, pollIntervalMs)
  const unsubscribeWs = subscribeToNotificationQueue((body) => {
    // Not connected until after the first poll seeds `seen` — if the
    // socket happens to connect first, still hold pushes until seeded so
    // pre-existing unread notifications don't fire as new arrivals.
    if (!initialized) return
    emitIfNew(body as AppNotification)
  })

  return () => {
    stopped = true
    clearInterval(pollId)
    unsubscribeWs()
  }
}

// ---------------- Catalog: Packages & Products ----------------
export interface CreatePackageInput {
  name: string; description?: string; category: string; basePrice: number
  discountedPrice?: number; durationHours?: number; photoCount?: number
  editedCount?: number; printCount?: number; deliveryDays?: number
}

export async function fetchPackages(opts: { search?: string; category?: string } = {}): Promise<CatalogPackage[]> {
  const res = await apiGet<PagedResponse<CatalogPackage>>('/packages', { params: { search: opts.search, category: opts.category, page: 0, size: 200 } })
  return res.content
}

export async function createPackage(input: CreatePackageInput): Promise<CatalogPackage> {
  return apiPost<CatalogPackage>('/packages', input)
}

export async function archivePackage(id: string): Promise<void> {
  await apiPost(`/packages/${id}/archive`)
}

export interface CreateProductInput {
  name: string; description?: string; categoryId?: string; sku?: string
  price: number; stockQuantity: number; imageUrl?: string
}

export async function fetchProducts(opts: { search?: string } = {}): Promise<CatalogProduct[]> {
  const res = await apiGet<PagedResponse<CatalogProduct>>('/products', { params: { search: opts.search, page: 0, size: 200 } })
  return res.content
}

export async function createProduct(input: CreateProductInput): Promise<CatalogProduct> {
  return apiPost<CatalogProduct>('/products', input)
}

export interface UpdateProductInput {
  name: string; description: string; categoryId: string; price: number; gstRate?: number
  stockQuantity: number; active: boolean; imageUrl: string
}

/** Blank description / imageUrl / categoryId clear that field on the server. */
export async function updateProduct(id: string, input: UpdateProductInput): Promise<CatalogProduct> {
  return apiPut<CatalogProduct>(`/products/${id}`, input)
}

export async function fetchProductCategories(): Promise<CatalogCategory[]> {
  return apiGet<CatalogCategory[]>('/products/categories')
}

export async function createProductCategory(name: string, description?: string): Promise<CatalogCategory> {
  return apiPost<CatalogCategory>('/products/categories', { name, description })
}

export interface BroadcastCampaign {
  id: string; title: string; messageText: string; mediaUrl?: string; mediaType: string
  status: string; totalRecipients: number; sentCount: number; failedCount: number
  createdByName: string; createdAt: string
}

export async function fetchAudiencePreview(): Promise<{ totalEligible: number; sampleNames: string[] }> {
  return apiGet('/broadcasts/audience-preview')
}

export async function fetchCampaigns(): Promise<BroadcastCampaign[]> {
  const res = await apiGet<PagedResponse<BroadcastCampaign>>('/broadcasts', { params: { page: 0, size: 50 } })
  return res.content
}

export async function uploadBroadcastMedia(file: File): Promise<{ url: string; mediaType: string }> {
  const form = new FormData()
  form.append('file', file)
  return apiPostForm('/broadcasts/media', form)
}

export interface CreateCampaignInput {
  title: string; messageText: string; mediaUrl?: string; mediaType?: 'NONE' | 'IMAGE' | 'VIDEO'
}

export async function createCampaign(input: CreateCampaignInput): Promise<BroadcastCampaign> {
  return apiPost<BroadcastCampaign>('/broadcasts', input)
}

// ---------------- Public site: CMS + Contact ----------------
// The backend has a full CMS module (hero slides, testimonials, editable
// pages by key) sitting behind /cms/public/** and a real contact-form
// endpoint at /public/contact — both permitAll in SecurityConfig.
export interface CmsSlide { id: string; title: string; subtitle?: string; imageUrl?: string; ctaText?: string; ctaUrl?: string }
export interface CmsTestimonial { id: string; clientName: string; clientPhotoUrl?: string; review: string; rating: number; packageType?: string }
export interface CmsPage { pageKey: string; title: string; content?: string }

export async function fetchCmsSlides(): Promise<CmsSlide[]> {
  return apiGet<CmsSlide[]>('/cms/public/slides')
}
export async function fetchCmsTestimonials(): Promise<CmsTestimonial[]> {
  return apiGet<CmsTestimonial[]>('/cms/public/testimonials')
}
export async function fetchCmsPage(key: string): Promise<CmsPage | undefined> {
  try { return await apiGet<CmsPage>(`/cms/public/pages/${key}`) } catch { return undefined }
}

export interface ContactInquiryInput { name: string; email: string; mobile?: string; message: string }
export async function submitContactInquiry(input: ContactInquiryInput): Promise<void> {
  await apiPost('/public/contact', input)
}

// ---------------- Indoor: Studio Sessions ----------------
// A fully separate backend module from the generic /bookings used
// elsewhere in this app — studioRoom/backdropNotes/propsRequired are real
// fields on this domain, not shoehorned into the generic booking's notes
// field.
export interface StudioSession {
  id: string; sessionNumber: string; clientName: string; clientMobile?: string
  packageName?: string; sessionDate: string; startTime: string; endTime?: string
  studioRoom?: string; backdropNotes?: string; propsRequired?: string
  photographerName?: string; status: string; totalAmount: number; advanceAmount: number; balanceAmount: number
}
export interface CreateStudioSessionInput {
  clientName: string; clientMobile?: string; clientEmail?: string
  packageName?: string; sessionDate: string; startTime: string; endTime?: string
  studioRoom?: string; backdropNotes?: string; propsRequired?: string
  totalAmount?: number; advanceAmount?: number; notes?: string
}

export async function fetchStudioSessions(opts: { search?: string; status?: string } = {}): Promise<StudioSession[]> {
  const res = await apiGet<PagedResponse<StudioSession>>('/studio-sessions', { params: { search: opts.search, status: opts.status, page: 0, size: 100 } })
  return res.content
}
export async function createStudioSession(input: CreateStudioSessionInput): Promise<StudioSession> {
  return apiPost<StudioSession>('/studio-sessions', input)
}
export async function cancelStudioSession(id: string, reason: string): Promise<void> {
  await apiPost(`/studio-sessions/${id}/cancel`, { reason })
}
export async function rescheduleStudioSession(id: string, sessionDate: string, startTime: string, endTime?: string, reason?: string): Promise<void> {
  await apiPost(`/studio-sessions/${id}/reschedule`, { newDate: sessionDate, newStartTime: startTime, newEndTime: endTime, reason })
}

// ---------------- Outdoor Shoots ----------------
export interface OutdoorShoot {
  id: string; shootNumber: string; clientName: string; clientMobile?: string; category: string
  packageName?: string; shootDate: string; startTime: string; endTime?: string
  venueName?: string; venueAddress?: string; venueCity?: string
  travelDistanceKm?: number; travelCost?: number; weatherDependent: boolean; backupPlanNotes?: string
  photographerName?: string; status: string; totalAmount: number; advanceAmount: number; balanceAmount: number
}
export interface CreateOutdoorShootInput {
  clientName: string; clientMobile?: string; clientEmail?: string; category: string
  packageName?: string; shootDate: string; startTime: string; endTime?: string
  venueName?: string; venueAddress?: string; venueCity?: string
  travelDistanceKm?: number; travelCost?: number; weatherDependent?: boolean; backupPlanNotes?: string
  totalAmount?: number; advanceAmount?: number; notes?: string
}

export async function fetchOutdoorShoots(opts: { search?: string; status?: string } = {}): Promise<OutdoorShoot[]> {
  const res = await apiGet<PagedResponse<OutdoorShoot>>('/outdoor-shoots', { params: { search: opts.search, status: opts.status, page: 0, size: 100 } })
  return res.content
}
export async function createOutdoorShoot(input: CreateOutdoorShootInput): Promise<OutdoorShoot> {
  return apiPost<OutdoorShoot>('/outdoor-shoots', input)
}
export async function cancelOutdoorShoot(id: string, reason: string): Promise<void> {
  await apiPost(`/outdoor-shoots/${id}/cancel`, { reason })
}
export async function rescheduleOutdoorShoot(id: string, shootDate: string, startTime: string, endTime?: string, reason?: string): Promise<void> {
  await apiPost(`/outdoor-shoots/${id}/reschedule`, { newDate: shootDate, newStartTime: startTime, newEndTime: endTime, reason })
}

// ---------------- Verification ----------------
// One generic OTP endpoint handles both purposes server-side (see
// AuthDto.SendOtpRequest's `purpose` field: LOGIN, EMAIL_VERIFICATION,
// MOBILE_VERIFICATION, PASSWORD_RESET) — not two separate endpoints.
export async function sendVerificationOtp(identifier: string, purpose: 'EMAIL_VERIFICATION' | 'MOBILE_VERIFICATION'): Promise<void> {
  await apiPost('/auth/otp/send', { identifier, purpose })
}
export async function verifyOtp(identifier: string, otp: string, purpose: 'EMAIL_VERIFICATION' | 'MOBILE_VERIFICATION'): Promise<void> {
  await apiPost('/auth/otp/verify', { identifier, otp, purpose })
}

// ---------------- Audit Logs ----------------
export interface AuditLogEntry {
  id: string; action: string; entityType: string; entityId: string
  performedBy: string; performedByName: string; details?: string; ipAddress?: string; createdAt: string
}
export async function fetchAuditLogs(opts: { search?: string; page?: number; size?: number } = {}): Promise<PagedResponse<AuditLogEntry>> {
  const { search = '', page = 0, size = 20 } = opts
  return apiGet<PagedResponse<AuditLogEntry>>('/audit-logs', { params: { search, page, size } })
}

// ---------------- CMS admin ----------------
export interface CreateSlideInput { title: string; subtitle?: string; imageUrl?: string; ctaText?: string; ctaUrl?: string; sortOrder?: number; active?: boolean }
export async function fetchAdminSlides(): Promise<CmsSlide[]> {
  return apiGet<CmsSlide[]>('/cms/slides')
}
export async function createCmsSlide(input: CreateSlideInput): Promise<CmsSlide> {
  return apiPost<CmsSlide>('/cms/slides', { ...input, slideType: 'HERO', active: input.active ?? true })
}
export async function updateCmsSlide(id: string, input: CreateSlideInput): Promise<CmsSlide> {
  return apiPut<CmsSlide>(`/cms/slides/${id}`, { ...input, slideType: 'HERO', active: input.active ?? true })
}
export async function deleteCmsSlide(id: string): Promise<void> {
  await apiDelete(`/cms/slides/${id}`)
}

/** Uploads an image (hero slide, logo, watermark) and returns its real,
 *  hosted URL — so admin fields that take an "image URL" can be filled by
 *  uploading a file directly instead of only accepting a pasted link. */
export async function uploadCmsMedia(file: File): Promise<{ url: string }> {
  const form = new FormData()
  form.append('file', file)
  return apiPostForm<{ key: string; url: string }>('/cms/media/upload', form)
}

export interface CreateTestimonialInput { clientName: string; review: string; rating: number; packageType?: string; sortOrder?: number; active?: boolean }
export async function fetchAdminTestimonials(): Promise<CmsTestimonial[]> {
  // The backend only exposes POST/PUT for testimonials, not a dedicated
  // admin list endpoint (checked CmsController directly) — the public read
  // endpoint doubles as the admin list view here since it returns the same
  // data an admin needs to see what already exists before adding more.
  return apiGet<CmsTestimonial[]>('/cms/public/testimonials')
}
export async function createTestimonial(input: CreateTestimonialInput): Promise<CmsTestimonial> {
  return apiPost<CmsTestimonial>('/cms/testimonials', { ...input, active: input.active ?? true })
}

export interface SaveCmsPageInput { title: string; content: string; active?: boolean }
interface BackendCmsPage extends CmsPage { id: string; slug?: string; active?: boolean }

export async function fetchAdminCmsPages(): Promise<CmsPage[]> {
  // GET /cms/pages returns a plain array (List<PageResponse>), not a
  // paginated envelope.
  return apiGet<BackendCmsPage[]>('/cms/pages')
}
export async function saveCmsPage(key: string, input: SaveCmsPageInput): Promise<CmsPage> {
  // POST /cms/pages always creates a new row; PUT /cms/pages/{id} updates
  // one. Calling POST unconditionally (as this used to) would create a
  // duplicate page every time an existing one was edited instead of
  // updating it — so this looks up the existing page by slug/pageKey first
  // and switches to PUT when one already exists.
  const existing = (await fetchAdminCmsPages()).find((p) => (p as BackendCmsPage).slug === key || p.pageKey === key)
  const payload = { ...input, slug: key, active: input.active ?? true, published: input.active ?? true }
  if (existing) return apiPut<CmsPage>(`/cms/pages/${(existing as BackendCmsPage).id}`, payload)
  return apiPost<CmsPage>('/cms/pages', payload)
}

// ---------------- Staff ----------------
// Staff creation is genuinely two backend calls, not one: the account lives
// in the generic `users` table (POST /users, with roleNames), and the
// studio-specific profile — department, designation, employeeId, booking
// limits — lives in a separate `staff` table (POST /staff, referencing that
// userId). See StaffDto.CreateStaffRequest — it requires an existing userId,
// it doesn't create the account itself. createStaffMember() below does both
// calls in sequence and surfaces which step failed if one does.
interface BackendStaffResponse {
  id: string; userId: string; firstName: string; lastName?: string; email: string
  mobile?: string; profilePhotoUrl?: string; roles: string[]; employeeId?: string
  department?: string; designation?: string; joiningDate?: string
  availableForBooking: boolean; maxBookingsPerDay: number
  assignedTasksCount: number; completedTasksCount: number; userStatus?: string
}

function adaptStaff(s: BackendStaffResponse): StaffMember {
  return {
    id: s.id,
    userId: s.userId,
    firstName: s.firstName,
    lastName: s.lastName ?? '',
    email: s.email,
    mobile: s.mobile ?? '',
    profilePhotoUrl: s.profilePhotoUrl,
    roles: s.roles as StaffMember['roles'],
    employeeId: s.employeeId ?? '—',
    department: s.department ?? 'Unassigned',
    designation: s.designation ?? '',
    joiningDate: s.joiningDate ?? '',
    availableForBooking: s.availableForBooking,
    maxBookingsPerDay: s.maxBookingsPerDay,
    assignedTasksCount: s.assignedTasksCount,
    completedTasksCount: s.completedTasksCount,
    userStatus: s.userStatus,
  }
}

export async function fetchStaff(opts: { department?: string; search?: string } = {}): Promise<StaffMember[]> {
  // GET /staff has no free-text `search` param (only department/availableOnly
  // filters) — filtering by name/employeeId is done client-side here rather
  // than silently dropping the search box.
  const res = await apiGet<PagedResponse<BackendStaffResponse>>('/staff', {
    params: { department: opts.department, page: 0, size: 200 },
  })
  let items = res.content.map(adaptStaff)
  if (opts.search) {
    const q = opts.search.toLowerCase()
    items = items.filter((s) => `${s.firstName} ${s.lastName}`.toLowerCase().includes(q) || s.employeeId.toLowerCase().includes(q))
  }
  return items
}

export async function toggleStaffAvailability(id: string) {
  return adaptStaff(await apiPatch<BackendStaffResponse>(`/staff/${id}/toggle-availability`))
}

export interface CreateStaffInput {
  email: string
  password: string
  firstName: string
  lastName?: string
  mobile: string
  role: string // single primary role, e.g. 'PHOTOGRAPHER'
  employeeId?: string
  department: string
  designation?: string
  joiningDate?: string
  maxBookingsPerDay?: number
}

export async function createStaffMember(input: CreateStaffInput): Promise<StaffMember> {
  // Step 1: the account. Step 2: the staff profile referencing it. If step
  // 2 fails, the account still exists (same as it would if a human did
  // this as two manual actions) — the thrown error names which step failed
  // so the caller can show something more useful than "something went wrong".
  let user: { id: string }
  try {
    user = await apiPost<{ id: string }>('/users', {
      email: input.email, password: input.password, firstName: input.firstName,
      lastName: input.lastName, mobile: input.mobile, roleNames: [input.role],
    })
  } catch (err) {
    throw err instanceof ApiError ? err : new ApiError('Could not create the staff account.', 500)
  }
  try {
    return adaptStaff(await apiPost<BackendStaffResponse>('/staff', {
      userId: user.id, employeeId: input.employeeId, department: input.department,
      designation: input.designation, joiningDate: input.joiningDate || undefined,
      availableForBooking: true, maxBookingsPerDay: input.maxBookingsPerDay || 5,
    }))
  } catch (err) {
    const msg = err instanceof ApiError ? err.message : 'Could not create the staff profile.'
    throw new ApiError(`Account was created, but the staff profile failed: ${msg}. Edit the user record directly to finish setup.`, 500)
  }
}

// ---------------- Attendance ----------------
interface BackendDaySummary {
  staffId: string; staffName: string; firstPunch: string | null; lastPunch: string | null
  punchCount: number; status: DayAttendanceStatus
  workedMinutes?: number; lateMinutes?: number; overtimeMinutes?: number
}

export async function fetchDaySummary(date: string): Promise<(DaySummary & { status: DayAttendanceStatus })[]> {
  const rows = await apiGet<BackendDaySummary[]>('/attendance/summary', { params: { date } })
  return rows.map((r) => ({
    staffId: r.staffId, staffName: r.staffName, firstPunch: r.firstPunch,
    lastPunch: r.lastPunch, punchCount: r.punchCount, status: r.status,
    workedMinutes: r.workedMinutes, lateMinutes: r.lateMinutes, overtimeMinutes: r.overtimeMinutes,
  }))
}

interface BackendMonthlyReport extends MonthlyReport {}

export async function fetchMonthlyReport(staffId: string, year: number, month: number): Promise<MonthlyReport> {
  return apiGet<BackendMonthlyReport>('/attendance/monthly-report', { params: { staffId, year, month } })
}

export async function fetchWorkHoursConfig(): Promise<WorkHoursConfig> {
  return apiGet<WorkHoursConfig>('/attendance/work-hours-config')
}
export async function updateWorkHoursConfig(config: WorkHoursConfig): Promise<WorkHoursConfig> {
  return apiPut<WorkHoursConfig>('/attendance/work-hours-config', config)
}

export async function fetchWebhookConfig() {
  return apiGet<WebhookConfig>('/attendance/webhook-config')
}
export async function regenerateWebhookSecret() {
  return apiPost<WebhookConfig>('/attendance/webhook-config/regenerate')
}

export async function fetchUnmatchedCodes() {
  return apiGet<UnmatchedCode[]>('/attendance/unmatched')
}
export async function linkEmployeeCode(employeeCode: string, staffId: string) {
  await apiPost('/attendance/link', { employeeCode, staffId })
  return { employeeCode, staffId }
}

export async function importAttendanceCsv(file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiPostForm<{ totalRows: number; imported: number; duplicates?: number; skipped: number; errors: string[] }>('/attendance/import', form)
}

export async function manualAttendanceEntry(staffId: string, punchTime: string, direction: 'IN' | 'OUT' | 'UNKNOWN', notes?: string) {
  return apiPost('/attendance', { staffId, punchTime, direction, notes })
}

// ---------------- Client & staff detail drawers ----------------
export interface ClientOverview {
  id: string; firstName: string; lastName?: string; email: string; mobile?: string
  address?: string; notes?: string; status: string
  emailVerified: boolean; mobileVerified: boolean; marketingOptIn: boolean
  createdAt: string; lastLoginAt?: string
  bookingCount: number; completedBookings: number; cancelledBookings: number
  totalBilled: number; totalPaid: number; balanceDue: number; lastBookingDate?: string
  /** Every booking the client has (the field keeps its old name; it is no longer limited to the latest 10). */
  recentBookings: { id: string; bookingNumber: string; bookingDate: string; packageName?: string; shootCategory?: string; status: string; orderStage?: string; totalAmount: number; balanceAmount: number; locationType?: 'INDOOR' | 'OUTDOOR'; invoiceId?: string }[]
  /** Every invoice the client has. */
  recentInvoices: { id: string; invoiceNumber: string; invoiceDate: string; status: string; totalAmount: number; amountPaid: number; balanceDue: number; sourceId?: string }[]
  galleries: { id: string; name: string; status: string; photoCount: number; publishedAt?: string }[]
  /** Indoor studio sessions and outdoor shoots (separate records from bookings). */
  studioSessionsAndShoots?: { type: 'STUDIO_SESSION' | 'OUTDOOR_SHOOT'; id: string; number: string; date: string; packageName?: string; status: string; totalAmount: number; balanceAmount: number; invoiceId?: string }[]
  selections?: { id: string; galleryId: string; galleryName: string; status: string; selectedCount: number; selectionLimit?: number }[]
}
export type ClientUpdateInput = { firstName: string; lastName?: string; email?: string; mobile?: string; address?: string; notes?: string; marketingOptIn?: boolean }
export const fetchClientOverview = (id: string) => apiGet<ClientOverview>(`/clients/${id}/overview`)
export const updateClient = (id: string, b: ClientUpdateInput) => apiPut<ClientOverview>(`/clients/${id}`, b)

export interface StaffShoot { id: string; bookingNumber: string; bookingDate: string; packageName?: string; shootCategory?: string; status: string; clientName?: string }
export interface StaffOverview {
  profile: StaffMember
  totalShoots: number; completedShoots: number
  upcomingShoots: StaffShoot[]; recentShoots: StaffShoot[]
  attendanceYear: number; attendanceMonth: number
  presentDays: number; lateDays: number; halfDays: number; absentDays: number; leaveDays: number; workingDays: number
  overtimeMinutes: number; shiftName?: string | null
  recentLeaves: { id: string; fromDate: string; toDate: string; dayCount: number; status: string; reason?: string }[]
}
export async function fetchStaffOverview(id: string): Promise<StaffOverview> {
  const o = await apiGet<Omit<StaffOverview, 'profile'> & { profile: BackendStaffResponse }>(`/staff/${id}/overview`)
  return { ...o, profile: adaptStaff(o.profile) }
}

// ---------------- Shifts, schedules, holidays, payroll ----------------
export type ShiftInput = { name: string; startTime: string; endTime: string; graceMinutes?: number; active?: boolean }
export const fetchShifts = () => apiGet<WorkShift[]>('/attendance/shifts')
export const createShift = (b: ShiftInput) => apiPost<WorkShift>('/attendance/shifts', b)
export const updateShift = (id: string, b: ShiftInput) => apiPut<WorkShift>(`/attendance/shifts/${id}`, b)
export const deleteShift = (id: string) => apiDelete<void>(`/attendance/shifts/${id}`)

export const fetchSchedules = () => apiGet<StaffScheduleRow[]>('/attendance/schedules')
export type ScheduleInput = { shiftId: string | null; weeklyOffDays: string[]; monthlySalary: number | null; overtimeRatePerHour: number | null }
export const updateSchedule = (staffId: string, b: ScheduleInput) => apiPut<StaffScheduleRow>(`/attendance/schedules/${staffId}`, b)

export const fetchHolidays = (year: number) => apiGet<AttendanceHolidayRow[]>('/attendance/holidays', { params: { year } })
export const addHoliday = (date: string, name: string) => apiPost<AttendanceHolidayRow>('/attendance/holidays', { date, name })
export const deleteHoliday = (id: string) => apiDelete<void>(`/attendance/holidays/${id}`)

export const fetchPayroll = (year: number, month: number) => apiGet<PayrollReport>('/attendance/payroll', { params: { year, month } })
export async function downloadPayrollCsv(year: number, month: number): Promise<void> {
  const blob = await apiGetBlob('/attendance/payroll/export', { params: { year, month } })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `payroll-${year}-${String(month).padStart(2, '0')}.csv`
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

// ---------------- Leave (staff attendance leave, separate from client engagement) ----------------
export async function fetchLeaveRequests(status?: LeaveStatus): Promise<StaffLeaveRequest[]> {
  return apiGet<StaffLeaveRequest[]>('/leaves', { params: { status } })
}
export async function actOnLeaveRequest(id: string, action: 'approve' | 'reject') {
  return apiPost<StaffLeaveRequest>(`/leaves/${id}/${action}`)
}

// ---------------- Payments ----------------
export interface RazorpayOrder {
  orderId: string
  amount: number
  currency: string
  keyId: string
  paymentId: string
}

export interface CreatePaymentOrderInput {
  invoiceId?: string
  bookingId?: string
  clientId?: string
  clientName?: string
  amount: number
  paymentType: 'ADVANCE' | 'PARTIAL' | 'FULL'
  notes?: string
}

export async function createPaymentOrder(input: CreatePaymentOrderInput): Promise<RazorpayOrder> {
  return apiPost<RazorpayOrder>('/payments/razorpay/create-order', input)
}

export interface VerifyPaymentInput {
  razorpayOrderId: string
  razorpayPaymentId: string
  razorpaySignature: string
}

export async function verifyRazorpayPayment(input: VerifyPaymentInput): Promise<void> {
  await apiPost('/payments/razorpay/verify', input)
}

export interface PaymentRecord {
  id: string; invoiceId?: string; bookingId?: string; clientId?: string; clientName?: string
  amount: number; currency: string; paymentMethod?: string; status: string; paymentType: string
  razorpayOrderId?: string; razorpayPaymentId?: string; transactionId?: string
  paymentDate?: string; notes?: string; refundedAmount?: number; refundedAt?: string; createdAt: string
}

export async function fetchPayments(opts: { search?: string; status?: string; page?: number; size?: number } = {}) {
  const { search = '', status = '', page = 0, size = 20 } = opts
  return apiGet<PagedResponse<PaymentRecord>>('/payments', { params: { search, status, page, size } })
}

export interface ManualPaymentInput {
  invoiceId?: string; bookingId?: string; clientId?: string; clientName?: string
  amount: number; paymentMethod: 'CASH' | 'UPI' | 'CARD' | 'NET_BANKING' | 'BANK_TRANSFER' | 'CHEQUE'
  paymentType: 'ADVANCE' | 'PARTIAL' | 'FULL'; transactionId?: string; transactionReference?: string; notes?: string
}

export async function recordManualPayment(input: ManualPaymentInput): Promise<PaymentRecord> {
  return apiPost<PaymentRecord>('/payments/manual', input)
}

export async function refundPayment(id: string, amount: number, reason: string): Promise<PaymentRecord> {
  return apiPost<PaymentRecord>(`/payments/${id}/refund`, { amount, reason })
}

// ---------------- Contact inquiries ----------------
export interface ContactInquiry {
  id: string; name: string; email: string; mobile?: string; message: string
  status: 'NEW' | 'CONTACTED' | 'CLOSED'; createdAt: string
  replyCount?: number; lastRepliedAt?: string; lastRepliedBy?: string
}

export type ReplyChannel = 'EMAIL' | 'PHONE' | 'WHATSAPP' | 'OTHER'
export interface InquiryReply { id: string; channel: ReplyChannel; message: string; emailed: boolean; repliedByName?: string; createdAt: string }
export const fetchInquiryReplies = (id: string) => apiGet<InquiryReply[]>(`/contact/${id}/replies`)
export const replyToInquiry = (id: string, body: { channel: ReplyChannel; message: string; sendEmail: boolean }) =>
  apiPost<ContactInquiry>(`/contact/${id}/replies`, body)

export async function fetchContactInquiries(opts: { page?: number; size?: number } = {}) {
  const { page = 0, size = 20 } = opts
  return apiGet<PagedResponse<ContactInquiry>>('/contact', { params: { page, size } })
}

export async function updateContactInquiryStatus(id: string, status: ContactInquiry['status']): Promise<ContactInquiry> {
  return apiPatch<ContactInquiry>(`/contact/${id}/status`, undefined, { params: { status } })
}

// ---------------- Reports & exports ----------------
export type ReportKind = 'bookings' | 'revenue' | 'invoices' | 'galleries' | 'inventory' | 'clients'

const REPORT_NEEDS_DATE_RANGE: Record<ReportKind, boolean> = {
  bookings: true, revenue: true, invoices: true, galleries: true, inventory: false, clients: false,
}

export function reportNeedsDateRange(kind: ReportKind): boolean {
  return REPORT_NEEDS_DATE_RANGE[kind]
}

/** Downloads one of the six .xlsx exports from GET /reports/{kind}/export
 *  and triggers the browser's normal file-save. from/to (yyyy-MM-dd) are
 *  required for the four report kinds that are period reports; ignored by
 *  the backend for the other two (inventory, clients are point-in-time
 *  snapshots). */
export async function downloadReport(kind: ReportKind, from?: string, to?: string): Promise<void> {
  const blob = await apiGetBlob(`/reports/${kind}/export`, { params: reportNeedsDateRange(kind) ? { from, to } : undefined })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${kind}-report.xlsx`
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

// ---------------- Albums ----------------
export type AlbumStatus = 'PENDING' | 'DESIGN_IN_PROGRESS' | 'REVIEW' | 'REVISION_REQUESTED' | 'APPROVED' | 'PRINTING' | 'DELIVERED'

export interface Album {
  id: string; bookingId?: string; clientId?: string; clientName?: string
  name: string; albumType: string; material?: string; coverType?: string
  pageCount: number; includedPages: number; basePrice: number; pricePerExtraPage?: number; totalPrice: number
  status: AlbumStatus; revisionCount: number; maxRevisions: number; notes?: string
  approvedAt?: string; createdAt: string
  coverPhotoId?: string; photoCount?: number
}

export interface AlbumPhoto {
  id: string; photoId: string; position: number; pageNumber?: number; note?: string
  filename: string; thumbnailUrl?: string; previewUrl?: string; cover: boolean
}

export const fetchAlbumPhotos = (albumId: string) => apiGet<AlbumPhoto[]>(`/albums/${albumId}/photos`)
export const setAlbumPhotos = (albumId: string, photoIds: string[]) =>
  apiPut<AlbumPhoto[]>(`/albums/${albumId}/photos`, { photoIds })
export const addSelectionToAlbum = (albumId: string, selectionId: string) =>
  apiPost<AlbumPhoto[]>(`/albums/${albumId}/photos/from-selection/${selectionId}`)
export const updateAlbumPhoto = (albumId: string, photoId: string, body: { pageNumber?: number | null; note?: string | null }) =>
  apiPatch<AlbumPhoto>(`/albums/${albumId}/photos/${photoId}`, body)
export const setAlbumCover = (albumId: string, photoId: string | null) =>
  apiPut<Album>(`/albums/${albumId}/cover`, undefined, { params: { photoId: photoId ?? undefined } })
export const fetchGalleriesForBooking = async (bookingId: string): Promise<Gallery[]> =>
  (await apiGet<BackendGallery[]>(`/galleries/booking/${bookingId}`)).map(adaptGallery)

export interface CreateAlbumInput {
  bookingId?: string; clientId?: string; clientName?: string
  name: string; albumType: string; material?: string; coverType?: string
  pageCount?: number; includedPages?: number; basePrice?: number; pricePerExtraPage?: number; notes?: string
}

export async function fetchAlbums(opts: { status?: string; page?: number; size?: number } = {}) {
  const { status = '', page = 0, size = 20 } = opts
  return apiGet<PagedResponse<Album>>('/albums', { params: { status, page, size } })
}

export async function fetchAlbumsForBooking(bookingId: string): Promise<Album[]> {
  return apiGet<Album[]>(`/albums/booking/${bookingId}`)
}

export async function createAlbum(input: CreateAlbumInput): Promise<Album> {
  return apiPost<Album>('/albums', input)
}

export async function updateAlbum(id: string, input: CreateAlbumInput): Promise<Album> {
  return apiPut<Album>(`/albums/${id}`, input)
}

export async function updateAlbumStatus(id: string, status: AlbumStatus, notes?: string): Promise<Album> {
  return apiPatch<Album>(`/albums/${id}/status`, undefined, { params: { status, notes } })
}

export async function requestAlbumRevision(id: string, notes: string): Promise<Album> {
  return apiPost<Album>(`/albums/${id}/revision`, undefined, { params: { notes } })
}

// ---------------- Quick search ----------------
export interface SearchResultItem { id: string; type: string; title: string; subtitle?: string; linkHint: string }
export interface QuickSearchResult {
  query: string
  clients: SearchResultItem[]
  bookings: SearchResultItem[]
  galleries: SearchResultItem[]
  invoices: SearchResultItem[]
  products: SearchResultItem[]
}

export async function fetchQuickSearch(q: string): Promise<QuickSearchResult> {
  return apiGet<QuickSearchResult>('/search/quick', { params: { q } })
}

/** Backend's linkHint (e.g. "/bookings/{id}") is a bare resource path — this
 *  maps it to a real admin route. Clients and products have no dedicated
 *  detail route in this app yet, so those fall back to their list page. */
export function resolveSearchResultRoute(item: SearchResultItem): string {
  switch (item.type) {
    case 'BOOKING': return `/admin/bookings/${item.id}`
    case 'GALLERY': return `/admin/galleries/${item.id}`
    case 'INVOICE': return `/admin/invoices/${item.id}`
    case 'CLIENT': return '/admin/clients'
    case 'PRODUCT': return '/admin/catalog'
    default: return '/admin'
  }
}

// ---------------- Studio Settings ----------------
export interface StudioSettings {
  studioName?: string
  ownerName?: string
  tagline?: string
  logoUrl?: string
  watermarkText?: string
  watermarkLogoUrl?: string
  gstNumber?: string
  address?: string
  phone?: string
  email?: string
  whatsappNumber?: string
  instagramUrl?: string
  facebookUrl?: string
  youtubeUrl?: string
  mapUrl?: string
  footerTagline?: string
  aboutStatsJson?: string
  servicesJson?: string
  bankAccount?: string
  bankIfsc?: string
  bankName?: string
  upiId?: string
  themePrimaryColor?: string
  themeSecondaryColor?: string
  bookingLeadTimeHours?: number
  cancellationWindowHours?: number
  rescheduleWindowHours?: number
  /** Minutes of inactivity before auto sign-out; 0 = never. */
  sessionIdleTimeoutMinutes?: number
}

export async function fetchStudioSettings(): Promise<StudioSettings> {
  return apiGet<StudioSettings>('/settings/studio')
}

/** PUT /settings takes a flat Map<String,String> of key/value pairs (see
 *  SettingsDto.UpdateSettingsRequest) — every value goes over the wire as a
 *  string regardless of its logical type, and only the keys actually
 *  present in the map are touched (partial update). */
export async function updateStudioSettings(settings: Partial<StudioSettings>): Promise<void> {
  const flat: Record<string, string> = {}
  for (const [key, value] of Object.entries(settings)) {
    if (value !== undefined && value !== null) flat[key] = String(value)
  }
  await apiPut('/settings', { settings: flat })
}

export interface PublicSiteInfo {
  studioName?: string
  tagline?: string
  footerTagline?: string
  address?: string
  phone?: string
  email?: string
  whatsappNumber?: string
  instagramUrl?: string
  facebookUrl?: string
  youtubeUrl?: string
  mapUrl?: string
  aboutStatsJson?: string
  servicesJson?: string
}

/** No-auth endpoint for the public marketing site — see
 *  SettingsController#getPublicSiteInfo. Deliberately a smaller shape than
 *  StudioSettings; never put bank/GST fields on this type. */
export async function fetchPublicSiteInfo(): Promise<PublicSiteInfo> {
  return apiGet<PublicSiteInfo>('/settings/public-info')
}

export interface AboutStat { value: string; label: string }
export function parseAboutStats(json?: string): AboutStat[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed.filter((s) => s && s.value && s.label) : []
  } catch {
    return []
  }
}

export interface PublicService { icon: string; title: string; desc: string }
export function parseServices(json?: string): PublicService[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed.filter((s) => s && s.title) : []
  } catch {
    return []
  }
}

// ---------------- Intake questions ----------------
export type IntakeFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT'
export interface IntakeQuestion {
  id: string; category: string; questionKey: string; questionText: string
  fieldType: IntakeFieldType; options?: string; required: boolean; sortOrder: number; active: boolean
}
export interface IntakeQuestionInput {
  category: string; questionKey: string; questionText: string
  fieldType: IntakeFieldType; options?: string; required: boolean; sortOrder: number
}

export async function fetchIntakeQuestionsForCategory(category: string): Promise<IntakeQuestion[]> {
  return apiGet<IntakeQuestion[]>(`/intake-questions/category/${encodeURIComponent(category)}`)
}
export async function fetchAllIntakeQuestions(): Promise<IntakeQuestion[]> {
  return apiGet<IntakeQuestion[]>('/intake-questions')
}
export async function createIntakeQuestion(input: IntakeQuestionInput): Promise<IntakeQuestion> {
  return apiPost<IntakeQuestion>('/intake-questions', input)
}
export async function updateIntakeQuestion(id: string, input: IntakeQuestionInput): Promise<IntakeQuestion> {
  return apiPut<IntakeQuestion>(`/intake-questions/${id}`, input)
}
export async function setIntakeQuestionActive(id: string, active: boolean): Promise<void> {
  await apiPatch(`/intake-questions/${id}/active`, undefined, { params: { active } })
}
export async function submitIntakeAnswers(bookingId: string, answers: Record<string, string>): Promise<void> {
  await apiPost(`/intake-questions/bookings/${bookingId}/answers`, { answers })
}
export async function fetchIntakeAnswers(bookingId: string): Promise<Record<string, string>> {
  return apiGet<Record<string, string>>(`/intake-questions/bookings/${bookingId}/answers`)
}

// ---------------- Account / security ----------------
export async function requestPasswordReset(email: string): Promise<void> {
  await apiPost('/auth/forgot-password', { email }, { skipAuthRetry: true })
}
export async function resetPassword(token: string, newPassword: string, confirmPassword: string): Promise<void> {
  await apiPost('/auth/reset-password', { token, newPassword, confirmPassword }, { skipAuthRetry: true })
}
export async function changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Promise<void> {
  await apiPost('/auth/change-password', { currentPassword, newPassword, confirmPassword })
}
export async function logoutAllDevices(): Promise<void> {
  await apiPost('/auth/logout-all')
}
export interface UpdateProfileInput { firstName?: string; lastName?: string; mobile?: string; address?: string; profilePhotoUrl?: string }
export async function updateMyProfile(input: UpdateProfileInput): Promise<void> {
  await apiPut('/users/me', input)
}
export async function setMarketingPreference(optIn: boolean): Promise<void> {
  await apiPut('/users/me/marketing-preference', undefined, { params: { optIn } })
}

// ---------------- Photo selection (proofing) ----------------
export type SelectionStatus = 'OPEN' | 'LOCKED' | 'APPROVED' | 'EXPIRED'
export interface PhotoSelectionInfo {
  id: string; galleryId: string; bookingId?: string; clientId?: string; clientName?: string
  accessToken: string; selectionLimit?: number; selectedCount: number; status: SelectionStatus
  lockedAt?: string; expiresAt?: string; adminNotes?: string; clientNotes?: string; createdAt?: string
  /** When the picks were last saved (draft or submitted). */
  lastSavedAt?: string
}
export interface SelectedPhotoInfo { id: string; photoId: string; selectedAt?: string; favourite: boolean; notes?: string }

export async function fetchGalleryByToken(token: string): Promise<Gallery> {
  return adaptGallery(await apiGet<BackendGallery>(`/galleries/public/${encodeURIComponent(token)}`))
}
export async function fetchMySelections(): Promise<PhotoSelectionInfo[]> {
  return apiGet<PhotoSelectionInfo[]>('/selections/mine')
}
export async function fetchSelectionByToken(token: string): Promise<PhotoSelectionInfo> {
  return apiGet<PhotoSelectionInfo>(`/selections/token/${encodeURIComponent(token)}`)
}
export async function selectPhoto(token: string, photoId: string): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>(`/selections/token/${encodeURIComponent(token)}/select/${photoId}`)
}
export async function deselectPhoto(token: string, photoId: string): Promise<PhotoSelectionInfo> {
  return apiDelete<PhotoSelectionInfo>(`/selections/token/${encodeURIComponent(token)}/deselect/${photoId}`)
}
/** Saves the whole pick list as a draft (replaces what was saved before). Nothing is sent to the studio. */
export async function saveSelectionDraft(token: string, photoIds: string[], clientNotes?: string): Promise<PhotoSelectionInfo> {
  return apiPut<PhotoSelectionInfo>(`/selections/token/${encodeURIComponent(token)}/draft`, { photoIds, clientNotes })
}
export async function lockSelection(token: string, clientNotes?: string): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>(`/selections/token/${encodeURIComponent(token)}/lock`, undefined, { params: { clientNotes } })
}
export async function fetchSelectedPhotos(selectionId: string): Promise<SelectedPhotoInfo[]> {
  return apiGet<SelectedPhotoInfo[]>(`/selections/${selectionId}/photos`)
}
export async function fetchAllSelections(opts: { status?: string; page?: number; size?: number } = {}) {
  return apiGet<PagedResponse<PhotoSelectionInfo>>('/selections', { params: { status: opts.status, page: opts.page ?? 0, size: opts.size ?? 20 } })
}
export async function createSelection(input: { galleryId: string; bookingId?: string; clientId?: string; clientName?: string; selectionLimit?: number; expiresAt?: string; adminNotes?: string }): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>('/selections', input)
}
export async function approveSelection(id: string): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>(`/selections/${id}/approve`)
}
export async function reopenSelection(id: string): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>(`/selections/${id}/reopen`)
}
export async function lockSelectionByStaff(id: string, staffNotes?: string): Promise<PhotoSelectionInfo> {
  return apiPost<PhotoSelectionInfo>(`/selections/${id}/lock`, undefined, { params: { staffNotes } })
}
export async function fetchSelectionsForGallery(galleryId: string): Promise<PhotoSelectionInfo[]> {
  // The staff list is paginated; galleries rarely have more than a handful of selections.
  const res = await fetchAllSelections({ size: 200 })
  return res.content.filter((s) => s.galleryId === galleryId)
}

/** Saves each photo through the browser's normal download flow, one at a time. */
export async function downloadPhotos(photoIds: string[]): Promise<void> {
  for (const id of photoIds) {
    const url = await fetchPhotoDownloadUrl(id, true)
    const a = document.createElement('a')
    a.href = url
    a.rel = 'noopener'
    a.download = ''
    document.body.appendChild(a)
    a.click()
    a.remove()
    await new Promise((r) => setTimeout(r, 400)) // browsers drop rapid-fire downloads
  }
}

// ---------------- Invoice lifecycle ----------------
export async function finalizeInvoice(id: string): Promise<void> {
  await apiPost(`/billing/invoices/${id}/finalize`)
}
export async function cancelInvoice(id: string, reason: string): Promise<void> {
  await apiPost(`/billing/invoices/${id}/cancel`, undefined, { params: { reason } })
}
export async function sendInvoiceWhatsApp(id: string): Promise<void> {
  await apiPost(`/billing/invoices/${id}/send-whatsapp`)
}
export async function sendInvoiceEmail(id: string): Promise<void> {
  await apiPost(`/billing/invoices/${id}/send-email`)
}

// ---------------- Staff editing, users and roles ----------------
export interface UpdateStaffInput {
  employeeId?: string; department?: string; designation?: string; joiningDate?: string
  specializations?: string; bio?: string; maxBookingsPerDay?: number; availableForBooking?: boolean
}
export async function updateStaffProfile(id: string, input: UpdateStaffInput): Promise<StaffMember> {
  return adaptStaff(await apiPut<BackendStaffResponse>(`/staff/${id}`, input))
}
export async function updateUserAccount(userId: string, input: { firstName?: string; lastName?: string; mobile?: string; roleNames?: string[] }): Promise<void> {
  await apiPut(`/users/${userId}`, input)
}
export async function setUserActive(userId: string, active: boolean): Promise<void> {
  await apiPost(`/users/${userId}/${active ? 'activate' : 'deactivate'}`)
}

export interface PermissionInfo { id: string; module: string; action: string; description?: string; active: boolean }
export interface RoleInfo { id: string; name: string; description?: string; active: boolean; permissions: PermissionInfo[] }
export async function fetchRoles(): Promise<RoleInfo[]> { return apiGet<RoleInfo[]>('/roles') }
export async function fetchPermissions(): Promise<PermissionInfo[]> { return apiGet<PermissionInfo[]>('/roles/permissions') }
export async function createRole(name: string, description?: string): Promise<RoleInfo> {
  return apiPost<RoleInfo>('/roles', { name, description })
}
export async function updateRole(id: string, input: { description?: string; active?: boolean }): Promise<RoleInfo> {
  return apiPut<RoleInfo>(`/roles/${id}`, input)
}
export async function assignRolePermissions(id: string, permissionIds: string[]): Promise<RoleInfo> {
  return apiPut<RoleInfo>(`/roles/${id}/permissions`, permissionIds)
}

// ---------------- Booking operations, calendar, blocked dates ----------------
export interface CalendarEvent {
  id: string; bookingNumber: string; title: string; date: string; startTime?: string; endTime?: string
  status: string; type?: string; shootCategory?: string; clientName?: string; photographerName?: string
}
export async function fetchCalendarEvents(from: string, to: string): Promise<CalendarEvent[]> {
  return apiGet<CalendarEvent[]>('/bookings/calendar', { params: { from, to } })
}
export async function blockDates(dates: string[], reason?: string): Promise<BlockedDate[]> {
  return apiPost<BlockedDate[]>('/bookings/blocked-dates', { dates, reason })
}
export async function unblockDate(date: string): Promise<void> {
  await apiDelete(`/bookings/blocked-dates/${date}`)
}
export async function assignBookingEditor(id: string, editorId: string): Promise<void> {
  await apiPatch(`/bookings/${id}/editor`, { editorId })
}
export async function updateBookingCourier(id: string, courierProvider: string, courierTrackingNumber: string): Promise<void> {
  await apiPatch(`/bookings/${id}/courier`, { courierProvider, courierTrackingNumber })
}

// ---------------- Notification preferences ----------------
export interface NotificationPreferenceView { category: string; label: string; description: string; mandatory: boolean; enabled: boolean }
export async function fetchNotificationPreferences(): Promise<NotificationPreferenceView[]> {
  return apiGet<NotificationPreferenceView[]>('/notifications/preferences')
}
export async function setNotificationPreference(category: string, enabled: boolean): Promise<void> {
  await apiPut('/notifications/preferences', { category, enabled })
}

// ---------------- Attendance devices ----------------
export interface AttendanceDeviceInfo { serial: string; label?: string; allowed: boolean; firstSeenAt: string; lastSeenAt: string; punchCount: number }
export async function fetchAttendanceDevices(): Promise<AttendanceDeviceInfo[]> {
  return apiGet<AttendanceDeviceInfo[]>('/attendance/devices')
}
export async function updateAttendanceDevice(serial: string, body: { allowed?: boolean; label?: string }): Promise<AttendanceDeviceInfo> {
  return apiPatch<AttendanceDeviceInfo>(`/attendance/devices/${encodeURIComponent(serial)}`, body)
}

// ---------------- Phase 10: clone, barcode, testimonials, broadcast detail, my profile ----------------
export async function clonePackage(id: string): Promise<void> {
  await apiPost(`/packages/${id}/clone`)
}
/** One scan = one movement. mode OUT takes stock out (default), IN puts it back. */
export async function scanInventory(barcode: string, mode: 'IN' | 'OUT' = 'OUT', quantity = 1): Promise<InventoryItem> {
  return adaptInventoryItem(await apiPost<BackendInventoryItem>(`/inventory/barcode/${encodeURIComponent(barcode.trim())}/scan`, { mode, quantity }))
}
export async function scanProduct(code: string, mode: 'IN' | 'OUT' = 'OUT', quantity = 1): Promise<CatalogProduct> {
  return apiPost<CatalogProduct>(`/products/barcode/${encodeURIComponent(code.trim())}/scan`, { mode, quantity })
}
export async function lookupInventoryByBarcode(barcode: string): Promise<InventoryItem> {
  return adaptInventoryItem(await apiGet<BackendInventoryItem>(`/inventory/barcode/${encodeURIComponent(barcode.trim())}`))
}
export async function updateTestimonial(id: string, input: { clientName: string; review: string; rating: number; packageType?: string; active?: boolean; sortOrder?: number }): Promise<void> {
  await apiPut(`/cms/testimonials/${id}`, input)
}
export interface BroadcastRecipient { id: string; name: string; mobile: string; status: string; errorMessage?: string; sentAt?: string }
export async function fetchCampaignRecipients(id: string, page = 0): Promise<PagedResponse<BroadcastRecipient>> {
  return apiGet<PagedResponse<BroadcastRecipient>>(`/broadcasts/${id}/recipients`, { params: { page, size: 50 } })
}
export async function fetchCampaign(id: string): Promise<BroadcastCampaign> {
  return apiGet<BroadcastCampaign>(`/broadcasts/${id}`)
}
export async function fetchMyStaffProfile(): Promise<StaffMember> {
  return adaptStaff(await apiGet<BackendStaffResponse>('/staff/me'))
}

// ---------------- Staff self-service (leave) ----------------
export async function fetchMyLeaves(): Promise<StaffLeaveRequest[]> {
  return apiGet<StaffLeaveRequest[]>('/leaves/mine')
}
export async function applyForLeave(staffId: string, fromDate: string, toDate: string, reason?: string): Promise<StaffLeaveRequest> {
  return apiPost<StaffLeaveRequest>('/leaves', { staffId, fromDate, toDate, reason })
}
export async function cancelLeave(id: string): Promise<void> {
  await apiPost(`/leaves/${id}/cancel`)
}

// ---------------- Attendance corrections (missing punch) ----------------
export interface AttendanceCorrectionInfo {
  id: string; staffId: string; staffName?: string; proposedTime: string; direction: string; reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'; reviewedByName?: string; reviewedAt?: string; reviewNotes?: string; createdAt: string
}
export async function requestPunchCorrection(proposedTime: string, direction: string, reason: string): Promise<AttendanceCorrectionInfo> {
  return apiPost<AttendanceCorrectionInfo>('/attendance-corrections', { proposedTime, direction, reason })
}
export async function fetchMyCorrections(): Promise<AttendanceCorrectionInfo[]> {
  return apiGet<AttendanceCorrectionInfo[]>('/attendance-corrections/mine')
}
export async function fetchCorrections(status = 'PENDING'): Promise<AttendanceCorrectionInfo[]> {
  return apiGet<AttendanceCorrectionInfo[]>('/attendance-corrections', { params: { status } })
}
export async function reviewCorrection(id: string, action: 'approve' | 'reject', notes?: string): Promise<void> {
  await apiPost(`/attendance-corrections/${id}/${action}`, { notes })
}


// ---------------- Gallery sharing (who can see / pick photos) ----------------
export interface GalleryViewer {
  clientId: string; name: string; email?: string; mobile?: string
  canSelect: boolean; primary: boolean; selectionStatus?: string; selectedCount: number
}
export const fetchGalleryViewers = (galleryId: string) => apiGet<GalleryViewer[]>(`/galleries/${galleryId}/viewers`)
/** Replaces the extra viewers. The gallery's primary client is implicit and ignored here. */
export const saveGalleryViewers = (galleryId: string, viewers: { clientId: string; canSelect: boolean }[]) =>
  apiPut<GalleryViewer[]>(`/galleries/${galleryId}/viewers`, { viewers })
