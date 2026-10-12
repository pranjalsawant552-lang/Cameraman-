import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

const fetchInquiryReplies = vi.fn()
const replyToInquiry = vi.fn()
vi.mock('@/lib/api', () => ({
  fetchInquiryReplies: (...a: unknown[]) => fetchInquiryReplies(...a),
  replyToInquiry: (...a: unknown[]) => replyToInquiry(...a),
}))

import { InquiryRepliesModal } from './InquiryRepliesModal'

const inquiry = { id: 'i1', name: 'Meera', email: 'meera@example.com', message: 'Do you shoot weddings?', status: 'NEW' as const, createdAt: '2025-03-01T10:00:00' }

describe('InquiryRepliesModal', () => {
  beforeEach(() => { fetchInquiryReplies.mockReset().mockResolvedValue([]); replyToInquiry.mockReset() })

  it('shows the original message and an empty log', async () => {
    render(<InquiryRepliesModal inquiry={inquiry} onClose={() => undefined} onUpdated={() => undefined} />)
    expect(screen.getByText('Do you shoot weddings?')).toBeInTheDocument()
    expect(await screen.findByText('No follow-up yet.')).toBeInTheDocument()
  })

  it('sends an email reply and reports the updated inquiry', async () => {
    replyToInquiry.mockResolvedValue({ ...inquiry, status: 'CONTACTED', replyCount: 1 })
    const onUpdated = vi.fn()
    render(<InquiryRepliesModal inquiry={inquiry} onClose={() => undefined} onUpdated={onUpdated} />)
    await userEvent.type(screen.getByLabelText('Email to send'), 'Yes we do!')
    await userEvent.click(screen.getByRole('button', { name: /send & log/i }))
    await waitFor(() => expect(replyToInquiry).toHaveBeenCalledWith('i1', { channel: 'EMAIL', message: 'Yes we do!', sendEmail: true }))
    expect(onUpdated).toHaveBeenCalledWith(expect.objectContaining({ status: 'CONTACTED' }))
  })

  it('logs a phone call without sending any email', async () => {
    replyToInquiry.mockResolvedValue({ ...inquiry, replyCount: 1 })
    render(<InquiryRepliesModal inquiry={inquiry} onClose={() => undefined} onUpdated={() => undefined} />)
    await userEvent.selectOptions(screen.getByLabelText('How did you reply?'), 'PHONE')
    await userEvent.type(screen.getByLabelText('What was said'), 'Called, quoted package B')
    await userEvent.click(screen.getByRole('button', { name: /log follow-up/i }))
    await waitFor(() => expect(replyToInquiry).toHaveBeenCalledWith('i1', { channel: 'PHONE', message: 'Called, quoted package B', sendEmail: false }))
  })

  it('does not allow an empty reply', async () => {
    render(<InquiryRepliesModal inquiry={inquiry} onClose={() => undefined} onUpdated={() => undefined} />)
    expect(screen.getByRole('button', { name: /send & log/i })).toBeDisabled()
  })
})
