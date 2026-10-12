export type Role = 'ADMIN' | 'SUPER_ADMIN' | 'MANAGER' | 'RECEPTIONIST' | 'PHOTOGRAPHER' | 'EDITOR' | 'ACCOUNTANT' | 'INVENTORY_MANAGER' | 'CLIENT'

export interface AuthUser {
  id: string
  firstName: string
  lastName: string
  email: string
  mobile: string
  roles: Role[]
  /** "MODULE:ACTION" codes from custom roles (empty for the built-in roles). */
  permissions?: string[]
  profilePhotoUrl?: string
  marketingOptIn: boolean
}

export type ShootCategory = 'CASUAL' | 'WEDDING' | 'PRE_WEDDING' | 'MATERNITY' | 'NEWBORN' | 'BIRTHDAY' | 'FAMILY' | 'CORPORATE' | 'EVENT' | 'PRODUCT' | 'PORTFOLIO' | 'OTHER'

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED' | 'NO_SHOW'
// Mirrors backend OrderStage enum exactly (booking/domain/OrderStage.java).
export type OrderStage =
  | 'BOOKING_CONFIRMED' | 'ADVANCE_PAID' | 'SHOOT_DONE' | 'PHOTOS_UPLOADED' | 'SELECTION_IN_PROGRESS'
  | 'SELECTION_LOCKED' | 'ALBUM_DESIGNING' | 'PRINTING' | 'QUALITY_CHECK' | 'DISPATCHED' | 'DELIVERED'

export interface Booking {
  id: string
  bookingNumber: string
  clientId: string
  clientName: string
  clientMobile: string
  clientAvatar?: string
  shootCategory: ShootCategory
  packageName: string
  bookingDate: string
  status: BookingStatus
  orderStage: OrderStage
  totalAmount: number
  advanceAmount: number
  balanceAmount: number
  photographerName?: string
  marketingOptIn: boolean
  createdAt: string
  startTime?: string
  endTime?: string
  packageId?: string
  venue?: string
  notes?: string
  type?: string
  clientEmail?: string
  cancelledReason?: string
  rescheduledCount?: number
  orderStageLabel?: string
  expectedDeliveryDate?: string
  editorId?: string
  editorName?: string
  photographerId?: string
  courierProvider?: string
  courierTrackingNumber?: string
  deliveredAt?: string
  stageHistory?: StageHistoryEntry[]
  locationType?: 'INDOOR' | 'OUTDOOR'
  pickupStatus?: 'NOT_READY' | 'AWAITING_PICKUP' | 'PICKED_UP'
  readyForPickupAt?: string
  pickedUpAt?: string
  pickupReminderCount?: number
  lastPickupReminderAt?: string
  daysAwaitingPickup?: number
}

export interface StageHistoryEntry {
  stage: string
  stageLabel: string
  note?: string
  changedByName?: string
  createdAt: string
}

export interface SlotAvailability {
  startTime: string
  endTime: string
  available: boolean
  bookedBy?: string
  closedReason?: string
}

export interface BlockedDate {
  id: string
  date: string
  reason?: string
}

export type GalleryStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface Gallery {
  id: string
  name: string
  bookingId: string
  clientName: string
  status: GalleryStatus
  photoCount: number
  coverPhotoUrl: string
  publishedAt?: string
  viewCount: number
  downloadAllowed: boolean
  selectionLimit?: number
  watermarkEnabled?: boolean
  clientId?: string
  accessToken?: string
  openSelectionToken?: string
  /** Days after publish before unselected full-size originals are auto-deleted; null = never. */
  originalsExpiryDays?: number | null
  originalsExpiredAt?: string | null
  totalSizeBytes?: number
}

export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'REFUNDED'

export interface InvoiceLineItem {
  id: string
  description: string
  itemType?: string
  quantity: number
  unitPrice: number
  discountPercent?: number
  lineTotal: number
}

