import type { Invoice } from './types'
import type { StudioSettings } from './api'

export type InvoiceLayout = 'BILLBOOK' | 'A4'

/** Page size in CSS px (96 dpi) and in mm, per layout. BILLBOOK is the A5 portrait slip of the studio's printed bill book. */
export const LAYOUTS: Record<InvoiceLayout, { label: string; wPx: number; hPx: number; wMm: number; hMm: number }> = {
  BILLBOOK: { label: 'Bill book (A5)', wPx: 559, hPx: 794, wMm: 148, hMm: 210 },
  A4: { label: 'A4', wPx: 794, hPx: 1123, wMm: 210, hMm: 297 },
}

// Defaults taken from the studio's printed bill book, used when Settings has no value.
const DEFAULTS = {
  name: 'कॅमेरामन फोटो स्टुडिओ',
  tagline: 'आमच्याकडे कमर्शियल, इंडस्ट्रियल फोटो, लग्न, साखरपुडा, वाढदिवस, कॉलेज फंक्शनची व सर्व प्रकारच्या समारंभाच्या फोटो व व्हिडिओ शुटिंगची कामे काळजीपूर्वक केली जातील.',
  owner: 'आकाश पवार',
  phone: '8600203188 | 8083327327',
  address: 'शॉप नं. १, घुलेनगर, स्वागत हॉटेल शेजारी, मांजरी-मुंढवा रोड, मांजरी बु., पुणे',
}

