import JsBarcode from 'jsbarcode'

/** Code 128 barcode as an SVG string (scans with any normal USB/phone scanner). */
export function barcodeSvg(code: string, opts: { height?: number; width?: number; displayValue?: boolean } = {}): string {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  JsBarcode(svg, code, {
    format: 'CODE128', height: opts.height ?? 40, width: opts.width ?? 1.6, margin: 0,
    displayValue: opts.displayValue ?? true, fontSize: 12,
  })
  return svg.outerHTML
}

export interface LabelItem { name: string; code: string; price?: string }

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))

export function labelsHtml(items: LabelItem[]): string {
  const cells = items.map((i) => `<div class="l"><div class="n">${esc(i.name)}</div>${barcodeSvg(i.code)}${i.price ? `<div class="p">${esc(i.price)}</div>` : ''}</div>`).join('')
  return `<!doctype html><html><head><meta charset="utf-8"><title>Barcode labels</title><style>
@page{size:A4;margin:8mm}body{margin:0;font-family:Arial,sans-serif}
.g{display:grid;grid-template-columns:repeat(3,1fr);gap:4mm}
.l{border:1px dashed #999;padding:3mm;text-align:center;break-inside:avoid;overflow:hidden}
.n{font-size:11px;font-weight:700;margin-bottom:2mm;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.p{font-size:11px;margin-top:1mm}svg{max-width:100%;height:auto}
</style></head><body><div class="g">${cells}</div></body></html>`
}

/** Prints labels through a hidden iframe so the app page itself is untouched. */
export function printLabels(items: LabelItem[]): void {
  printHtml(labelsHtml(items))
}

export function printHtml(html: string): void {
  const frame = document.createElement('iframe')
  frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0'
  document.body.appendChild(frame)
  const doc = frame.contentDocument!
  doc.open(); doc.write(html); doc.close()
  const go = () => { try { frame.contentWindow?.focus(); frame.contentWindow?.print() } finally { window.setTimeout(() => frame.remove(), 60_000) } }
  if (doc.readyState === 'complete') window.setTimeout(go, 100); else frame.onload = go
}
