import { api, PAGE_SIZE } from './client'
import type { Page } from './members'
export interface RoomSummary { id: number; roomId: string; roomName: string; roomImage?: string; memberCount: number; lastChatMsg?: string; lastChatTime?: string }
export interface RoomDetail {
  id: number
  roomId: string
  roomName: string
  roomImage?: string
  lastChatMsg?: string
  lastChatId?: number
  lastChatTime?: string
  members: { id: number; userId: string; email: string; username: string; role?: string; profileImage?: string }[]
}
export interface Chat { id: number; sender: string; message: string; chatTime: string; chatType: number }
/** 서버(ChatRoomSort)가 받아 주는 값. 목록에 값이 보이는 열은 모두 있다. 그 밖의 값을 보내면 400 이 온다. */
export const ROOM_SORTS = [
  'createdDate,desc',
  'createdDate,asc',
  'lastChatTime,desc',
  'lastChatTime,asc',
  'lastChatMsg,asc',
  'lastChatMsg,desc',
  'roomName,asc',
  'roomName,desc',
  'memberCount,asc',
  'memberCount,desc',
] as const
export type RoomSort = (typeof ROOM_SORTS)[number]
/** 최근 생성순. 서버 기본값과 같게 둔다. */
export const DEFAULT_ROOM_SORT: RoomSort = 'createdDate,desc'
export const listRooms = (page: number, sort: RoomSort = DEFAULT_ROOM_SORT) =>
  api<Page<RoomSummary>>(
    `/chat-service/api-admin/chat/rooms?page=${page}&size=${PAGE_SIZE}&sort=${encodeURIComponent(sort)}`,
  )
export const getRoom = (roomId: string) => api<RoomDetail>(`/chat-service/api-admin/chat/rooms/${roomId}`)
export const getRoomChats = (roomId: string, page: number) =>
  api<Page<Chat>>(`/chat-service/api-admin/chat/rooms/${roomId}/chats?page=${page}&size=${PAGE_SIZE}`)