const esc = (v: unknown) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))
const money = (n: number | undefined) => new Intl.NumberFormat('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(n ?? 0)
const dmy = (d?: string) => {
  if (!d) return ''
  const x = new Date(d)
  return Number.isNaN(x.getTime()) ? '' : `${String(x.getDate()).padStart(2, '0')}/${String(x.getMonth() + 1).padStart(2, '0')}/${x.getFullYear()}`
}
const FONT = `'Noto Sans Devanagari','Nirmala UI','Mangal','Kohinoor Devanagari','Lohit Devanagari',Arial,sans-serif`

function head(studio: StudioSettings | null) {
  const s = studio ?? {}
  return {
    name: s.studioName || DEFAULTS.name,
    tagline: s.tagline || DEFAULTS.tagline,
    owner: s.ownerName || DEFAULTS.owner,
    phone: s.phone || DEFAULTS.phone,
    address: s.address || DEFAULTS.address,
    gst: s.gstNumber,
  }
}

/** Complete HTML document for one invoice. Used for both printing and PDF capture, so what you print is what you download. */
export function invoiceHtml(inv: Invoice, studio: StudioSettings | null, layout: InvoiceLayout): string {
  return billBook(inv, studio, layout)
}

function billBook(inv: Invoice, studio: StudioSettings | null, layout: InvoiceLayout): string {
  const h = head(studio)
  const L = LAYOUTS[layout]
  const k = L.wPx / 559 // the A4 sheet is the bill-book slip scaled up, so both look identical
  const f = (n: number) => `${Math.round(n * k * 10) / 10}px`
  const items = inv.items ?? []
  const rows = items.map((it, i) => `<tr><td class="c">${i + 1}</td><td>${esc(it.description)}</td><td class="c">${it.quantity}</td><td class="r">${money(it.lineTotal)}</td></tr>`).join('')
  const filler = Array.from({ length: Math.max(0, 9 - items.length) }, () => '<tr class="e"><td></td><td></td><td></td><td></td></tr>').join('')
  const taxRow = (inv.totalTax ?? 0) > 0 ? `<div class="t"><span>GST</span><b>${money(inv.totalTax)}</b></div>` : ''
  return `<!doctype html><html lang="mr"><head><meta charset="utf-8"><title>${esc(inv.invoiceNumber)}</title><style>
@page{size:${L.wMm}mm ${L.hMm}mm;margin:0}*{box-sizing:border-box}
html,body{margin:0;background:#fff}body{width:${L.wPx}px;height:${L.hPx}px;padding:${f(20)};font-family:${FONT};color:#000;font-size:${f(12)}}
.box{border:${f(1.5)} solid #000;height:100%;display:flex;flex-direction:column}
.top{text-align:center;padding:${f(6)} ${f(8)};border-bottom:${f(1.5)} solid #000}
.top h1{margin:0;font-size:${f(24)};line-height:1.2}.top p{margin:${f(3)} 0 0;font-size:${f(11)};line-height:1.35}
.own{text-align:center;font-weight:700;font-size:${f(14)};padding:${f(4)};border-bottom:${f(1.5)} solid #000}
.adr{text-align:center;font-size:${f(11)};padding:${f(4)};border-bottom:${f(1.5)} solid #000}
.meta{display:flex;border-bottom:${f(1.5)} solid #000}.meta .l{flex:1;padding:${f(6)} ${f(8)};border-right:${f(1.5)} solid #000}.meta .rt{width:${f(170)};padding:${f(6)} ${f(8)}}
.meta div div{margin:${f(3)} 0}.v{font-size:${f(14)};font-weight:700}
table{width:100%;border-collapse:collapse;flex:1}th,td{border-left:${f(1.5)} solid #000;padding:${f(4)} ${f(6)};vertical-align:top;font-size:${f(12)}}
th{border-bottom:${f(1.5)} solid #000;text-align:center;font-weight:700}td:first-child,th:first-child{border-left:0;width:${f(36)}}
th:nth-child(3),td:nth-child(3){width:${f(48)}}th:nth-child(4),td:nth-child(4){width:${f(90)}}
tr.e td{height:${f(24)}}.c{text-align:center}.r{text-align:right}
.tot{border-top:${f(1.5)} solid #000;display:flex;justify-content:flex-end}.tot .b{width:${f(230)};border-left:${f(1.5)} solid #000}
.t{display:flex;justify-content:space-between;padding:${f(4)} ${f(8)};border-bottom:${f(1.5)} solid #000}.t:last-child{border-bottom:0}
.note{border-top:${f(1.5)} solid #000;padding:${f(5)} ${f(8)};font-size:${f(11)};line-height:1.5}
.sig{display:flex;justify-content:space-between;align-items:flex-end;padding:${f(4)} ${f(8)} ${f(6)};font-size:${f(11)}}.sig b{font-size:${f(12)}}
</style></head><body><div class="box">
<div class="top"><h1>${esc(h.name)}</h1><p>${esc(h.tagline)}</p></div>
<div class="own">${esc(h.owner)} – ${esc(h.phone)}</div>
<div class="adr">पत्ता : ${esc(h.address)}${h.gst ? `<br>GSTIN: ${esc(h.gst)}` : ''}</div>
<div class="meta"><div class="l"><div>नाव :</div><div class="v">${esc(inv.clientName)}</div>${inv.clientMobile ? `<div>मो. ${esc(inv.clientMobile)}</div>` : ''}</div>
<div class="rt"><div>बिल नं. : <span class="v">${esc(inv.invoiceNumber)}</span></div><div>दिनांक : <span class="v">${dmy(inv.invoiceDate)}</span></div></div></div>
<table><thead><tr><th>अ.क्र.</th><th>तपशील</th><th>नग</th><th>रक्कम</th></tr></thead><tbody>${rows}${filler}</tbody></table>
<div class="tot"><div class="b">${taxRow}<div class="t"><span>एकूण</span><b>₹ ${money(inv.totalAmount)}</b></div><div class="t"><span>जमा</span><b>₹ ${money(inv.amountPaid)}</b></div><div class="t"><span>बाकी</span><b>₹ ${money(inv.balanceDue)}</b></div></div></div>
<div class="note">★ कृपया अगोदर अ‍ॅडव्हान्स द्यावा लागेल.<br>★ कृपया फोटो १ महिन्याच्या आत घेऊन जावेत अन्यथा आम्ही जबाबदार नाही.</div>
<div class="sig"><span>Cameraman Photo Studio</span><b>${esc(h.name)} करिता</b></div>
</div></body></html>`
}
