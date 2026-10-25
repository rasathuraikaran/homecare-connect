import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import CaregiverCard from '../components/CaregiverCard'
import { Activity, Alert, Heart, Stethoscope } from '../components/Icons'
import { JOBS } from '../constants/jobs'

const ICONS = { CARETAKER: Heart, NURSING: Stethoscope, PHYSIOTHERAPIST: Activity }

export default function SeekerHome({ firstName }) {
  const [params, setParams] = useSearchParams()
  const selected = JOBS.find((j) => j.value === params.get('job')) ?? null
  const resultsRef = useRef(null)

  const [counts, setCounts] = useState({})
  const [result, setResult] = useState({ status: 'idle', list: [], error: '' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    api('/caregivers/counts').then(setCounts).catch(() => {})
  }, [])

  useEffect(() => {
    if (!selected) return
    let cancelled = false
    setResult({ status: 'loading', list: [], error: '' })
    api(`/caregivers?job=${selected.value}`)
      .then((list) => !cancelled && setResult({ status: 'done', list, error: '' }))
      .catch((e) => !cancelled && setResult({ status: 'error', list: [], error: e.message }))
    return () => { cancelled = true }
  }, [selected?.value, attempt])

  function choose(value) {
    if (value === selected?.value) {
      setParams({})
      return
    }
    setParams({ job: value })
    // wait a tick so the results section exists before scrolling to it
    setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
  }

  return (
    <section className="content wide seeker">
      <div className="page-head">
        <h1>Hi {firstName}, who are you looking for?</h1>
        <p className="subtitle">Choose the kind of care you need and we'll show you available people.</p>
      </div>

      <div className="care-tiles" role="radiogroup" aria-label="Type of care">
        {JOBS.map(({ value, label, plural, desc }) => {
          const Icon = ICONS[value]
          const active = selected?.value === value
          const n = counts[value]
          return (
            <button key={value} type="button" role="radio" aria-checked={active}
                    className={active ? 'care-tile active' : 'care-tile'} onClick={() => choose(value)}>
              <span className="care-icon"><Icon /></span>
              <strong>{label === 'Nursing' ? 'Nurse' : label}</strong>
              <em>{desc}</em>
              <span className="care-count">{n == null ? '\u00a0' : `${n} ${plural.toLowerCase()} available`}</span>
            </button>
          )
        })}
      </div>

      <div ref={resultsRef} className="results" aria-live="polite">
        {!selected && <p className="empty-hint">Pick an option above to see caregivers.</p>}

        {selected && (
          <>
            <h2 className="results-title">
              {selected.plural}
              {result.status === 'done' && <span className="results-count">{result.list.length}</span>}
            </h2>

            {result.status === 'loading' && (
              <div className="cg-grid">{[0, 1, 2].map((i) => <div key={i} className="cg-card skeleton" />)}</div>
            )}

            {result.status === 'error' && (
              <div className="alert" role="alert"><Alert />{result.error}
                <button type="button" className="link" onClick={() => setAttempt(attempt + 1)}>Try again</button>
              </div>
            )}

            {result.status === 'done' && result.list.length === 0 && (
              <div className="empty">
                <strong>No {selected.plural.toLowerCase()} available yet</strong>
                <span>Check back soon, or try another option.</span>
              </div>
            )}

            {result.status === 'done' && result.list.length > 0 && (
              <div className="cg-grid">{result.list.map((c) => <CaregiverCard key={c.id} caregiver={c} />)}</div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
