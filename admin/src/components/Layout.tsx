import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { logout as logoutRequest } from '../api/auth'
import { clearToken } from '../auth/token'

export default function Layout() {
  const navigate = useNavigate()

  const logout = () => {
    logoutRequest()
      .catch(() => {})
      .finally(() => {
        clearToken()
        navigate('/login')
      })
  }

  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="brand">
          {/* 옆 글자가 이름을 말하므로 로고는 장식이다 — alt 를 비워 화면 낭독기가 두 번 읽지 않게 한다. */}
          <img src="/favicon.svg" alt="" className="brand-logo" />
          <span>모두메신저 백오피스</span>
        </div>
        <div className="sidebar-nav">
          <NavLink to="/members" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            회원
          </NavLink>
          <NavLink to="/rooms" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            채팅방
          </NavLink>
          <NavLink to="/push" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            푸시
          </NavLink>
          <NavLink to="/settings" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            앱 설정
          </NavLink>
        </div>
        <div className="sidebar-footer">
          <NavLink to="/me" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            내 정보
          </NavLink>
          <button type="button" className="btn btn--ghost" onClick={logout}>
            로그아웃
          </button>
        </div>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
