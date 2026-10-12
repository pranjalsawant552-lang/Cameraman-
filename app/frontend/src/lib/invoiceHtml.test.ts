import { describe, expect, it } from 'vitest'
import { invoiceHtml } from './invoiceHtml'
import { barcodeSvg } from './barcode'
import { nextStage, prevStage } from '@/components/booking/BookingStatusPanel'
import type { Invoice } from './types'

const inv = {
  id: '1', invoiceNumber: '8763', clientName: 'Ramesh <Patil>', invoiceDate: '2026-10-11', dueDate: '2026-10-11',
  totalAmount: 5000, amountPaid: 2000, balanceDue: 3000, status: 'SENT', createdAt: '2026-10-11',
  items: [{ id: 'a', description: 'Pre-wedding shoot', quantity: 1, unitPrice: 5000, lineTotal: 5000 }],
} as unknown as Invoice

describe('invoice layouts', () => {
  it.each(['BILLBOOK', 'A4'] as const)('%s shows the bill number, escaped client name and balance', (layout) => {
    const html = invoiceHtml(inv, null, layout)
    expect(html).toContain('8763')
    expect(html).toContain('Ramesh &lt;Patil&gt;')
    expect(html).toContain('3,000')
    expect(html).toContain('कॅमेरामन फोटो स्टुडिओ')
  })
  it('bill book uses the A5 slip page size', () => {
    expect(invoiceHtml(inv, null, 'BILLBOOK')).toContain('size:148mm 210mm')
    expect(invoiceHtml(inv, null, 'A4')).toContain('size:210mm 297mm')
  })
})

describe('barcode + stage helpers', () => {
  it('draws a Code 128 svg (jsdom has no canvas, so no text label here)', () => { expect(barcodeSvg('PRD-ABC12345', { displayValue: false })).toContain('<svg') })
  it('walks the order stages', () => {
    expect(nextStage('BOOKING_CONFIRMED')).toBe('ADVANCE_PAID')
    expect(nextStage('DELIVERED')).toBeNull()
    expect(prevStage('BOOKING_CONFIRMED')).toBeNull()
  })
})
