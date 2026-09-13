import { type FormEvent, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { getCommonData, updateCommonData } from '../api/common'

/** 앱이 읽어 가는 설정 키. 안드로이드가 GET /api-public/common/version 으로 부른다. */
const VERSION_KEY = 'version'

export default function AppSettingsPage() {
  const [version, setVersion] = useState('')
  const [savedVersion, setSavedVersion] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getCommonData(VERSION_KEY)
      .then((data) => {
        if (cancelled) return
        setVersion(data.value)
        setSavedVersion(data.value)
      })
      .catch((err) => {
        if (cancelled) return
        // 아직 한 번도 저장하지 않은 키다. 오류가 아니라 "값 없음"이므로 빈칸으로 둔다.
        if (err instanceof ApiError && err.status === 404) return
        setLoadError('앱 설정을 불러오지 못했습니다')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setMessage(null)
    setError(null)
    setSaving(true)
    try {
      const saved = await updateCommonData(VERSION_KEY, version)
      setVersion(saved.value)
      setSavedVersion(saved.value)
      setMessage('저장했습니다')
    } catch {
      setError('저장하지 못했습니다')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <h1>앱 설정</h1>

      {loading && <p>불러오는 중...</p>}
      {loadError && <p className="error-text">{loadError}</p>}

      {!loading && !loadError && (
        <>
          <h2>앱 버전</h2>
          <form className="form-card" onSubmit={onSubmit}>
            <div className="form-section">
              <div className="form-field">
                <label htmlFor="app-version">현재 값</label>
                <p className="form-hint" data-testid="current-version">
                  {savedVersion === '' ? '아직 값이 없습니다' : savedVersion}
                </p>
                <input
                  id="app-version"
                  value={version}
                  required
                  onChange={(e) => setVersion(e.target.value)}
                />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn--primary" disabled={saving}>
                저장
              </button>
            </div>
          </form>

          {message && <p className="result-text">{message}</p>}
          {error && <p className="error-text">{error}</p>}
        </>
      )}
    </div>
  )
}
