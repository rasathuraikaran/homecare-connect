import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Arrow, Bulb, Check } from '../components/Icons'
import { JOB_ICONS } from '../components/jobIcons'
import { JOBS } from '../constants/jobs'
import ProfilePreview from '../components/ProfilePreview'
import ReviewList, { RatingSummary } from '../components/ReviewList'
import VerificationCard from '../components/VerificationCard'
import {
  COMPLETENESS_ITEMS,
  GENERIC_TIP,
  TIPS,
  caretakerCompleteness,
  completeness,
  loadOnboardingExtras,
} from '../constants/completeness'

export default function CaregiverHome({ firstName }) {
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    setError('')
    api('/caregivers/me/profile').then(setProfile).catch((e) => setError(e.message))
  }, [attempt])

  return (
    <section className="content wide">
      <div className="page-head">
        <h1>Welcome, {firstName} 👋</h1>
        <p className="subtitle">Here is how your profile looks to families, and what to add next.</p>
      </div>

      {error && (
        <div className="alert" role="alert"><Alert />{error}
          <button type="button" className="link" onClick={() => setAttempt(attempt + 1)}>Try again</button>
        </div>
      )}

      {!profile && !error && (
        <div className="cg-home">
          <div className="block skeleton" style={{ minHeight: 320 }} />
          <div className="block skeleton" style={{ minHeight: 320 }} />
        </div>
      )}

      {profile && <Loaded profile={profile} />}
    </section>
  )
}

