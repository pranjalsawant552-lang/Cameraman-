import type { Invoice } from './types'
import type { StudioSettings } from './api'
import { invoiceHtml, LAYOUTS, type InvoiceLayout } from './invoiceHtml'
import { printHtml } from './barcode'

/** Print through the browser: Marathi text is shaped by the browser's own engine, so conjuncts render correctly. */
export function printInvoice(inv: Invoice, studio: StudioSettings | null, layout: InvoiceLayout): void {
  printHtml(invoiceHtml(inv, studio, layout))
}

/** Renders the invoice off-screen, captures it, and saves a real PDF file (no print dialog, no server round-trip). */
export async function downloadInvoiceFile(inv: Invoice, studio: StudioSettings | null, layout: InvoiceLayout): Promise<void> {
  const L = LAYOUTS[layout]
  const frame = document.createElement('iframe')
  frame.style.cssText = `position:fixed;left:-10000px;top:0;width:${L.wPx}px;height:${L.hPx}px;border:0`
  document.body.appendChild(frame)
  try {
    const doc = frame.contentDocument!
    doc.open(); doc.write(invoiceHtml(inv, studio, layout)); doc.close()
    await new Promise<void>((res) => { if (doc.readyState === 'complete') res(); else frame.onload = () => res() })
    await doc.fonts?.ready
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([import('html2canvas'), import('jspdf')])
    const height = Math.max(L.hPx, doc.body.scrollHeight)
    const canvas = await html2canvas(doc.body, { scale: 2, backgroundColor: '#ffffff', width: L.wPx, height, windowWidth: L.wPx })
    const pdf = new jsPDF({ unit: 'mm', format: [L.wMm, L.hMm], orientation: 'portrait' })
    const pageH = (canvas.height / canvas.width) * L.wMm
    // Content taller than one page (long A4 invoices) is sliced across pages.
    for (let y = 0, page = 0; y < pageH - 0.5; y += L.hMm, page++) {
      if (page > 0) pdf.addPage([L.wMm, L.hMm])
      pdf.addImage(canvas.toDataURL('image/jpeg', 0.95), 'JPEG', 0, -y, L.wMm, pageH)
    }
    pdf.save(`${inv.invoiceNumber}.pdf`)
  } finally {
    frame.remove()
  }
}
