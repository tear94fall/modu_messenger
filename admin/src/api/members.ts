import { api, PAGE_SIZE } from './client'
export interface Member {
  id: number
  userId: string
  email: string
  username: string
  role: string
  statusMessage?: string
  profileImage?: string
  wallpaperImage?: string
  createdDate?: string
  /** 회원 상세의 친구 목록에서만 온다: 그 회원이 이 친구에게 정한 이름 */
  friendName?: string
}
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export interface MemberDetail { member: Member; friendCount: number; createdDate?: string; friends: Member[] }
/** 서버(MemberSort)가 받아 주는 값. 목록에 값이 보이는 열은 모두 있다. 그 밖의 값을 보내면 400 이 온다. */
export const MEMBER_SORTS = [
  'name,asc',
  'name,desc',
  'email,asc',
  'email,desc',
  'userId,asc',
  'userId,desc',
  'role,asc',
  'role,desc',
  'createdDate,desc',
  'createdDate,asc',
] as const
export type MemberSort = (typeof MEMBER_SORTS)[number]
/** 이름 가나다순(한글 이름 먼저). 서버 기본값과 같게 둔다. */
export const DEFAULT_MEMBER_SORT: MemberSort = 'name,asc'
export const searchMembers = (keyword: string, page: number, sort: MemberSort = DEFAULT_MEMBER_SORT) =>
  api<Page<Member>>(
    `/member-service/api-admin/member?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${PAGE_SIZE}&sort=${encodeURIComponent(sort)}`,
  )
export const getMember = (id: string) => api<MemberDetail>(`/member-service/api-admin/member/${id}`)
export const getMe = () => api<MemberDetail>('/member-service/api-admin/member/me')
export const updateMe = (body: { username?: string; statusMessage?: string; profileImage?: string; wallpaperImage?: string }) =>
  api<MemberDetail>('/member-service/api-admin/member/me', { method: 'PUT', body: JSON.stringify(body) })
