import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import AppSettingsPage from './pages/AppSettingsPage'
import LoginPage from './pages/LoginPage'
import MemberDetailPage from './pages/MemberDetailPage'
import MembersPage from './pages/MembersPage'
import MePage from './pages/MePage'
import PushPage from './pages/PushPage'
import RoomDetailPage from './pages/RoomDetailPage'
import RoomsPage from './pages/RoomsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/members" replace />} />
          <Route path="/members" element={<MembersPage />} />
          <Route path="/members/:id" element={<MemberDetailPage />} />
          <Route path="/rooms" element={<RoomsPage />} />
          <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
          <Route path="/push" element={<PushPage />} />
          <Route path="/settings" element={<AppSettingsPage />} />
          <Route path="/me" element={<MePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
