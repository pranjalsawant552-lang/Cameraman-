export const BOOKING_STATUS_TONE: Record<string, string> = {
  PENDING: 'warning', CONFIRMED: 'accent', IN_PROGRESS: 'info', COMPLETED: 'success', CANCELLED: 'danger', RESCHEDULED: 'info', NO_SHOW: 'danger',
}
export const GALLERY_STATUS_TONE: Record<string, string> = {
  DRAFT: 'neutral', PUBLISHED: 'success', ARCHIVED: 'neutral',
}
export const INVOICE_STATUS_TONE: Record<string, string> = {
  DRAFT: 'neutral', SENT: 'info', PARTIALLY_PAID: 'warning', PAID: 'success', OVERDUE: 'danger', CANCELLED: 'neutral', REFUNDED: 'info',
}
export const OUTREACH_STATUS_TONE: Record<string, string> = {
  PENDING_REVIEW: 'warning', APPROVED_SENT: 'success', EDITED_SENT: 'success',
  DISMISSED: 'neutral', FAILED: 'danger', AUTO_SENT: 'success', AUTO_SEND_FAILED: 'danger',
}
export const CATEGORY_TONE: Record<string, string> = {
  WEDDING: 'accent', PRE_WEDDING: 'accent', MATERNITY: 'info', NEWBORN: 'success',
  BIRTHDAY: 'warning', FAMILY: 'neutral', CORPORATE: 'neutral', EVENT: 'neutral', PRODUCT: 'neutral', CASUAL: 'neutral', PORTFOLIO: 'neutral', OTHER: 'neutral',
}
export const STAGE_LABEL: Record<string, string> = {
  BOOKING_CONFIRMED: 'Booking confirmed', ADVANCE_PAID: 'Advance paid', SHOOT_DONE: 'Shoot done',
  PHOTOS_UPLOADED: 'Photos uploaded', SELECTION_IN_PROGRESS: 'Selection in progress', SELECTION_LOCKED: 'Selection locked',
  ALBUM_DESIGNING: 'Album designing', PRINTING: 'Printing', QUALITY_CHECK: 'Quality check',
  DISPATCHED: 'Dispatched', DELIVERED: 'Delivered',
}
export const ORDER_STAGES = [
  'BOOKING_CONFIRMED', 'ADVANCE_PAID', 'SHOOT_DONE', 'PHOTOS_UPLOADED', 'SELECTION_IN_PROGRESS',
  'SELECTION_LOCKED', 'ALBUM_DESIGNING', 'PRINTING', 'QUALITY_CHECK', 'DISPATCHED', 'DELIVERED',
] as const
export const DAY_ATTENDANCE_TONE: Record<string, string> = {
  PRESENT: 'success', LATE: 'warning', HALF_DAY: 'warning', ABSENT: 'danger', ON_LEAVE: 'info', WEEKLY_OFF: 'neutral', HOLIDAY: 'info', FUTURE: 'neutral',
}
export const STAFF_LEAVE_STATUS_TONE: Record<string, string> = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'neutral',
}
