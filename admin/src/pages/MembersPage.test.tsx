import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearImageCache } from '../api/imageCache'
import * as members from '../api/members'
import * as storage from '../api/storage'
import MembersPage from './MembersPage'

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 15 }

describe('MembersPage', () => {
  beforeEach(() => {
    clearImageCache()
  })

  it('renders columns in order (이름, 이메일, 사용자 ID, 권한, 가입일) with formatted createdDate', async () => {
    vi.spyOn(members, 'searchMembers').mockResolvedValue({
      content: [
        {
          id: 1,
          userId: 'u1',
          email: 'a@b.c',
          username: 'Alice',
          role: 'ROLE_MEMBER',
          createdDate: '2026-09-04T12:34:56',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })

    render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    const headers = await screen.findAllByRole('columnheader')
    // 정렬 가능한 머리글에는 방향 화살표가 붙는다. 여기서 보려는 건 열 차례뿐이라 화살표는 걷어낸다.
    expect(headers.map((h) => h.textContent?.replace(/[\u25b2\u25bc\u2195]/g, ''))).toEqual([
      '',
      '이름',
      '이메일',
      '사용자 ID',
      '권한',
      '가입일',
    ])

    expect(await screen.findByText('일반 회원')).toBeInTheDocument()
    expect(await screen.findByText('2026-09-04 12:34')).toBeInTheDocument()
  })

  it('shows the profile image via a blob object URL when profileImage is set', async () => {
    vi.spyOn(members, 'searchMembers').mockResolvedValue({
      content: [
        {
          id: 1,
          userId: 'u1',
          email: 'a@b.c',
          username: 'Alice',
          role: 'ROLE_MEMBER',
          profileImage: 'a.jpg',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })
    const fetchImageObjectUrl = vi.spyOn(storage, 'fetchImageObjectUrl').mockResolvedValue('blob:fake')

    render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    const img = (await screen.findAllByAltText('Alice')).find((el) => el.tagName === 'IMG') as HTMLImageElement
    expect(img).toBeDefined()
    expect(img.src).toBe('blob:fake')
    expect(fetchImageObjectUrl).toHaveBeenCalledWith('a.jpg')
  })

  it('shows the first letter as a placeholder when profileImage is missing', async () => {
    vi.spyOn(members, 'searchMembers').mockResolvedValue({
      content: [
        {
          id: 2,
          userId: 'u2',
          email: 'b@c.d',
          username: 'Bob',
          role: 'ROLE_MEMBER',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })

    render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Bob')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('asks for the default 이름순 ordering and has no 정렬 select', async () => {
    const searchMembers = vi.spyOn(members, 'searchMembers').mockResolvedValue(emptyPage)

    const { container } = render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(searchMembers).toHaveBeenCalledWith('', 0, 'name,asc'))

    // 정렬은 머리글을 눌러서만 바꾼다. 같은 일을 하는 입력이 둘이면 어느 쪽이 진짜인지 헷갈린다.
    expect(screen.queryByLabelText('정렬')).toBeNull()
    expect(container.querySelector('select')).toBeNull()
  })

  it('sorts by clicking every column header, first click using that column default direction', async () => {
    const searchMembers = vi.spyOn(members, 'searchMembers').mockResolvedValue(emptyPage)

    render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(searchMembers).toHaveBeenCalledWith('', 0, 'name,asc'))

    // [머리글, 처음 누를 때 방향, 다시 누를 때 방향]
    const columns: [string, string, string][] = [
      ['이메일', 'email,asc', 'email,desc'],
      ['사용자 ID', 'userId,asc', 'userId,desc'],
      ['권한', 'role,asc', 'role,desc'],
      ['가입일', 'createdDate,desc', 'createdDate,asc'],
      ['이름', 'name,asc', 'name,desc'],
    ]

    for (const [label, first, second] of columns) {
      await userEvent.click(screen.getByRole('button', { name: label }))
      await waitFor(() => expect(searchMembers).toHaveBeenLastCalledWith('', 0, first))
      expect(screen.getByRole('columnheader', { name: label })).toHaveAttribute(
        'aria-sort',
        first.endsWith('asc') ? 'ascending' : 'descending',
      )

      await userEvent.click(screen.getByRole('button', { name: label }))
      await waitFor(() => expect(searchMembers).toHaveBeenLastCalledWith('', 0, second))
      expect(screen.getByRole('columnheader', { name: label })).toHaveAttribute(
        'aria-sort',
        second.endsWith('asc') ? 'ascending' : 'descending',
      )
    }
  })

  it('marks ascending with ▼ and descending with ▲, and leaves the other headers unmarked', async () => {
    const searchMembers = vi.spyOn(members, 'searchMembers').mockResolvedValue(emptyPage)

    render(
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(searchMembers).toHaveBeenCalledWith('', 0, 'name,asc'))

    const header = (label: string) => screen.getByRole('columnheader', { name: label })
    const others = ['이메일', '사용자 ID', '권한', '가입일']

    // 엑셀과 같은 방향이다: 오름차순(ㄱ→ㅎ)이 아래 화살표.
    expect(header('이름').textContent).toContain('▼')
    for (const label of others) {
      expect(header(label).textContent).toBe(label)
      expect(header(label)).toHaveAttribute('aria-sort', 'none')
    }

    await userEvent.click(screen.getByRole('button', { name: '이름' }))

    await waitFor(() => expect(searchMembers).toHaveBeenLastCalledWith('', 0, 'name,desc'))
    expect(header('이름').textContent).toContain('▲')
    expect(header('이름').textContent).not.toContain('▼')
  })
})
