// Thin wrapper around Razorpay's hosted Checkout.js widget. This is the
// standard client-side integration Razorpay documents: load their script
// once, then call `new window.Razorpay(options).open()`. There is no npm
// package for this — Razorpay's own docs have you load the script directly.

interface RazorpayInstance {
  open: () => void
  on: (event: string, handler: (response: unknown) => void) => void
}

interface RazorpayOptions {
  key: string
  amount: number // paise
  currency: string
  name: string
  description?: string
  order_id: string
  prefill?: { name?: string; email?: string; contact?: string }
  theme?: { color?: string }
  handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => void
  modal?: { ondismiss?: () => void }
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance
  }
}

let loadPromise: Promise<void> | null = null

function loadRazorpayScript(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  if (loadPromise) return loadPromise
  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve()
    script.onerror = () => { loadPromise = null; reject(new Error('Could not load the payment widget. Check your connection and try again.')) }
    document.body.appendChild(script)
  })
  return loadPromise
}

export interface RazorpayCheckoutInput {
  keyId: string
  orderId: string
  amountPaise: number
  currency: string
  studioName: string
  description?: string
  prefillName?: string
  prefillEmail?: string
  prefillContact?: string
}

/** Opens the Razorpay checkout modal and resolves with the payment
 *  response once the user completes payment, or rejects if they close the
 *  modal or the script fails to load. Does not itself verify the payment —
 *  the caller still needs to POST the response to /payments/razorpay/verify,
 *  since that's the step that actually confirms the signature server-side. */
export async function openRazorpayCheckout(input: RazorpayCheckoutInput): Promise<{ razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }> {
  await loadRazorpayScript()
  if (!window.Razorpay) throw new Error('Payment widget failed to load.')

  return new Promise((resolve, reject) => {
    const rzp = new window.Razorpay!({
      key: input.keyId,
      amount: input.amountPaise,
      currency: input.currency,
      name: input.studioName,
      description: input.description,
      order_id: input.orderId,
      prefill: { name: input.prefillName, email: input.prefillEmail, contact: input.prefillContact },
      theme: { color: '#111111' },
      handler: (response) => resolve(response as { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }),
      modal: { ondismiss: () => reject(new Error('Payment cancelled.')) },
    })
    rzp.open()
  })
}