function Loaded({ profile }) {
  const { user } = useAuth()
  const [available, setAvailable] = useState(profile.available)
  const [listed, setListed] = useState(profile.listed)
  const [verification, setVerification] = useState(null)
  const [toggleError, setToggleError] = useState('')
  const [toggling, setToggling] = useState(false)
  const [reviews, setReviews] = useState(null)

  const isCaretaker = profile.jobType === 'CARETAKER'
  const userKey = user?.id ?? user?.email
  const serverExtras = profile?.caretakerOnboarding
    ? Object.fromEntries(Object.entries(profile.caretakerOnboarding).filter(([, v]) => v !== null && v !== undefined))
    : {}
  const extras = { ...loadOnboardingExtras(userKey), ...serverExtras }

  useEffect(() => {
    api('/caregivers/me/reviews').then(setReviews).catch(() => setReviews({ average: null, count: 0, reviews: [], failed: true }))
  }, [])

  async function toggle() {
    if (!profile.address) {
      setToggleError('Save your profile details (name and address) first to change your availability.')
      return
    }
    const next = !available
    setAvailable(next)            // optimistic: flip now, undo if the server says no
    setToggleError('')
    setToggling(true)
    try {
      await api('/caregivers/me/availability', { method: 'PUT', body: { available: next } })
    } catch (e) {
      setAvailable(!next)
      setToggleError(e.message)
    } finally {
      setToggling(false)
    }
  }

  const standardProgress = completeness(profile)
  const ctProgress = caretakerCompleteness(profile, verification, extras)
  const activeMissing = isCaretaker ? ctProgress.missing : standardProgress.missing
  const complete = activeMissing.length === 0
  const tips = [...new Set(activeMissing.map((m) => TIPS[m.key]).filter(Boolean))].slice(0, 3)

  return (
    <div className="cg-home">
      <div className="cg-home-main">
        <section className={available ? 'block availability on' : 'block availability'}>
          <div>
            <h2>{available ? 'You are available for work' : 'You are not available right now'}</h2>
            <p className="muted small">
              {!listed
                ? 'Families cannot see you until your required documents are approved. See the card below.'
                : available ? 'Families can find you when they search.' : 'You are hidden from search until you switch this back on.'}
            </p>
            {toggleError && <p className="error-text small" role="alert">{toggleError}</p>}
          </div>
          <button type="button" role="switch" aria-checked={available} aria-label="Available for work"
                  className="switch" disabled={toggling} onClick={toggle}><span /></button>
        </section>

        <VerificationCard onStatus={setListed} onView={setVerification} />

        {/* If the user has not chosen a jobType yet, let them pick their role right here */}
        {!profile.jobType && (
          <section className="block">
            <div className="block-head">
              <h2>Start by choosing what you do</h2>
            </div>
            <p className="muted small">
              Each professional role has its own onboarding and verification steps. Select your role to begin:
            </p>
            <div className="jobs" role="group" aria-label="Choose your role">
              {JOBS.map(({ value, label, desc }) => {
                const Icon = JOB_ICONS[value]
                const to = value === 'CARETAKER' ? '/profile?jobType=CARETAKER&step=about' : `/profile?jobType=${value}&focus=job`
                return (
                  <Link key={value} to={to} className="job">
                    <span className="job-icon"><Icon /></span>
                    <span className="job-text"><strong>{label}</strong><em>{desc}</em></span>
                  </Link>
                )
              })}
            </div>
          </section>
        )}

        {/* CARETAKER-ONLY: 10-Step, 4-Phase Onboarding Flow with Sequential Step Gating */}
        {isCaretaker ? (
          <section className="block onboarding-card">
            <div className="onboarding-head">
              <div>
                <span className="eyebrow">CARETAKER ONBOARDING JOURNEY · 4 PHASES</span>
                <h2>{complete ? 'Caretaker onboarding complete 🎉' : `Caretaker onboarding · ${ctProgress.doneCount} of ${ctProgress.totalCount} completed`}</h2>
              </div>
              <span className="onboarding-badge">{ctProgress.percent}% completed</span>
            </div>

            <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={ctProgress.percent} aria-label="Caretaker onboarding completeness">
              <span style={{ width: `${ctProgress.percent}%` }} />
            </div>

            <div className="onboarding-cta-row">
              <p className="muted small">
                {complete
                  ? 'All 10 caretaker onboarding steps are finished. You can review or edit any step below anytime.'
                  : `Current step: ${ctProgress.nextStep.label} — ${ctProgress.nextStep.action}. Complete each step in order.`}
              </p>
              {!complete && (
                <Link to={`/profile?step=${ctProgress.nextStep.focus}`} className="btn-primary small-btn link-btn">
                  {ctProgress.doneCount <= 1 ? 'Start Step 2' : `Continue · Step ${ctProgress.nextStep.stepNumber}`}
                </Link>
              )}
            </div>

            <div className="phase-grid">
              {ctProgress.phases.map((phase) => {
                const phaseDone = phase.steps.filter((s) => s.isDone).length
                return (
                  <div key={phase.id} className="phase-box">
                    <div className="phase-head">
                      <strong>Phase {phase.phaseNumber} · {phase.title}</strong>
                      <span className="muted small">{phaseDone}/{phase.steps.length}</span>
                    </div>
                    <ul className="todo phase-steps">
                      {phase.steps.map((step) => {
                        const isAccountStep = step.key === 'account'
                        const isCurrentNext = !step.isDone && step.stepNumber === ctProgress.maxUnlockedStepNumber
                        const isLocked = !step.isDone && !step.isUnlocked

                        if (step.isDone) {
                          return (
                            <li key={step.key} className="done">
                              <span className="tick"><Check /></span>
                              {isAccountStep ? (
                                <span>
                                  <strong>{step.label}</strong>
                                  <em className="step-sub">{step.action}</em>
                                </span>
                              ) : (
                                <Link to={`/profile?step=${step.focus}`}>
                                  <span>
                                    <strong>{step.label}</strong>
                                    <em className="step-sub">{step.action}</em>
                                  </span>
                                  <span className="edit-pill">Edit</span>
                                </Link>
                              )}
                            </li>
                          )
                        }

                        if (isCurrentNext) {
                          return (
                            <li key={step.key} className="current-step-item">
                              <span className="tick current-num">{step.stepNumber}</span>
                              <Link to={`/profile?step=${step.focus}`}>
                                <span>
                                  <strong>{step.label}</strong>
                                  <em className="step-sub">{step.action}</em>
                                </span>
                                <Arrow />
                              </Link>
                            </li>
                          )
                        }

                        return (
                          <li key={step.key} className={isLocked ? 'locked' : ''} aria-disabled={isLocked}>
                            <span className="tick locked-num">{step.stepNumber}</span>
                            <div className="locked-step-body">
                              <span>
                                <strong>{step.label}</strong>
                                <em className="step-sub">{step.action}</em>
                              </span>
                              <span className="locked-pill">Locked</span>
                            </div>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                )
              })}
            </div>
          </section>
        ) : (
          /* NURSING & PHYSIOTHERAPIST (and unselected): Original Completeness Card */
          <section className="block">
            <div className="block-head">
              <h2>{complete ? 'Your profile is complete 🎉' : `Your profile is ${standardProgress.percent}% complete`}</h2>
              {!complete && (
                <span className="muted">
                  {standardProgress.items.length - standardProgress.missing.length} of {standardProgress.items.length} done
                </span>
              )}
            </div>
            <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={standardProgress.percent} aria-label="Profile completeness">
              <span style={{ width: `${standardProgress.percent}%` }} />
            </div>
            <p className="muted small">
              {complete ? 'Families can see everything about you.' : 'A complete profile makes it easier for families to choose you.'}
            </p>

            <ul className="todo">
              {COMPLETENESS_ITEMS.map((def) => {
                const item = standardProgress.items.find((i) => i.key === def.key)
                return item.isDone ? (
                  <li key={item.key} className="done"><span className="tick"><Check /></span>{item.label}</li>
                ) : (
                  <li key={item.key}>
                    <span className="tick open" />
                    <Link to={`/profile?focus=${item.focus}`}>{item.action}<Arrow /></Link>
                  </li>
                )
              })}
            </ul>
          </section>
        )}

        <section className="block">
          <div className="block-head">
            <h2>Reviews</h2>
            {reviews && <RatingSummary average={reviews.average} count={reviews.count} />}
          </div>
          {!reviews && <p className="muted">Loading…</p>}
          {reviews?.failed && <p className="muted">Could not load your reviews right now.</p>}
          {reviews && !reviews.failed && (
            <ReviewList reviews={reviews.reviews.slice(0, 5)} emptyText="No reviews yet. They appear here when families rate you." />
          )}
        </section>

        <section className="block tips">
          <div className="block-head"><h2><Bulb /> Tips</h2></div>
          <ul>
            {(tips.length ? tips : [GENERIC_TIP]).map((t) => <li key={t}>{t}</li>)}
          </ul>
        </section>
      </div>

      <div className="side">
        <ProfilePreview name={profile.fullName} age={profile.age ?? ''} years={profile.experienceYears ?? ''}
                        qualification={profile.qualification ?? ''} photo={profile.photoUrl} jobType={profile.jobType}
                        rates={profile.rates} />
        <Link
          to={isCaretaker ? `/profile?step=${complete ? 'review' : ctProgress.nextStep.focus}` : '/profile'}
          className="btn-primary link-btn"
        >
          {isCaretaker ? (complete ? 'Review / Edit profile' : `Continue · Step ${ctProgress.nextStep.stepNumber}`) : 'Edit profile'}
        </Link>
      </div>
    </div>
  )
}
