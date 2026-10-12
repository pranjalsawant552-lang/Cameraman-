import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PayrollReport } from '@/lib/types'

const fetchPayroll = vi.fn()
const downloadPayrollCsv = vi.fn()
vi.mock('@/lib/api', () => ({ fetchPayroll: (...a: unknown[]) => fetchPayroll(...a), downloadPayrollCsv: (...a: unknown[]) => downloadPayrollCsv(...a) }))

import { PayrollPanel } from './PayrollPanel'

const report = (over: Partial<PayrollReport> = {}): PayrollReport => ({
  year: 2025, month: 3, monthInProgress: false, totalNetPay: 23000,
  rows: [{
    staffId: 's1', staffName: 'Anita Rao', employeeId: 'E1', workingDays: 26, presentDays: 23, lateDays: 1, halfDays: 2,
    absentDays: 1, leaveDays: 0, weeklyOffDays: 4, holidayDays: 0, workedHours: 180, overtimeHours: 0, unpaidDays: 2,
    latePenaltyDays: 0, monthlySalary: 26000, perDayRate: 1000, deduction: 2000, overtimePay: 0, netPay: 24000,
  }, {
    staffId: 's2', staffName: 'No Salary', workingDays: 26, presentDays: 26, lateDays: 0, halfDays: 0, absentDays: 0, leaveDays: 0,
    weeklyOffDays: 4, holidayDays: 0, workedHours: 200, overtimeHours: 0, unpaidDays: 0, latePenaltyDays: 0,
    monthlySalary: null, perDayRate: null, deduction: null, overtimePay: 0, netPay: null,
  }],
  ...over,
})

describe('PayrollPanel', () => {
  beforeEach(() => { fetchPayroll.mockReset(); downloadPayrollCsv.mockReset() })

  it('shows each staff member and flags a missing salary', async () => {
    fetchPayroll.mockResolvedValue(report())
    render(<PayrollPanel />)
    expect(await screen.findByText('Anita Rao')).toBeInTheDocument()
    expect(screen.getByText('No salary set')).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('warns when the month is still in progress', async () => {
    fetchPayroll.mockResolvedValue(report({ monthInProgress: true }))
    render(<PayrollPanel />)
    expect(await screen.findByRole('status')).toHaveTextContent(/isn't over/i)
  })

  it('downloads the CSV for the shown month', async () => {
    fetchPayroll.mockResolvedValue(report())
    downloadPayrollCsv.mockResolvedValue(undefined)
    render(<PayrollPanel />)
    await screen.findByText('Anita Rao')
    await userEvent.click(screen.getByRole('button', { name: /download csv/i }))
    await waitFor(() => expect(downloadPayrollCsv).toHaveBeenCalledTimes(1))
    const [year, month] = downloadPayrollCsv.mock.calls[0]
    expect(typeof year).toBe('number'); expect(month).toBeGreaterThanOrEqual(1)
  })

  it('explains itself when the API refuses', async () => {
    fetchPayroll.mockRejectedValue(new Error('403'))
    render(<PayrollPanel />)
    expect(await screen.findByText(/managers, admins and accountants/i)).toBeInTheDocument()
  })
})
