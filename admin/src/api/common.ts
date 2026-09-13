import { api } from './client'

/** 앱이 켜질 때 읽어 가는 key/value 설정 한 줄. 예: {"key":"version","value":"1.2.3"} */
export interface CommonData {
  key: string
  value: string
}

const BASE = '/member-service/api-admin/common'

/** 아직 값을 넣지 않은 키는 서버가 404 를 준다. 부르는 쪽에서 ApiError(404) 를 "값 없음"으로 다룬다. */
export const getCommonData = (key: string) => api<CommonData>(`${BASE}/${encodeURIComponent(key)}`)

export const updateCommonData = (key: string, value: string) =>
  api<CommonData>(`${BASE}/${encodeURIComponent(key)}`, { method: 'PUT', body: JSON.stringify({ value }) })
