import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearImageCache } from '../api/imageCache'
import * as rooms from '../api/rooms'
import * as storage from '../api/storage'
import RoomsPage from './RoomsPage'

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 15 }

describe('RoomsPage', () => {
  beforeEach(() => {
    clearImageCache()
  })

  it('shows the room image via a blob object URL when roomImage is set', async () => {
    vi.spyOn(rooms, 'listRooms').mockResolvedValue({
      content: [
        {
          id: 1,
          roomId: 'r1',
          roomName: 'Room One',
          roomImage: 'r.jpg',
          memberCount: 2,
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
        <RoomsPage />
      </MemoryRouter>,
    )

    const img = (await screen.findAllByAltText('Room One')).find((el) => el.tagName === 'IMG') as HTMLImageElement
    expect(img).toBeDefined()
    expect(img.src).toBe('blob:fake')
    expect(fetchImageObjectUrl).toHaveBeenCalledWith('r.jpg')
  })

  it('shows the first letter as a placeholder when roomImage is missing', async () => {
    vi.spyOn(rooms, 'listRooms').mockResolvedValue({
      content: [
        {
          id: 2,
          roomId: 'r2',
          roomName: 'Bob Room',
          memberCount: 1,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })

    render(
      <MemoryRouter>
        <RoomsPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Bob Room')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('keeps 최근 생성순 as the default ordering and has no 정렬 select', async () => {
    const listRooms = vi.spyOn(rooms, 'listRooms').mockResolvedValue(emptyPage)

    const { container } = render(
      <MemoryRouter>
        <RoomsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(listRooms).toHaveBeenCalledWith(0, 'createdDate,desc'))

    // 정렬은 머리글을 눌러서만 바꾼다. 같은 일을 하는 입력이 둘이면 어느 쪽이 진짜인지 헷갈린다.
    expect(screen.queryByLabelText('정렬')).toBeNull()
    expect(container.querySelector('select')).toBeNull()
  })

  it('sorts by clicking every column header, first click using that column default direction', async () => {
    const listRooms = vi.spyOn(rooms, 'listRooms').mockResolvedValue(emptyPage)

    render(
      <MemoryRouter>
        <RoomsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(listRooms).toHaveBeenCalledWith(0, 'createdDate,desc'))

    // [머리글, 처음 누를 때 방향, 다시 누를 때 방향]
    const columns: [string, string, string][] = [
      ['채팅방 이름', 'roomName,asc', 'roomName,desc'],
      ['멤버 수', 'memberCount,desc', 'memberCount,asc'],
      ['마지막 메시지', 'lastChatMsg,asc', 'lastChatMsg,desc'],
      ['마지막 시각', 'lastChatTime,desc', 'lastChatTime,asc'],
    ]

    for (const [label, first, second] of columns) {
      await userEvent.click(screen.getByRole('button', { name: label }))
      await waitFor(() => expect(listRooms).toHaveBeenLastCalledWith(0, first))
      expect(screen.getByRole('columnheader', { name: label })).toHaveAttribute(
        'aria-sort',
        first.endsWith('asc') ? 'ascending' : 'descending',
      )

      await userEvent.click(screen.getByRole('button', { name: label }))
      await waitFor(() => expect(listRooms).toHaveBeenLastCalledWith(0, second))
      expect(screen.getByRole('columnheader', { name: label })).toHaveAttribute(
        'aria-sort',
        second.endsWith('asc') ? 'ascending' : 'descending',
      )
    }
  })

  it('marks ascending with ▼ and descending with ▲, and leaves the other headers unmarked', async () => {
    const listRooms = vi.spyOn(rooms, 'listRooms').mockResolvedValue(emptyPage)

    render(
      <MemoryRouter>
        <RoomsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(listRooms).toHaveBeenCalledWith(0, 'createdDate,desc'))

    const header = (label: string) => screen.getByRole('columnheader', { name: label })
    const labels = ['채팅방 이름', '멤버 수', '마지막 메시지', '마지막 시각']

    // 기본값은 생성일순이고 그 열은 표에 없다. 그러니 어떤 머리글에도 표시가 없다.
    for (const label of labels) {
      expect(header(label).textContent).toBe(label)
      expect(header(label)).toHaveAttribute('aria-sort', 'none')
    }

    await userEvent.click(screen.getByRole('button', { name: '채팅방 이름' }))

    await waitFor(() => expect(listRooms).toHaveBeenLastCalledWith(0, 'roomName,asc'))
    // 엑셀과 같은 방향이다: 오름차순(ㄱ→ㅎ)이 아래 화살표.
    expect(header('채팅방 이름').textContent).toContain('▼')

    await userEvent.click(screen.getByRole('button', { name: '채팅방 이름' }))

    await waitFor(() => expect(listRooms).toHaveBeenLastCalledWith(0, 'roomName,desc'))
    expect(header('채팅방 이름').textContent).toContain('▲')
    expect(header('채팅방 이름').textContent).not.toContain('▼')
  })
})