export interface Invoice {
  id: string
  invoiceNumber: string
  /** Id of the booking / studio session / outdoor shoot this invoice belongs to. */
  sourceId?: string
  sizeFormat?: 'A4' | 'A6' | 'BILLBOOK' | string
  clientAddress?: string
  clientName: string
  clientMobile?: string
  clientEmail?: string
  invoiceDate?: string
  subtotal?: number
  discountAmount?: number
  taxableAmount?: number
  cgstAmount?: number
  sgstAmount?: number
  igstAmount?: number
  totalTax?: number
  totalAmount: number
  amountPaid: number
  balanceDue: number
  status: InvoiceStatus
  dueDate: string
  notes?: string
  pdfUrl?: string
  generatedByName?: string
  taxType?: 'INTRA_STATE' | 'INTER_STATE' | 'EXEMPT'
  items?: InvoiceLineItem[]
  createdAt: string
}

export interface Client {
  id: string
  firstName: string
  lastName: string
  email: string
  mobile: string
  avatarUrl?: string
  totalBookings: number
  totalSpend: number
  marketingOptIn: boolean
  createdAt: string
  lastShootDate?: string
}

export type OutreachChannel = 'WHATSAPP' | 'SMS'
export type OutreachStatus = 'PENDING_REVIEW' | 'APPROVED_SENT' | 'EDITED_SENT' | 'DISMISSED' | 'FAILED' | 'AUTO_SENT' | 'AUTO_SEND_FAILED'
export type TriggerType = 'RELATIVE_TO_BOOKING_DATE' | 'RELATIVE_TO_DELIVERY_DATE' | 'RELATIVE_TO_ANSWER_DATE'

export interface SuggestionRule {
  id: string
  name: string
  category: ShootCategory | null
  triggerType: TriggerType
  offsetDays: number
  messageTemplate: string
  active: boolean
  autoSend: boolean
  channel: OutreachChannel
  recurring: boolean
}

export interface SuggestedOutreach {
  id: string
  ruleName: string
  clientName: string
  clientMobile: string
  sourceType: string
  suggestedMessage: string
  channel: OutreachChannel
  triggerDate: string
  occurrenceNumber: number
  status: OutreachStatus
  sentAt?: string
}

export interface CategoryRecommendationRule {
  id: string
  category: ShootCategory
  productId: string
  productName: string
  productPrice: number
  priority: number
  active: boolean
}

export interface ProductSuggestion {
  productId: string
  productName: string
  price: number
  imageUrl: string
  stockQuantity: number
}

export interface PackageUpgradeSuggestion {
  packageId: string
  packageName: string
  effectivePrice: number
  priceDifference: number
}

export interface InventoryItem {
  id: string
  name: string
  sku: string
  category: string
  quantityOnHand: number
  reorderLevel: number
  unitPrice: number
  imageUrl?: string
  /** Printable / scannable code. Falls back to the SKU when none was entered. */
  barcode?: string
  unit?: string
}

export interface CatalogPackage {
  id: string
  name: string
  description?: string
  category: string
  basePrice: number
  discountedPrice?: number
  effectivePrice: number
  durationHours?: number
  photoCount?: number
  deliveryDays?: number
  active: boolean
  archived: boolean
  sortOrder: number
  thumbnailUrl?: string
}

export interface CatalogProduct {
  id: string
  categoryId?: string
  categoryName?: string
  name: string
  description?: string
  sku?: string
  price: number
  gstRate?: number
  stockQuantity: number
  active: boolean
  imageUrl?: string
}

export interface CatalogCategory {
  id: string
  name: string
  description?: string
  active: boolean
}

export type NotificationType =
  | 'BOOKING_CREATED' | 'BOOKING_CANCELLED' | 'PAYMENT_RECEIVED' | 'LOW_STOCK' | 'OUT_OF_STOCK'
  | 'LEAVE_REQUESTED' | 'LEAVE_APPROVED' | 'LEAVE_REJECTED' | 'GALLERY_PUBLISHED'
  | 'SUGGESTIONS_PENDING' | 'SUGGESTIONS_AUTO_SENT'

export interface AppNotification {
  id: string
  type: NotificationType
  title: string
  message: string
  read: boolean
  entityType?: string
  entityId?: string
  createdAt: string
}

export interface DashboardStats {
  bookingsToday: number
  bookingsThisWeek: number
  revenueThisMonth: number
  revenueLastMonth: number
  pendingSuggestions: number
  lowStockItems: number
  activeGalleries: number
  upcomingShoots: Booking[]
  revenueTrend: { month: string; revenue: number }[]
  bookingsByCategory: { category: string; count: number }[]
}

