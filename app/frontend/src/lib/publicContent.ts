import type { PublicService } from '@/lib/api'

/** Fallback "What we shoot" list, used until the studio saves its own in Settings. */
export const DEFAULT_SERVICES: PublicService[] = [
  { icon: 'camera', title: 'Studio portraits', desc: 'Backdrops, controlled lighting and props for portraits, passport photos and product shoots — book a room and slot in minutes.' },
  { icon: 'heart', title: 'Weddings & pre-wedding', desc: 'Full-day coverage, on location, with a second shooter and same-week preview gallery.' },
  { icon: 'baby', title: 'Maternity & newborn', desc: 'Gentle, unhurried sessions in the studio, timed around your due date.' },
  { icon: 'location', title: 'On-location & events', desc: 'Birthdays, corporate events and destination shoots — we travel, weather-permitting plans included.' },
  { icon: 'building', title: 'Corporate', desc: 'Headshots, office documentation and on-site event coverage for teams of any size.' },
  { icon: 'users', title: 'Family sessions', desc: 'Multi-generation portraits in the studio or a park setting near you.' },
]

export interface ProcessStep { title: string; desc: string }

/** The real journey a client takes through the portal, start to finish. */
export const PROCESS_STEPS: ProcessStep[] = [
  { title: 'Create your account', desc: 'Sign up in a minute and your bookings, galleries and invoices all live in one private portal.' },
  { title: 'Pick a package & slot', desc: 'Choose a package, see which time slots are free, and request your date online — no phone tag.' },
  { title: 'We shoot', desc: 'Show up, relax, and let the team guide you. Studio room or on location, we plan it with you beforehand.' },
  { title: 'Review & select', desc: 'Your gallery appears in the portal. Browse it, favourite the frames you love and send us your selection.' },
]

export interface FaqItem { q: string; a: string; cat?: string }

/**
 * Starter answers that describe how the booking portal works. They deliberately avoid
 * prices, deadlines and policies — the studio can replace the whole list by saving a CMS
 * page with the key `faq` (see parseFaq) so the answers always match its real terms.
 */
export const DEFAULT_FAQ: FaqItem[] = [
  { cat: 'Booking', q: 'How do I book a session?', a: 'Create a free account, open "Book a shoot" in your portal, choose a package and pick an available date and time. We confirm your request and you can follow its status from the same portal.' },
  { cat: 'Booking', q: 'Can I see which dates and time slots are free?', a: 'Yes. When you book online the calendar shows open slots for the day you choose, so you can pick a time that works without waiting for a call back.' },
  { cat: 'Booking', q: 'I need to change my date — what do I do?', a: 'Open your booking in the portal to see its details. If you need a different date, get in touch through the contact page and we will help you reschedule.' },
  { cat: 'Shoot day', q: 'Do you shoot on location as well as in the studio?', a: 'Both. We run studio sessions and travel for weddings, events and outdoor shoots. Tell us what you have in mind and we will recommend the right setup.' },
  { cat: 'Shoot day', q: 'What should I bring or wear?', a: 'Comfortable outfits you feel like yourself in, plus any props or keepsakes that matter to you. We talk through ideas beforehand so there are no surprises on the day.' },
  { cat: 'Photos & delivery', q: 'How will I receive my photos?', a: 'Your gallery appears in your client portal. You can browse it online, mark the images you love and send your selection straight to the studio.' },
  { cat: 'Photos & delivery', q: 'Can I choose which photos get edited?', a: 'Yes — that is what the selection step is for. Favourite the frames you want and the team takes it from there.' },
  { cat: 'Payments', q: 'How do payments and invoices work?', a: 'Invoices for your bookings show up in your portal, where you can review them and pay online. Your package and invoice spell out exactly what is included.' },
  { cat: 'Payments', q: 'Where can I find package details and prices?', a: 'The Packages page lists what is currently on offer. If you have something custom in mind, send us a message and we will put together a quote.' },
]

/**
 * Turns the text of a CMS page into FAQ entries. Blocks are separated by a blank line; the
 * first line of a block is the question and the remaining lines are the answer. Optional
 * "Q:" / "A:" prefixes are stripped, so both of these work:
 *
 *   How long does delivery take?
 *   Usually a week.
 *
 *   Q: Do you travel?
 *   A: Yes, within reason.
 */
export function parseFaq(content?: string): FaqItem[] {
  if (!content) return []
  return content
    .split(/\n\s*\n/)
    .map((block) => block.split('\n').map((l) => l.trim()).filter(Boolean))
    .map((lines) => {
      if (lines.length < 2) return null
      const q = lines[0].replace(/^q\s*[:.)-]\s*/i, '').trim()
      const a = lines.slice(1).join(' ').replace(/^a\s*[:.)-]\s*/i, '').trim()
      return q && a ? ({ q, a } as FaqItem) : null
    })
    .filter((x): x is FaqItem => x !== null)
}

export function slugify(s: string): string {
  return s.toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

/** Normalises a phone number for a wa.me link. 10-digit Indian numbers get the +91 prefix. */
export function whatsappLink(raw?: string, text?: string): string | null {
  if (!raw) return null
  const digits = raw.replace(/\D/g, '')
  if (digits.length < 10) return null
  const full = digits.length === 10 ? `91${digits}` : digits
  return `https://wa.me/${full}${text ? `?text=${encodeURIComponent(text)}` : ''}`
}
