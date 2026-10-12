import { describe, expect, it } from 'vitest'
import { parseFaq, slugify, whatsappLink } from './publicContent'

describe('parseFaq', () => {
  it('returns nothing for empty content', () => {
    expect(parseFaq(undefined)).toEqual([])
    expect(parseFaq('')).toEqual([])
  })

  it('treats the first line as the question and the rest as the answer', () => {
    const out = parseFaq('How long does delivery take?\nUsually about a week.\nRush options exist.')
    expect(out).toEqual([{ q: 'How long does delivery take?', a: 'Usually about a week. Rush options exist.' }])
  })

  it('strips Q:/A: markers and splits on blank lines', () => {
    const out = parseFaq('Q: Do you travel?\nA: Yes.\n\nQ: Is parking free?\nA: Mostly.')
    expect(out.map((i) => i.q)).toEqual(['Do you travel?', 'Is parking free?'])
    expect(out.map((i) => i.a)).toEqual(['Yes.', 'Mostly.'])
  })

  it('drops blocks that have no answer', () => {
    expect(parseFaq('Just a heading\n\nReal question?\nReal answer.')).toHaveLength(1)
  })
})

describe('slugify', () => {
  it('makes url-safe slugs', () => {
    expect(slugify('Weddings & pre-wedding')).toBe('weddings-and-pre-wedding')
    expect(slugify('  On-location / events  ')).toBe('on-location-events')
  })
})

describe('whatsappLink', () => {
  it('returns null for missing or too-short numbers', () => {
    expect(whatsappLink(undefined)).toBeNull()
    expect(whatsappLink('123')).toBeNull()
  })

  it('adds the India country code to 10-digit numbers', () => {
    expect(whatsappLink('98765 43210')).toBe('https://wa.me/919876543210')
  })

  it('keeps numbers that already carry a country code and encodes the message', () => {
    expect(whatsappLink('+91 98765 43210', 'Hi there')).toBe('https://wa.me/919876543210?text=Hi%20there')
  })
})
