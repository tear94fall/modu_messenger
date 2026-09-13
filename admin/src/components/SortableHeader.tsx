export type SortDir = 'asc' | 'desc'

interface Props {
  /** 화면에 보이는 머리글 */
  label: string
  /** 서버 정렬 값의 앞부분. 예: 'name' */
  field: string
  /** 지금 적용된 정렬. 'field,dir' 형태 그대로 받는다. */
  currentSort: string
  /** 이 열을 처음 누를 때 쓸 방향 */
  defaultDir: SortDir
  onChange: (sort: string) => void
}

/**
 * 누를 수 있는 표 머리글.
 * 이미 이 열로 정렬 중이면 방향만 뒤집고, 아니면 defaultDir 로 이 열을 잡는다.
 * 화살표는 엑셀과 같은 방향이다 — 오름차순(ㄱ→ㅎ, A→Z)이 ▼, 내림차순이 ▲.
 * 정렬 중이 아닌 열에는 아무 표시도 없다. 흐린 화살표는 지금 기준이 뭔지 알아보기 어렵게 한다.
 * 화살표는 장식이라 aria-hidden 이고, 대신 th 의 aria-sort 가 상태를 알린다.
 */
export default function SortableHeader({ label, field, currentSort, defaultDir, onChange }: Props) {
  const [activeField, activeDir] = currentSort.split(',')
  const active = activeField === field
  const dir: SortDir | undefined = active ? (activeDir === 'asc' ? 'asc' : 'desc') : undefined
  const nextDir: SortDir = active ? (dir === 'asc' ? 'desc' : 'asc') : defaultDir

  return (
    <th aria-sort={active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="sortable-header" onClick={() => onChange(`${field},${nextDir}`)}>
        {label}
        {active && (
          <span className="sort-arrow" aria-hidden="true">
            {dir === 'asc' ? '▼' : '▲'}
          </span>
        )}
      </button>
    </th>
  )
}