// ---------------- Staff & Attendance ----------------

export interface StaffMember {
  id: string
  userId: string
  firstName: string
  lastName: string
  email: string
  mobile: string
  profilePhotoUrl?: string
  roles: Role[]
  employeeId: string
  department: string
  designation: string
  joiningDate: string
  availableForBooking: boolean
  maxBookingsPerDay: number
  assignedTasksCount: number
  completedTasksCount: number
  userStatus?: string
}

export type PunchDirection = 'IN' | 'OUT' | 'UNKNOWN'
export type AttendanceSource = 'MANUAL' | 'CSV_IMPORT' | 'GENERIC_WEBHOOK' | 'ZK_PROTOCOL'

export interface AttendanceRecord {
  id: string
  employeeCode: string
  staffId: string | null
  staffName: string | null
  punchTime: string
  direction: PunchDirection
  source: AttendanceSource
  deviceSerial?: string
}

export type DayAttendanceStatus = 'PRESENT' | 'LATE' | 'HALF_DAY' | 'ABSENT' | 'ON_LEAVE' | 'WEEKLY_OFF' | 'HOLIDAY' | 'FUTURE'

export interface DaySummary {
  staffId: string
  staffName: string
  firstPunch: string | null
  lastPunch: string | null
  punchCount: number
  workedMinutes?: number
  lateMinutes?: number
  overtimeMinutes?: number
}

export interface DayStatus {
  date: string
  status: DayAttendanceStatus
  firstPunch: string | null
  lastPunch: string | null
  punchCount: number
  workedMinutes?: number
  lateMinutes?: number
  earlyLeaveMinutes?: number
  overtimeMinutes?: number
  missingOut?: boolean
  holidayName?: string | null
}

export interface MonthlyReport {
  staffId: string
  staffName: string
  year: number
  month: number
  days: DayStatus[]
  presentDays: number
  lateDays: number
  absentDays: number
  leaveDays: number
  weeklyOffDays: number
  halfDays?: number
  holidayDays?: number
  workingDays?: number
  workedMinutes?: number
  lateMinutes?: number
  overtimeMinutes?: number
  shiftName?: string | null
}

export interface WorkHoursConfig {
  shiftStart: string
  graceMinutes: number
  weeklyOffDay: string
  shiftEnd?: string
  halfDayMinutes?: number
  minOvertimeMinutes?: number
  latesPerHalfDay?: number
}

export interface WorkShift {
  id: string
  name: string
  startTime: string
  endTime: string
  graceMinutes: number
  active: boolean
  staffCount: number
}

export interface StaffScheduleRow {
  staffId: string
  staffName: string
  employeeId?: string
  department?: string
  shiftId: string | null
  shiftName: string | null
  effectiveStart: string
  effectiveEnd: string
  weeklyOffDays: string[]
  usesDefaultShift: boolean
  monthlySalary: number | null
  overtimeRatePerHour: number | null
}

export interface AttendanceHolidayRow { id: string; date: string; name: string }

export interface PayrollRow {
  staffId: string
  staffName: string
  employeeId?: string
  department?: string
  workingDays: number
  presentDays: number
  lateDays: number
  halfDays: number
  absentDays: number
  leaveDays: number
  weeklyOffDays: number
  holidayDays: number
  workedHours: number
  overtimeHours: number
  unpaidDays: number
  latePenaltyDays: number
  monthlySalary: number | null
  perDayRate: number | null
  deduction: number | null
  overtimePay: number | null
  netPay: number | null
}

export interface PayrollReport {
  year: number
  month: number
  monthInProgress: boolean
  rows: PayrollRow[]
  totalNetPay: number
}

export interface WebhookConfig {
  secret: string
  genericWebhookUrl: string
  iclockBaseUrl: string
}

export interface UnmatchedCode {
  employeeCode: string
  punchCount: number
  lastSeen: string
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface StaffLeaveRequest {
  id: string
  staffId: string
  staffName: string
  fromDate: string
  toDate: string
  dayCount: number
  reason: string
  status: LeaveStatus
  reviewedByName?: string
  reviewedAt?: string
  reviewNotes?: string
  createdAt: string
}

