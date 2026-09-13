import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as common from '../api/common'
import AppSettingsPage from './AppSettingsPage'

const renderPage = () =>
  render(
    <MemoryRouter>
      <AppSettingsPage />
    </MemoryRouter>,
  )

describe('AppSettingsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the version that is saved right now', async () => {
    vi.spyOn(common, 'getCommonData').mockResolvedValue({ key: 'version', value: '1.2.3' })
    renderPage()

    expect(await screen.findByText('앱 버전')).toBeInTheDocument()
    expect(screen.getByTestId('current-version')).toHaveTextContent('1.2.3')
    expect(screen.getByLabelText('현재 값')).toHaveValue('1.2.3')
  })

  /** 아직 한 번도 저장하지 않은 키는 404 다. 오류 문구가 아니라 빈칸으로 보여야 한다. */
  it('treats a 404 as "no value yet" instead of an error', async () => {
    vi.spyOn(common, 'getCommonData').mockRejectedValue(new ApiError(404, 'not found'))
    renderPage()

    expect(await screen.findByText('아직 값이 없습니다')).toBeInTheDocument()
    expect(screen.queryByText('앱 설정을 불러오지 못했습니다')).not.toBeInTheDocument()
  })

  it('saves the typed version and says so', async () => {
    vi.spyOn(common, 'getCommonData').mockResolvedValue({ key: 'version', value: '1.2.3' })
    const update = vi.spyOn(common, 'updateCommonData').mockResolvedValue({ key: 'version', value: '2.0.0' })
    renderPage()

    const input = await screen.findByLabelText('현재 값')
    await userEvent.clear(input)
    await userEvent.type(input, '2.0.0')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(update).toHaveBeenCalledWith('version', '2.0.0')
    expect(await screen.findByText('저장했습니다')).toBeInTheDocument()
    expect(screen.getByTestId('current-version')).toHaveTextContent('2.0.0')
  })

  it('shows an error message when saving fails', async () => {
    vi.spyOn(common, 'getCommonData').mockResolvedValue({ key: 'version', value: '1.2.3' })
    vi.spyOn(common, 'updateCommonData').mockRejectedValue(new ApiError(500, 'boom'))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '저장' }))

    expect(await screen.findByText('저장하지 못했습니다')).toBeInTheDocument()
  })
})
