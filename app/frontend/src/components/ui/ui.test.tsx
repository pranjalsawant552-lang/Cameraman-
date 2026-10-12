import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Input, Select, Textarea, SearchInput } from './Input'
import { Modal } from './Modal'
import { Drawer } from './Drawer'

describe('form fields', () => {
  it('associate their label with the control even when no id is passed', () => {
    render(<><Input label="Email" /><Select label="Role"><option>a</option></Select><Textarea label="Notes" /></>)
    expect(screen.getByLabelText('Email')).toBeInstanceOf(HTMLInputElement)
    expect(screen.getByLabelText('Role')).toBeInstanceOf(HTMLSelectElement)
    expect(screen.getByLabelText('Notes')).toBeInstanceOf(HTMLTextAreaElement)
  })
  it('two fields on one page do not share an id', () => {
    render(<><Input label="One" /><Input label="Two" /></>)
    expect(screen.getByLabelText('One').id).not.toBe(screen.getByLabelText('Two').id)
  })
  it('keeps an explicit id', () => {
    render(<Input label="Name" id="custom" />)
    expect(screen.getByLabelText('Name').id).toBe('custom')
  })
  it('SearchInput is labelled from its placeholder', () => {
    render(<SearchInput value="" onChange={() => undefined} placeholder="Search clients…" />)
    expect(screen.getByRole('searchbox', { name: 'Search clients' })).toBeInTheDocument()
  })
})

describe('Modal', () => {
  it('is a labelled dialog and closes on Escape', async () => {
    const onClose = vi.fn()
    render(<Modal open onClose={onClose} title="Edit thing"><button>Inside</button></Modal>)
    expect(screen.getByRole('dialog', { name: 'Edit thing' })).toHaveAttribute('aria-modal', 'true')
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })
  it('renders nothing when closed', () => {
    render(<Modal open={false} onClose={() => undefined} title="Hidden"><p>x</p></Modal>)
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('Drawer', () => {
  it('is a labelled dialog, closes with its button and with Escape', async () => {
    const onClose = vi.fn()
    render(<Drawer open onClose={onClose} title="Client" subtitle="Since 2024"><p>body</p></Drawer>)
    expect(screen.getByRole('dialog', { name: 'Client' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Close panel' }))
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(2)
  })
  it('locks page scroll while open and restores it', () => {
    const { unmount } = render(<Drawer open onClose={() => undefined} title="T"><p>x</p></Drawer>)
    expect(document.body.style.overflow).toBe('hidden')
    unmount()
    expect(document.body.style.overflow).not.toBe('hidden')
  })
})
