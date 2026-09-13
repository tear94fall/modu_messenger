import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Layout from './Layout'

const renderLayout = () =>
  render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  )

describe('Layout', () => {
  it('shows the brand logo next to the title', () => {
    const { container } = renderLayout()

    const logo = container.querySelector('.brand-logo')
    expect(logo).toBeInTheDocument()
    expect(logo).toHaveAttribute('src', '/favicon.svg')
    // 옆 글자가 이름을 말하므로 로고는 장식이다 — 낭독기가 두 번 읽으면 안 된다.
    expect(logo).toHaveAttribute('alt', '')
    expect(screen.getByText('모두메신저 백오피스')).toBeInTheDocument()
  })

  it('links to the main sections', () => {
    renderLayout()

    expect(screen.getByText('회원')).toBeInTheDocument()
    expect(screen.getByText('채팅방')).toBeInTheDocument()
    expect(screen.getByText('푸시')).toBeInTheDocument()
    expect(screen.getByText('앱 설정')).toBeInTheDocument()
  })
})
