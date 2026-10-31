import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import Field from './Field'
import ProfilePreview from './ProfilePreview'
import StatusChip from './StatusChip'
import { Alert, Check, HeartHouse, Shield } from './Icons'
import { JOB_ICONS } from './jobIcons'
import { JOBS, jobLabel } from '../constants/jobs'
import { CURRENCY, RATE_TYPES, formatMoney } from '../constants/rates'
import { DOCUMENT_FILE_TYPES, DOC_LABEL, DOC_TONE, MAX_DOCUMENT, OVERALL_LABEL, OVERALL_TONE } from '../constants/verification'
import {
  CALL_TIME_SLOTS,
  HOME_NURSING_SERVICES,
  NURSING_CLINICAL_AREAS,
  NURSING_CONSENTS,
  NURSING_DURATIONS,
  NURSING_LAST_PERFORMED,
  NURSING_ONBOARDING_PHASES,
  NURSING_QUALIFICATION_TYPES,
  NURSING_REGISTRATION_BODIES,
  NURSING_SERVICE_EXPERIENCE_LEVELS,
  NURSING_TIMES_PERFORMED,
  NURSING_TRAVEL_DISTANCES,
  NURSING_WIZARD_STEPS,
  NURSING_WORK_STATUSES,
  ROLE_DYNAMIC_REQUIREMENTS,
  SHIFT_OPTIONS,
  WEEK_DAYS,
  nursingCompleteness,
} from '../constants/completeness'

const digits = (v, max) => String(v ?? '').replace(/\D/g, '').slice(0, max)

const EMPTY_NURSING_WORK_ENTRY = {
  org: '',
  title: '',
  department: '',
  duration: '',
  responsibilities: '',
}

export default function NurseOnboardingWizard({
  user,
  form,
  setForm,
  rnExtras,
  updateRnExtras,
  photoUrl,
  setPhotoUrl,
  newPhoto,
  setNewPhoto,
  removePhoto,
  setRemovePhoto,
  shownPhoto,
  pickPhoto,
  dragging,
  setDragging,
  fileRef,
  docRows,
  savedDocs,
  setSavedDocs,
  newDocs,
  setNewDocs,
  verification,
  setVerification,
  openDocPicker,
  removeQueuedDoc,
  docRef,
  onDocInputChange,
  requestedStep,
  setParams,
  refresh,
  logout,
}) {
  const navigate = useNavigate()
  const bannerRef = useRef(null)
  const [serverIssues, setServerIssues] = useState([])
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [savedNotice, setSavedNotice] = useState(false)

  const num = (v) => (v === '' || v == null ? null : Number(v))
  const ageError = form.age !== '' && (Number(form.age) < 18 || Number(form.age) > 100) ? 'Age must be between 18 and 100' : ''
  const expError = form.experienceYears !== '' && Number(form.experienceYears) > 60 ? 'Enter 60 or fewer years' : ''
  const nameError = !form.fullName.trim() ? 'Full name is required' : ''
  const addressError = !form.address.trim() ? 'Address is required' : ''
  const rateErrors = Object.fromEntries(
    Object.entries(form.rates).map(([k, v]) => [k, v === '' || Number(v) <= 0 ? 'Enter an amount' : ''])
  )

  const rnProgress = nursingCompleteness(
    {
      ...form,
      age: num(form.age),
      experienceYears: num(form.experienceYears),
      photoUrl: shownPhoto,
    },
    verification,
    rnExtras
  )

  const maxAllowedWizardIndex = Math.max(
    0,
    NURSING_WIZARD_STEPS.findIndex((s) => s.stepNumber === rnProgress.maxUnlockedStepNumber)
  )
  const requestedWizardIndex = requestedStep
    ? NURSING_WIZARD_STEPS.findIndex((s) => s.key === requestedStep)
    : -1
  const currentStepIndex =
    requestedWizardIndex >= 0
      ? Math.min(requestedWizardIndex, maxAllowedWizardIndex)
      : maxAllowedWizardIndex
  const currentStep = NURSING_WIZARD_STEPS[currentStepIndex] || NURSING_WIZARD_STEPS[0]

  function goToStep(stepKey) {
    const targetIdx = NURSING_WIZARD_STEPS.findIndex((s) => s.key === stepKey)
    if (targetIdx > maxAllowedWizardIndex) return
    setAttempted(false)
    setServerIssues([])
    setParams({ step: stepKey }, { replace: true })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function showServerIssues(list) {
    setServerIssues(list)
    setTimeout(() => bannerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
  }

  function handleDobChange(dobValue) {
    updateRnExtras({ dob: dobValue })
    if (dobValue) {
      const birth = new Date(dobValue)
      if (!Number.isNaN(birth.getTime())) {
        const today = new Date()
        let calcAge = today.getFullYear() - birth.getFullYear()
        const m = today.getMonth() - birth.getMonth()
        if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) calcAge--
        if (calcAge >= 18 && calcAge <= 100) {
          setForm((prev) => ({ ...prev, age: String(calcAge) }))
        }
      }
    }
  }

  // Service selection & inline updates for Step 5
  const selectedServices = Array.isArray(rnExtras.services) ? rnExtras.services : []

  function findService(code) {
    return selectedServices.find((s) => s.code === code) || null
  }

  function syncServicesToForm(nextServices, minVisitFeeVal = rnExtras.minVisitFee) {
    const names = nextServices.map((s) => s.name).join(', ').slice(0, 255)
    const positiveRates = nextServices.map((s) => Number(s.expectedRate)).filter((n) => n > 0)
    const lowestServiceRate = positiveRates.length > 0 ? Math.min(...positiveRates) : null
    const sessionAmount = Number(minVisitFeeVal) > 0 ? Number(minVisitFeeVal) : lowestServiceRate

    setForm((prev) => {
      const nextRates = { ...prev.rates }
      if (sessionAmount && sessionAmount > 0) {
        nextRates.SESSION = String(sessionAmount)
      }
      return {
        ...prev,
        qualification: names || prev.qualification,
        rates: nextRates,
      }
    })
  }

  function toggleNursingService(catalogItem) {
    const exists = findService(catalogItem.code)
    let next
    if (exists) {
      next = selectedServices.filter((s) => s.code !== catalogItem.code)
    } else {
      next = [
        ...selectedServices,
        {
          code: catalogItem.code,
          name: catalogItem.name,
          category: catalogItem.category,
          expectedRate: catalogItem.suggestedRate || '',
          duration: catalogItem.defaultDuration || '30 minutes',
          experienceLevel: NURSING_SERVICE_EXPERIENCE_LEVELS[1],
          timesPerformed: '10–50',
          lastPerformed: 'Within 3 months',
          bringsEquipment: true,
          providesConsumables: false,
        },
      ]
    }
    updateRnExtras({ services: next })
    syncServicesToForm(next)
  }

  function updateNursingServiceField(code, field, value) {
    const next = selectedServices.map((s) => (s.code === code ? { ...s, [field]: value } : s))
    updateRnExtras({ services: next })
    if (field === 'expectedRate') {
      syncServicesToForm(next)
    }
  }

  function toggleRate(type) {
    const rates = { ...form.rates }
    if (type in rates) delete rates[type]
    else rates[type] = ''
    setForm({ ...form, rates })
  }
  const setRate = (type) => (e) => setForm({ ...form, rates: { ...form.rates, [type]: digits(e.target.value, 7) } })

  function updateWorkEntry(index, field, value) {
    const list = [...(rnExtras.workHistory || [{ ...EMPTY_NURSING_WORK_ENTRY }])]
    list[index] = { ...list[index], [field]: value }
    updateRnExtras({ workHistory: list })
  }

  function addWorkEntry() {
    const list = [...(rnExtras.workHistory || [{ ...EMPTY_NURSING_WORK_ENTRY }]), { ...EMPTY_NURSING_WORK_ENTRY }]
    updateRnExtras({ workHistory: list })
  }

  function removeWorkEntry(index) {
    const list = (rnExtras.workHistory || []).filter((_, i) => i !== index)
    updateRnExtras({ workHistory: list.length ? list : [{ ...EMPTY_NURSING_WORK_ENTRY }] })
  }

  function getStepValidationProblems(stepKey) {
    const list = []
    if (nameError) list.push(nameError)
    if (addressError) list.push(addressError)

    if (stepKey === 'about') {
      if (form.age === '') list.push('Please enter your date of birth or age (18–100)')
      else if (ageError) list.push(ageError)
      if (!rnExtras.gender) list.push('Please select your gender')
      if (!rnExtras.emergencyContactName?.trim()) list.push('Emergency contact name is required')
      if (!rnExtras.emergencyContactPhone?.trim()) list.push('Emergency contact phone number is required')
    }

    if (stepKey === 'job') {
      if (!rnExtras.nursingQualificationType) {
        list.push('Please select your nursing qualification')
      }
      if (!rnExtras.registrationBody) {
        list.push('Please select your professional registration body (e.g. SLNC / SLMC)')
      }
    }

    if (stepKey === 'experience') {
      // Optional step: only validate experienceYears range if entered
      if (form.experienceYears !== '' && expError) {
        list.push(expError)
      }
    }

    if (stepKey === 'skills') {
      if (selectedServices.length === 0) {
        list.push('Please select at least one home nursing service you can provide')
      } else {
        for (const s of selectedServices) {
          if (!s.expectedRate || Number(s.expectedRate) <= 0) {
            list.push(`Please enter your expected payment (LKR) for "${s.name}"`)
          }
          if (!s.experienceLevel) {
            list.push(`Please choose your experience level for "${s.name}"`)
          }
        }
      }
      if (rnExtras.offersLongDurationCare) {
        const badRates = RATE_TYPES.filter((r) => rateErrors[r.value]).map((r) => `Enter an amount for "${r.label}" or untick it`)
        list.push(...badRates)
      }
    }

    if (stepKey === 'charges') {
      if (!rnExtras.minVisitFee || Number(rnExtras.minVisitFee) <= 0) {
        list.push('Please enter your minimum payment for a home visit in LKR')
      }
      if (!rnExtras.maxTravelDistance) {
        list.push('Please select how far you are willing to travel')
      }
      if (rnExtras.chargesExtraForTravel && !rnExtras.travelAllowance?.trim()) {
        list.push('Please enter your expected travel allowance (amount or per-km rate)')
      }
      if (rnExtras.providesNightServices && !rnExtras.nightExtraCharge?.trim()) {
        list.push('Please enter your extra charge for night visits')
      }
      if (!rnExtras.availableDays?.length) {
        list.push('Please select at least one available day of the week')
      }
    }

    if (stepKey === 'interview') {
      if (!rnExtras.callWhatsAppPhone?.trim()) {
        list.push('Please enter your phone / WhatsApp number so our team can call you')
      }
      if (!rnExtras.callAvailableTimes?.length) {
        list.push('Please select when you are normally available during the day for a 10-minute call')
      }
    }

    if (stepKey === 'review') {
      const allConsented = NURSING_CONSENTS.every((c) => !!rnExtras.consents?.[c.key])
      if (!allConsented) {
        list.push('Please accept all 4 clinical agreements and consents to submit your nurse application')
      }
    }

    return list
  }

  const stepProblems = getStepValidationProblems(currentStep.key)

  async function persistNurseStep({ exitAfter = false, isSaveAndExit = false, advanceToKey = null } = {}) {
    setServerIssues([])
    if (!isSaveAndExit && stepProblems.length) {
      setAttempted(true)
      setTimeout(() => bannerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
      return false
    }

    setBusy(true)
    try {
      const stepCompletionPatch = { lastStep: currentStep.key }
      if (currentStep.key === 'job') stepCompletionPatch.credentialsStepSaved = true
      if (currentStep.key === 'experience') stepCompletionPatch.experienceStepSaved = true
      if (currentStep.key === 'skills') stepCompletionPatch.servicesStepSaved = true
      if (currentStep.key === 'identity_docs') stepCompletionPatch.identityStepSaved = true
      if (currentStep.key === 'certificates') stepCompletionPatch.certificatesReviewed = true
      if (currentStep.key === 'charges') stepCompletionPatch.availabilityStepSaved = true
      if (currentStep.key === 'interview') stepCompletionPatch.interviewScheduled = true
      if (currentStep.key === 'review' && !isSaveAndExit) {
        stepCompletionPatch.applicationSubmitted = true
        stepCompletionPatch.consented = true
      }

      const nextExtras = {
        ...rnExtras,
        ...stepCompletionPatch,
        minVisitFee:
          rnExtras.minVisitFee !== '' && rnExtras.minVisitFee != null && Number(rnExtras.minVisitFee) > 0
            ? Number(rnExtras.minVisitFee)
            : null,
        services: selectedServices
          .filter((s) => Number(s.expectedRate) > 0)
          .map((s) => ({
            ...s,
            expectedRate: Number(s.expectedRate),
          })),
      }

      const validRates = Object.fromEntries(
        Object.entries(form.rates)
          .filter(([, v]) => v !== '' && Number(v) > 0)
          .map(([k, v]) => [k, Number(v)])
      )

      if (Number(nextExtras.minVisitFee) > 0 && !validRates.SESSION) {
        validRates.SESSION = Number(nextExtras.minVisitFee)
      } else if (!validRates.SESSION && selectedServices.length > 0) {
        const minSvc = Math.min(...selectedServices.map((s) => Number(s.expectedRate)).filter((n) => n > 0))
        if (Number.isFinite(minSvc) && minSvc > 0) {
          validRates.SESSION = minSvc
        }
      }

      const serviceNames = selectedServices.map((s) => s.name).join(', ').slice(0, 255)
      const qualificationValue = serviceNames || form.qualification || rnExtras.nursingQualificationType || ''

      await api('/caregivers/me/profile', {
        method: 'PUT',
        body: {
          fullName: form.fullName.trim(),
          address: form.address.trim(),
          age: num(form.age),
          experienceYears: num(form.experienceYears),
          qualification: qualificationValue,
          jobType: 'NURSING',
          rates: validRates,
          nursingOnboarding: nextExtras,
        },
      })

      if (currentStep.key === 'charges') {
        await api('/caregivers/me/availability', {
          method: 'PUT',
          body: { available: Boolean(form.available) },
        }).catch(() => {})
      }

      if (newPhoto) {
        const fd = new FormData()
        fd.append('file', newPhoto.file)
        const updatedProfile = await api('/caregivers/me/photo', { method: 'POST', form: fd })
        setPhotoUrl(updatedProfile.photoUrl)
        setNewPhoto(null)
      } else if (removePhoto && photoUrl) {
        await api('/caregivers/me/photo', { method: 'DELETE' })
        setPhotoUrl(null)
        setRemovePhoto(false)
      }

      const failed = []
      const left = {}
      for (const row of docRows) {
        const entry = newDocs[row.type]
        if (!entry?.file) continue
        try {
          const fd = new FormData()
          fd.append('file', entry.file)
          const ver = await api(`/caregivers/me/documents/${row.type}`, { method: 'POST', form: fd })
          setVerification(ver)
          setSavedDocs(Object.fromEntries((ver.documents || []).filter((d) => d.status).map((d) => [d.type, d])))
        } catch (err) {
          failed.push(`${row.label}: ${err.message}`)
          left[row.type] = entry
        }
      }

      updateRnExtras(stepCompletionPatch)
      await refresh()

      if (failed.length) {
        setNewDocs(left)
        showServerIssues(['Your profile was saved, but some documents did not upload:', ...failed])
        return false
      }

      setNewDocs({})
      setSavedNotice(true)
      setTimeout(() => setSavedNotice(false), 2500)

      if (exitAfter) {
        navigate('/')
      } else if (advanceToKey) {
        setAttempted(false)
        setServerIssues([])
        setParams({ step: advanceToKey }, { replace: true })
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }
      return true
    } catch (err) {
      showServerIssues(err.errors?.length ? err.errors : [err.message])
      return false
    } finally {
      setBusy(false)
    }
  }

  async function handleNurseSubmit(e) {
    e.preventDefault()
    const nextWizardStep = NURSING_WIZARD_STEPS[currentStepIndex + 1]
    if (nextWizardStep) {
      await persistNurseStep({ advanceToKey: nextWizardStep.key })
    } else {
      await persistNurseStep({ exitAfter: true })
    }
  }

  async function handleNurseSaveAndExit() {
    if (!form.address.trim() || !form.fullName.trim()) {
      navigate('/')
      return
    }
    await persistNurseStep({ exitAfter: true, isSaveAndExit: true })
  }

  const identityDocRows = docRows.filter((r) => r.type === 'NIC' || r.type === 'GS_LETTER')
  const certificateDocRows = docRows.filter((r) => r.type === 'NURSING_CERTIFICATE')
  const visibleErrors = [...serverIssues, ...(attempted ? stepProblems : [])]
  const initials = (form.fullName || '?').split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase()

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark"><HeartHouse /></span>
          HomeCare Connect
        </div>
        <div className="topbar-actions">
          {savedNotice && <span className="status-chip ok">✓ Progress saved</span>}
          <button type="button" className="btn-ghost" onClick={handleNurseSaveAndExit} disabled={busy}>
            Save and exit
          </button>
          <button type="button" className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>

      <main className="content wide">
        {/* Best-Practice 4-Phase & 10-Step Header (Identical layout to Caretaker wizard) */}
        <div className="wizard-header block">
          <div className="wizard-meta">
            <span className="eyebrow">
              HOME NURSE REGISTRATION · PHASE {currentStep.phaseNumber} OF 4: {currentStep.phaseTitle.toUpperCase()}
            </span>
            <span className="wizard-progress-label">
              {rnProgress.doneCount} of 10 steps completed ({rnProgress.percent}%) · Auto-saves on continue
            </span>
          </div>

          <div className="wizard-title-row">
            <div>
              <h1>Step {currentStep.stepNumber} of 10 · {currentStep.shortTitle}</h1>
              <p className="subtitle">
                Complete each step in order. You can save and continue later from your phone or computer.
              </p>
            </div>
          </div>

          {/* 4-Phase Progress Track */}
          <div className="phase-track" role="list" aria-label="Nurse onboarding phases">
            {NURSING_ONBOARDING_PHASES.map((phase) => {
              const phaseSteps = rnProgress.items.filter((i) => i.phaseId === phase.id)
              const doneInPhase = phaseSteps.filter((s) => s.isDone).length
              const isCurrentPhase = currentStep.phaseId === phase.id
              const isPhaseComplete = doneInPhase === phaseSteps.length
              return (
                <div
                  key={phase.id}
                  role="listitem"
                  className={`phase-track-item${isCurrentPhase ? ' active' : ''}${isPhaseComplete ? ' complete' : ''}`}
                >
                  <span className="phase-track-num">
                    {isPhaseComplete ? <Check /> : phase.phaseNumber}
                  </span>
                  <div className="phase-track-text">
                    <strong>{phase.title}</strong>
                    <span>{doneInPhase}/{phaseSteps.length} steps</span>
                  </div>
                </div>
              )
            })}
          </div>

          <div
            className="progress"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={rnProgress.percent}
            aria-label="Nurse onboarding progress"
          >
            <span style={{ width: `${rnProgress.percent}%` }} />
          </div>

          {/* Sequential 10-Step Pills */}
          <nav className="wizard-steps-nav" aria-label="Nurse onboarding steps">
            <span className="step-pill done" title="Step 1 completed at account creation">
              <span className="step-pill-num"><Check /></span>
              <span>1. Account</span>
            </span>
            {NURSING_WIZARD_STEPS.map((s) => {
              const item = rnProgress.items.find((i) => i.key === s.key)
              const active = s.key === currentStep.key
              const isLocked = s.stepNumber > rnProgress.maxUnlockedStepNumber
              return (
                <button
                  key={s.key}
                  type="button"
                  disabled={isLocked}
                  title={isLocked ? `Complete Step ${rnProgress.maxUnlockedStepNumber} first to unlock` : s.label}
                  onClick={() => goToStep(s.key)}
                  className={`step-pill${active ? ' active' : ''}${item?.isDone ? ' done' : ''}${isLocked ? ' locked' : ''}`}
                >
                  <span className="step-pill-num">
                    {item?.isDone ? <Check /> : isLocked ? '🔒' : s.stepNumber}
                  </span>
                  <span>{s.stepNumber}. {s.shortTitle}</span>
                </button>
              )
            })}
          </nav>
        </div>

        <input
          ref={docRef}
          type="file"
          accept={DOCUMENT_FILE_TYPES.join(',')}
          hidden
          onChange={onDocInputChange}
        />

        <div className="profile-grid">
          <form className="panel wizard-panel" onSubmit={handleNurseSubmit} noValidate>
            {visibleErrors.length > 0 && (
              <div ref={bannerRef} className="banner" role="alert" tabIndex={-1}>
                <Alert />
                <div>
                  <strong>
                    {visibleErrors.length === 1
                      ? 'Please check this detail before continuing'
                      : `Please fix these ${visibleErrors.length} details before continuing`}
                  </strong>
                  <ul>{visibleErrors.map((m) => <li key={m}>{m}</li>)}</ul>
                </div>
              </div>
            )}

            {/* ==============================================================
                STEP 2: PERSONAL INFORMATION
               ============================================================== */}
            {currentStep.key === 'about' && (
              <section id="about" className="section span-all">
                <div className="step-section-head">
                  <h2>Personal information & emergency contact</h2>
                  <p className="hint">
                    This helps our clinical verification team confirm your identity and reach a trusted contact if needed.
                  </p>
                </div>

                <div className="identity">
                  <div
                    id="photo"
                    className={`photo-block${dragging ? ' dragging' : ''}`}
                    onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
                    onDragLeave={() => setDragging(false)}
                    onDrop={(e) => { e.preventDefault(); setDragging(false); pickPhoto(e.dataTransfer.files?.[0]) }}
                  >
                    <button type="button" className="photo-btn" onClick={() => fileRef.current?.click()} aria-label="Choose a profile photo">
                      {shownPhoto ? <img src={shownPhoto} alt="" /> : <span className="initials">{initials || '?'}</span>}
                      <span className="camera">+</span>
                    </button>
                    {shownPhoto
                      ? <button type="button" className="link small" onClick={() => { setNewPhoto(null); setRemovePhoto(true) }}>Remove photo</button>
                      : <span className="photo-caption">Profile photo</span>}
                    <input
                      ref={fileRef}
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      hidden
                      onChange={(e) => { pickPhoto(e.target.files?.[0]); e.target.value = '' }}
                    />
                  </div>

                  <div className="identity-fields">
                    <Field
                      label="Full name *"
                      required
                      maxLength={150}
                      autoComplete="name"
                      placeholder="e.g. Nirmala Perera"
                      error={attempted ? nameError : ''}
                      value={form.fullName}
                      onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                    />
                    <div className="row">
                      <div className="field">
                        <label htmlFor="rn-dob">Date of birth *</label>
                        <div className="input-wrap">
                          <input
                            id="rn-dob"
                            type="date"
                            value={rnExtras.dob || ''}
                            onChange={(e) => handleDobChange(e.target.value)}
                          />
                        </div>
                      </div>

                      <Field
                        label="Age *"
                        inputMode="numeric"
                        placeholder="e.g. 32"
                        suffix="yrs"
                        error={attempted && form.age === '' ? 'Age is required' : ageError}
                        value={form.age}
                        onChange={(e) => setForm({ ...form, age: digits(e.target.value, 3) })}
                      />
                    </div>
                  </div>
                </div>

                <div className="row">
                  <div className="field">
                    <label>Gender *</label>
                    <div className="chips">
                      {['Female', 'Male', 'Prefer not to say'].map((g) => (
                        <button
                          key={g}
                          type="button"
                          className={`chip${rnExtras.gender === g ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ gender: g })}
                        >
                          {g}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="field">
                    <label>Languages spoken with patients *</label>
                    <div className="chips">
                      {['Tamil', 'Sinhala', 'English'].map((lang) => {
                        const list = rnExtras.spokenLanguages || [rnExtras.preferredLanguage || 'Tamil']
                        const active = list.includes(lang)
                        return (
                          <button
                            key={lang}
                            type="button"
                            className={`chip${active ? ' chip-active' : ''}`}
                            onClick={() => {
                              const next = active
                                ? list.filter((l) => l !== lang)
                                : [...list, lang]
                              updateRnExtras({
                                spokenLanguages: next.length ? next : [lang],
                                preferredLanguage: next[0] || lang,
                              })
                            }}
                          >
                            {lang}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                </div>

                <div className={`field${attempted && addressError ? ' has-error' : ''}`}>
                  <label htmlFor="rn-address">Residential address *</label>
                  <textarea
                    id="rn-address"
                    rows={2}
                    maxLength={300}
                    autoComplete="street-address"
                    placeholder="House number, street, town (e.g. 45 Hospital Road, Jaffna)"
                    value={form.address}
                    onChange={(e) => setForm({ ...form, address: e.target.value })}
                  />
                  <span className="hint">Our team uses this to match home visits near you. Families never see your street address.</span>
                </div>

                <div className="sub-card">
                  <strong>Emergency contact person *</strong>
                  <p className="muted small">Who should our team contact in case of an emergency while you are on a home visit?</p>
                  <div className="row">
                    <Field
                      label="Contact person name *"
                      maxLength={120}
                      placeholder="e.g. S. Perera"
                      value={rnExtras.emergencyContactName || ''}
                      onChange={(e) => updateRnExtras({ emergencyContactName: e.target.value })}
                    />
                    <Field
                      label="Emergency phone number *"
                      type="tel"
                      maxLength={20}
                      placeholder="e.g. 077 123 4567"
                      value={rnExtras.emergencyContactPhone || ''}
                      onChange={(e) => updateRnExtras({ emergencyContactPhone: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <label>Relationship</label>
                    <div className="chips">
                      {['Spouse / Partner', 'Parent', 'Sibling', 'Adult child', 'Other relative'].map((rel) => (
                        <button
                          key={rel}
                          type="button"
                          className={`chip${rnExtras.emergencyContactRelation === rel ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ emergencyContactRelation: rel })}
                        >
                          {rel}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 3: NURSING QUALIFICATIONS & PROFESSIONAL REGISTRATION
               ============================================================== */}
            {currentStep.key === 'job' && (
              <section id="job" className="section span-all">
                <div className="step-section-head">
                  <h2>Nursing qualifications & professional registration</h2>
                  <p className="hint">
                    Tell us about your nursing qualification, training school, and SLNC / SLMC registration status.
                  </p>
                </div>

                <div className="field">
                  <label>Highest nursing qualification *</label>
                  <div className="chips">
                    {NURSING_QUALIFICATION_TYPES.map((q) => (
                      <button
                        key={q}
                        type="button"
                        className={`chip${rnExtras.nursingQualificationType === q ? ' chip-active' : ''}`}
                        onClick={() => updateRnExtras({ nursingQualificationType: q })}
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="row">
                  <Field
                    label="Nursing school / university (optional)"
                    maxLength={160}
                    placeholder="e.g. School of Nursing, Jaffna / University of Jaffna"
                    value={rnExtras.trainingInstitute || ''}
                    onChange={(e) => updateRnExtras({ trainingInstitute: e.target.value })}
                  />
                  <Field
                    label="Year of graduation (optional)"
                    inputMode="numeric"
                    placeholder="e.g. 2019"
                    value={rnExtras.graduationYear || ''}
                    onChange={(e) => updateRnExtras({ graduationYear: digits(e.target.value, 4) })}
                  />
                </div>

                <div className="sub-card">
                  <strong>Professional council registration *</strong>
                  <p className="muted small">Select your registering council and enter your registration number (if already issued):</p>
                  <div className="field">
                    <label>Registration council / body *</label>
                    <div className="chips">
                      {NURSING_REGISTRATION_BODIES.map((body) => (
                        <button
                          key={body}
                          type="button"
                          className={`chip${rnExtras.registrationBody === body ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ registrationBody: body })}
                        >
                          {body}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="row">
                    <Field
                      label="SLNC / SLMC registration number (optional if pending)"
                      maxLength={80}
                      placeholder="e.g. SLNC-48291"
                      value={rnExtras.registrationNumber || ''}
                      onChange={(e) => updateRnExtras({ registrationNumber: e.target.value })}
                    />
                    <div className="field">
                      <label htmlFor="rn-reg-valid">Registration valid until (optional)</label>
                      <div className="input-wrap">
                        <input
                          id="rn-reg-valid"
                          type="date"
                          value={rnExtras.registrationValidUntil || ''}
                          onChange={(e) => updateRnExtras({ registrationValidUntil: e.target.value })}
                        />
                      </div>
                    </div>
                  </div>
                </div>

                <div className="field">
                  <label>Clinical ward / department experience (Select all that apply)</label>
                  <div className="chips">
                    {NURSING_CLINICAL_AREAS.map((area) => {
                      const list = rnExtras.clinicalAreas || []
                      const active = list.includes(area)
                      return (
                        <button
                          key={area}
                          type="button"
                          className={`chip${active ? ' chip-active' : ''}`}
                          onClick={() => {
                            const next = active ? list.filter((a) => a !== area) : [...list, area]
                            updateRnExtras({ clinicalAreas: next })
                          }}
                        >
                          {area}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <div className="conditional-box">
                  <div className="conditional-head">
                    <Shield />
                    <strong>{ROLE_DYNAMIC_REQUIREMENTS.NURSING.title}</strong>
                  </div>
                  <ul className="conditional-list">
                    {ROLE_DYNAMIC_REQUIREMENTS.NURSING.items.map((item) => (
                      <li key={item}><Check /> <span>{item}</span></li>
                    ))}
                  </ul>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 4: EMPLOYMENT EXPERIENCE & REFERENCES (OPTIONAL)
               ============================================================== */}
            {currentStep.key === 'experience' && (
              <section id="experience" className="section span-all">
                <div className="step-section-head">
                  <h2>Employment experience & references (Optional)</h2>
                  <p className="hint">
                    Both hospital/clinic work history and references are <strong>optional</strong>. Share your clinical background or click <strong>Save & continue to Step 5 →</strong>.
                  </p>
                </div>

                <div className="field">
                  <label>Current employment status</label>
                  <div className="chips">
                    {NURSING_WORK_STATUSES.map((st) => (
                      <button
                        key={st}
                        type="button"
                        className={`chip${rnExtras.currentWorkStatus === st ? ' chip-active' : ''}`}
                        onClick={() => updateRnExtras({ currentWorkStatus: st })}
                      >
                        {st}
                      </button>
                    ))}
                  </div>
                </div>

                <Field
                  label="Total years of nursing experience (optional)"
                  inputMode="numeric"
                  placeholder="e.g. 6"
                  suffix="years"
                  error={expError}
                  value={form.experienceYears}
                  onChange={(e) => setForm({ ...form, experienceYears: digits(e.target.value, 2) })}
                />

                <div className="work-history-list">
                  {(rnExtras.workHistory || [{ ...EMPTY_NURSING_WORK_ENTRY }]).map((entry, idx) => (
                    <div key={idx} className="sub-card">
                      <div className="summary-head">
                        <strong>Hospital / clinic experience #{idx + 1} (optional)</strong>
                        {(rnExtras.workHistory?.length ?? 0) > 1 && (
                          <button type="button" className="link small" onClick={() => removeWorkEntry(idx)}>
                            Remove
                          </button>
                        )}
                      </div>
                      <div className="row">
                        <Field
                          label="Hospital / clinic / institution (optional)"
                          maxLength={150}
                          placeholder="e.g. Teaching Hospital Jaffna / Northern Central Hospital"
                          value={entry.org || ''}
                          onChange={(e) => updateWorkEntry(idx, 'org', e.target.value)}
                        />
                        <Field
                          label="Role / designation (optional)"
                          maxLength={120}
                          placeholder="e.g. Staff Nurse / Ward Sister"
                          value={entry.title || ''}
                          onChange={(e) => updateWorkEntry(idx, 'title', e.target.value)}
                        />
                      </div>
                      <div className="row">
                        <Field
                          label="Ward / department (optional)"
                          maxLength={100}
                          placeholder="e.g. Surgical Ward / ICU / Outpatients"
                          value={entry.department || ''}
                          onChange={(e) => updateWorkEntry(idx, 'department', e.target.value)}
                        />
                        <Field
                          label="Duration (optional)"
                          maxLength={100}
                          placeholder="e.g. 2020 – Present (5 years)"
                          value={entry.duration || ''}
                          onChange={(e) => updateWorkEntry(idx, 'duration', e.target.value)}
                        />
                      </div>
                      <Field
                        label="Key clinical responsibilities (optional)"
                        maxLength={255}
                        placeholder="e.g. Post-operative wound care, urinary catheterization, IV cannulation, and patient monitoring"
                        value={entry.responsibilities || ''}
                        onChange={(e) => updateWorkEntry(idx, 'responsibilities', e.target.value)}
                      />
                    </div>
                  ))}

                  <button type="button" className="btn-ghost small-btn" onClick={addWorkEntry}>
                    + Add another hospital / clinic experience
                  </button>
                </div>

                <div className="sub-card">
                  <strong>Clinical reference contact (optional)</strong>
                  <p className="muted small">A doctor, nursing officer, matron, or supervisor who can vouch for your clinical work:</p>
                  <div className="row">
                    <Field
                      label="Reference name (optional)"
                      maxLength={150}
                      placeholder="e.g. Dr. M. Ratnam / Nursing Sister K. Devi"
                      value={rnExtras.referenceName || ''}
                      onChange={(e) => updateRnExtras({ referenceName: e.target.value })}
                    />
                    <Field
                      label="Reference phone (optional)"
                      type="tel"
                      maxLength={20}
                      placeholder="e.g. 077 987 6543"
                      value={rnExtras.referencePhone || ''}
                      onChange={(e) => updateRnExtras({ referencePhone: e.target.value })}
                    />
                  </div>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 5: YOUR HOME NURSING SERVICES & RATES
               ============================================================== */}
            {currentStep.key === 'skills' && (
              <section id="skills" className="section span-all">
                <div className="nurse-services-header">
                  <div>
                    <span className="eyebrow">NURSE ONBOARDING · SERVICES</span>
                    <h2>Which services can you provide?</h2>
                  </div>
                  <StatusChip
                    label={rnExtras.servicesStepSaved ? 'Saved' : 'Draft'}
                    tone={rnExtras.servicesStepSaved ? 'ok' : 'wait'}
                  />
                </div>
                <p className="hint">
                  Select the home nursing services you are qualified and competent to perform, then enter your expected payment for each visit.
                  The experience and pricing information is self-reported until verified.
                </p>

                <div className="nurse-service-list">
                  {HOME_NURSING_SERVICES.map((item) => {
                    const selected = findService(item.code)
                    const isChecked = Boolean(selected)
                    return (
                      <div key={item.code} className={`nurse-service-card${isChecked ? ' selected' : ''}`}>
                        <label className="nurse-service-top">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => toggleNursingService(item)}
                          />
                          <div className="nurse-service-title-wrap">
                            <strong>{item.name}</strong>
                            <span className="nurse-service-cat">{item.category}</span>
                          </div>
                          {isChecked && selected.expectedRate && Number(selected.expectedRate) > 0 && (
                            <span className="nurse-service-price-pill">
                              {formatMoney(Number(selected.expectedRate))}
                            </span>
                          )}
                        </label>

                        {isChecked && (
                          <div className="nurse-service-body">
                            <div className="row">
                              <Field
                                label={`Your expected payment (${CURRENCY}) *`}
                                inputMode="numeric"
                                placeholder={`e.g. ${item.suggestedRate}`}
                                prefix={CURRENCY}
                                value={selected.expectedRate || ''}
                                onChange={(e) => updateNursingServiceField(item.code, 'expectedRate', digits(e.target.value, 7))}
                              />

                              <div className="field">
                                <label>Expected duration</label>
                                <div className="chips">
                                  {NURSING_DURATIONS.map((dur) => (
                                    <button
                                      key={dur}
                                      type="button"
                                      className={`chip${selected.duration === dur ? ' chip-active' : ''}`}
                                      onClick={() => updateNursingServiceField(item.code, 'duration', dur)}
                                    >
                                      {dur}
                                    </button>
                                  ))}
                                </div>
                              </div>
                            </div>

                            <div className="field">
                              <label>Experience with this service *</label>
                              <div className="nurse-exp-options">
                                {NURSING_SERVICE_EXPERIENCE_LEVELS.map((lvl) => (
                                  <button
                                    key={lvl}
                                    type="button"
                                    className={`nurse-exp-btn${selected.experienceLevel === lvl ? ' active' : ''}`}
                                    onClick={() => updateNursingServiceField(item.code, 'experienceLevel', lvl)}
                                  >
                                    <span className="nurse-exp-radio" />
                                    <span>{lvl}</span>
                                  </button>
                                ))}
                              </div>
                            </div>

                            <div className="row">
                              <div className="field">
                                <label>Number of times performed</label>
                                <div className="chips">
                                  {NURSING_TIMES_PERFORMED.map((cnt) => (
                                    <button
                                      key={cnt}
                                      type="button"
                                      className={`chip${selected.timesPerformed === cnt ? ' chip-active' : ''}`}
                                      onClick={() => updateNursingServiceField(item.code, 'timesPerformed', cnt)}
                                    >
                                      {cnt}
                                    </button>
                                  ))}
                                </div>
                              </div>

                              <div className="field">
                                <label>Last performed</label>
                                <div className="chips">
                                  {NURSING_LAST_PERFORMED.map((rec) => (
                                    <button
                                      key={rec}
                                      type="button"
                                      className={`chip${selected.lastPerformed === rec ? ' chip-active' : ''}`}
                                      onClick={() => updateNursingServiceField(item.code, 'lastPerformed', rec)}
                                    >
                                      {rec}
                                    </button>
                                  ))}
                                </div>
                              </div>
                            </div>

                            <div className="nurse-equip-row">
                              <label className="consent-row compact">
                                <input
                                  type="checkbox"
                                  checked={Boolean(selected.bringsEquipment)}
                                  onChange={(e) => updateNursingServiceField(item.code, 'bringsEquipment', e.target.checked)}
                                />
                                <span>
                                  <strong>Brings equipment?</strong>
                                  <em className="step-sub">I bring the necessary reusable clinical instruments (e.g. BP monitor, glucometer, dressing forceps)</em>
                                </span>
                              </label>

                              <label className="consent-row compact">
                                <input
                                  type="checkbox"
                                  checked={Boolean(selected.providesConsumables)}
                                  onChange={(e) => updateNursingServiceField(item.code, 'providesConsumables', e.target.checked)}
                                />
                                <span>
                                  <strong>Provides consumables?</strong>
                                  <em className="step-sub">I can bring sterile gauze, gloves, strips, or catheters if the family does not have them</em>
                                </span>
                              </label>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>

                <div className="nurse-selection-footer">
                  <strong>
                    {selectedServices.length} {selectedServices.length === 1 ? 'service' : 'services'} selected.
                  </strong>{' '}
                  <span>These selections require verification before services can be activated.</span>
                </div>

                <div className="sub-card">
                  <label className="consent-row compact">
                    <input
                      type="checkbox"
                      checked={Boolean(rnExtras.offersLongDurationCare)}
                      onChange={(e) => updateRnExtras({ offersLongDurationCare: e.target.checked })}
                    />
                    <span>
                      <strong>I also offer long-duration bedside nursing shifts (optional)</strong>
                      <em className="step-sub">
                        Only tick this if you also want to list general hourly, full-day, or 24-hour nursing rates.
                      </em>
                    </span>
                  </label>

                  {rnExtras.offersLongDurationCare && (
                    <div className="rates" style={{ marginTop: 12 }}>
                      {RATE_TYPES.map(({ value, label, hint, placeholder }) => {
                        const on = value in form.rates
                        const err = attempted ? rateErrors[value] : ''
                        return (
                          <div key={value} className={`rate-row${on ? ' on' : ''}${err ? ' has-error' : ''}`}>
                            <label className="rate-check">
                              <input type="checkbox" checked={on} onChange={() => toggleRate(value)} />
                              <span className="rate-box" aria-hidden="true"><Check /></span>
                              <span className="rate-text"><strong>{label}</strong><em>{hint}</em></span>
                            </label>
                            {on && (
                              <div className="rate-amount">
                                <div className="input-wrap">
                                  <span className="affix prefix">{CURRENCY}</span>
                                  <input
                                    inputMode="numeric"
                                    placeholder={placeholder}
                                    aria-label={`${label} charge in ${CURRENCY}`}
                                    value={form.rates[value]}
                                    onChange={setRate(value)}
                                  />
                                </div>
                                {err && <span className="error-text">{err}</span>}
                              </div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 6: IDENTITY DOCUMENTS
               ============================================================== */}
            {currentStep.key === 'identity_docs' && (
              <section id="identity_docs" className="section span-all">
                <div className="step-section-head">
                  <h2>Identity verification documents</h2>
                  <p className="hint">
                    Upload a clear photo or scan of your National Identity Card (NIC). PDF, JPG, PNG or WEBP up to 5 MB.
                  </p>
                </div>

                <ul className="doc-list">
                  {(identityDocRows.length ? identityDocRows : docRows).map((r) => {
                    const saved = savedDocs[r.type]
                    const queued = newDocs[r.type]
                    const status = saved?.status
                    return (
                      <li key={r.type} className="doc-row">
                        <div className="doc-main">
                          <strong>{r.label} {!r.required && <span className="optional">Optional</span>}</strong>
                          <span className="muted small">{r.hint}</span>
                          {status === 'REJECTED' && saved.rejectReason && (
                            <span className="error-text small">Rejected: {saved.rejectReason}</span>
                          )}
                          {queued && <span className="small">Ready to upload: <strong>{queued.file.name}</strong></span>}
                        </div>
                        <div className="doc-side">
                          {queued ? (
                            <>
                              <StatusChip label="Will upload on save" tone="wait" />
                              <button type="button" className="link" onClick={() => removeQueuedDoc(r.type)}>Cancel</button>
                            </>
                          ) : (
                            <>
                              <StatusChip label={status ? DOC_LABEL[status] : 'Not uploaded'} tone={status ? DOC_TONE[status] : 'off'} />
                              {status !== 'APPROVED' && (
                                <button type="button" className="btn-ghost small-btn" onClick={() => openDocPicker(r.type)}>
                                  {status ? 'Replace' : 'Choose file'}
                                </button>
                              )}
                            </>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </section>
            )}

            {/* ==============================================================
                STEP 7: NURSING CERTIFICATES & TRAINING
               ============================================================== */}
            {currentStep.key === 'certificates' && (
              <section id="certificates" className="section span-all">
                <div className="step-section-head">
                  <h2>Nursing qualification certificate & additional training</h2>
                  <p className="hint">
                    Upload your nursing diploma, degree certificate, or SLNC/SLMC registration certificate so our clinical team can verify your credentials.
                  </p>
                </div>

                {certificateDocRows.length > 0 && (
                  <ul className="doc-list">
                    {certificateDocRows.map((r) => {
                      const saved = savedDocs[r.type]
                      const queued = newDocs[r.type]
                      const status = saved?.status
                      return (
                        <li key={r.type} className="doc-row">
                          <div className="doc-main">
                            <strong>{r.label} {!r.required && <span className="optional">Optional</span>}</strong>
                            <span className="muted small">{r.hint}</span>
                            {status === 'REJECTED' && saved.rejectReason && (
                              <span className="error-text small">Rejected: {saved.rejectReason}</span>
                            )}
                            {queued && <span className="small">Ready to upload: <strong>{queued.file.name}</strong></span>}
                          </div>
                          <div className="doc-side">
                            {queued ? (
                              <>
                                <StatusChip label="Will upload on save" tone="wait" />
                                <button type="button" className="link" onClick={() => removeQueuedDoc(r.type)}>Cancel</button>
                              </>
                            ) : (
                              <>
                                <StatusChip label={status ? DOC_LABEL[status] : 'Not uploaded'} tone={status ? DOC_TONE[status] : 'off'} />
                                {status !== 'APPROVED' && (
                                  <button type="button" className="btn-ghost small-btn" onClick={() => openDocPicker(r.type)}>
                                    {status ? 'Replace' : 'Choose file'}
                                  </button>
                                )}
                              </>
                            )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                )}

                <Field
                  label="Additional clinical training or certifications (optional)"
                  maxLength={300}
                  placeholder="e.g. BLS / ACLS Certified, Advanced Wound Care Workshop, ICU Special Training"
                  value={rnExtras.additionalTraining || ''}
                  onChange={(e) => updateRnExtras({ additionalTraining: e.target.value })}
                />
              </section>
            )}

            {/* ==============================================================
                STEP 8: AVAILABILITY, TRAVEL & VISIT PREFERENCES
               ============================================================== */}
            {currentStep.key === 'charges' && (
              <section id="charges" className="section span-all">
                <div className="step-section-head">
                  <h2>Availability, travel & home visit rules</h2>
                  <p className="hint">
                    Beyond individual procedure rates, tell us your minimum home visit charge, travel radius, urgent/night visit availability, and consumables policy.
                  </p>
                </div>

                <div className="row">
                  <Field
                    label={`What is your minimum payment for a home visit? (${CURRENCY}) *`}
                    inputMode="numeric"
                    prefix={CURRENCY}
                    placeholder="e.g. 1500"
                    hint="Even for a short single check-up, this is your minimum payout per visit"
                    value={rnExtras.minVisitFee ?? ''}
                    onChange={(e) => {
                      const val = digits(e.target.value, 7)
                      updateRnExtras({ minVisitFee: val })
                      syncServicesToForm(selectedServices, val)
                    }}
                  />

                  <div className="field">
                    <label>How far are you willing to travel? *</label>
                    <div className="chips">
                      {NURSING_TRAVEL_DISTANCES.map((dist) => (
                        <button
                          key={dist}
                          type="button"
                          className={`chip${rnExtras.maxTravelDistance === dist ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ maxTravelDistance: dist })}
                        >
                          {dist}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="sub-card">
                  <div className="row">
                    <div className="field">
                      <label>Do you charge extra for travelling?</label>
                      <div className="chips">
                        {[
                          { label: 'Yes', val: true },
                          { label: 'No (included in visit rate)', val: false },
                        ].map((opt) => (
                          <button
                            key={opt.label}
                            type="button"
                            className={`chip${Boolean(rnExtras.chargesExtraForTravel) === opt.val ? ' chip-active' : ''}`}
                            onClick={() => updateRnExtras({ chargesExtraForTravel: opt.val })}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {rnExtras.chargesExtraForTravel && (
                      <Field
                        label="What is your expected travel allowance? *"
                        maxLength={100}
                        placeholder="e.g. LKR 500 flat or LKR 80 per km beyond 5 km"
                        value={rnExtras.travelAllowance || ''}
                        onChange={(e) => updateRnExtras({ travelAllowance: e.target.value })}
                      />
                    )}
                  </div>
                </div>

                <div className="sub-card">
                  <div className="row">
                    <div className="field">
                      <label>Do you provide night services?</label>
                      <div className="chips">
                        {[
                          { label: 'Yes', val: true },
                          { label: 'No (daytime / evening only)', val: false },
                        ].map((opt) => (
                          <button
                            key={opt.label}
                            type="button"
                            className={`chip${Boolean(rnExtras.providesNightServices) === opt.val ? ' chip-active' : ''}`}
                            onClick={() => updateRnExtras({ providesNightServices: opt.val })}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {rnExtras.providesNightServices && (
                      <Field
                        label="Extra charge for night visits (LKR) *"
                        maxLength={100}
                        placeholder="e.g. LKR 1,000 extra after 8 PM"
                        value={rnExtras.nightExtraCharge || ''}
                        onChange={(e) => updateRnExtras({ nightExtraCharge: e.target.value })}
                      />
                    )}
                  </div>
                </div>

                <div className="row">
                  <div className="field">
                    <label>Are you available for urgent bookings?</label>
                    <div className="chips">
                      {[
                        { label: 'Yes', val: true },
                        { label: 'No', val: false },
                      ].map((opt) => (
                        <button
                          key={opt.label}
                          type="button"
                          className={`chip${Boolean(rnExtras.availableForUrgent) === opt.val ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ availableForUrgent: opt.val })}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="field">
                    <label>Can you perform multiple services during one visit?</label>
                    <div className="chips">
                      {[
                        { label: 'Yes', val: true },
                        { label: 'No', val: false },
                      ].map((opt) => (
                        <button
                          key={opt.label}
                          type="button"
                          className={`chip${Boolean(rnExtras.multipleServicesPerVisit) === opt.val ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ multipleServicesPerVisit: opt.val })}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="row">
                  <div className="field">
                    <label>Do you provide medical consumables?</label>
                    <div className="chips">
                      {[
                        { label: 'Yes (charged at cost / included)', val: true },
                        { label: 'No (family provides)', val: false },
                      ].map((opt) => (
                        <button
                          key={opt.label}
                          type="button"
                          className={`chip${Boolean(rnExtras.providesMedicalConsumables) === opt.val ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ providesMedicalConsumables: opt.val })}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="field">
                    <label>Are you available for recurring visits?</label>
                    <div className="chips">
                      {[
                        { label: 'Yes', val: true },
                        { label: 'No', val: false },
                      ].map((opt) => (
                        <button
                          key={opt.label}
                          type="button"
                          className={`chip${Boolean(rnExtras.availableForRecurring) === opt.val ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ availableForRecurring: opt.val })}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="field">
                  <label>Available days of the week *</label>
                  <div className="chips">
                    {WEEK_DAYS.map((day) => {
                      const days = rnExtras.availableDays || []
                      const active = days.includes(day)
                      return (
                        <button
                          key={day}
                          type="button"
                          className={`chip${active ? ' chip-active' : ''}`}
                          onClick={() => {
                            const next = active ? days.filter((d) => d !== day) : [...days, day]
                            updateRnExtras({ availableDays: next })
                          }}
                        >
                          {day}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <div className="row">
                  <div className="field">
                    <label>Preferred time of day for home visits</label>
                    <div className="chips">
                      {SHIFT_OPTIONS.map((sh) => (
                        <button
                          key={sh}
                          type="button"
                          className={`chip${rnExtras.preferredShift === sh ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ preferredShift: sh })}
                        >
                          {sh}
                        </button>
                      ))}
                    </div>
                  </div>

                  <Field
                    label="Preferred service areas / towns (optional)"
                    maxLength={200}
                    placeholder="e.g. Jaffna, Nallur, Chavakachcheri"
                    value={rnExtras.preferredLocations || ''}
                    onChange={(e) => updateRnExtras({ preferredLocations: e.target.value })}
                  />
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 9: 10-MINUTE WHATSAPP / PHONE CALL WITH HOMECARE TEAM
               ============================================================== */}
            {currentStep.key === 'interview' && (
              <section id="interview" className="section span-all">
                <div className="step-section-head">
                  <h2>10-minute WhatsApp / phone call with our HomeCare team</h2>
                  <p className="hint">
                    Our clinical coordinator will give you a quick <strong>10-minute phone or WhatsApp call</strong> during the time of day you are normally free to confirm your nursing services and activate your profile.
                  </p>
                </div>

                <div className="meet-card">
                  <div className="meet-card-head">
                    <div className="meet-badge">
                      <span className="meet-dot" />
                      <strong>WhatsApp / Phone · 10-Minute Clinical Verification Call</strong>
                    </div>
                    <StatusChip
                      label={rnExtras.interviewScheduled ? 'Availability saved' : 'Share your availability'}
                      tone={rnExtras.interviewScheduled ? 'ok' : 'wait'}
                    />
                  </div>
                  <p className="small muted">What we cover on this quick 10-minute call:</p>
                  <ul className="conditional-list">
                    <li><Check /> <span>2-minute introduction with our HomeCare Connect nursing coordinator</span></li>
                    <li><Check /> <span>5-minute review of the home nursing procedures and rates you selected</span></li>
                    <li><Check /> <span>3 minutes for your questions on prescriptions, safety, and payouts</span></li>
                  </ul>
                </div>

                <div className="row">
                  <Field
                    label="Your WhatsApp / phone number *"
                    type="tel"
                    maxLength={20}
                    placeholder="e.g. 077 123 4567"
                    value={rnExtras.callWhatsAppPhone || ''}
                    onChange={(e) => updateRnExtras({ callWhatsAppPhone: e.target.value })}
                  />

                  <div className="field">
                    <label>How should we contact you? *</label>
                    <div className="chips">
                      {['WhatsApp Call', 'Normal Phone Call', 'Either WhatsApp or Phone'].map((method) => (
                        <button
                          key={method}
                          type="button"
                          className={`chip${(rnExtras.callMethod || 'WhatsApp Call') === method ? ' chip-active' : ''}`}
                          onClick={() => updateRnExtras({ callMethod: method })}
                        >
                          {method}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="field">
                  <label>What time of day are you normally free for a 10-minute call? * (Select all that apply)</label>
                  <div className="chips">
                    {CALL_TIME_SLOTS.map((slot) => {
                      const selected = rnExtras.callAvailableTimes || []
                      const active = selected.includes(slot)
                      return (
                        <button
                          key={slot}
                          type="button"
                          className={`chip${active ? ' chip-active' : ''}`}
                          onClick={() => {
                            const next = active ? selected.filter((s) => s !== slot) : [...selected, slot]
                            updateRnExtras({ callAvailableTimes: next })
                          }}
                        >
                          {slot}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <Field
                  label="Any specific notes on your shift / call availability? (optional)"
                  maxLength={160}
                  placeholder="e.g. Free after hospital duty at 4 PM, or anytime on Sundays"
                  value={rnExtras.callNotes || ''}
                  onChange={(e) => updateRnExtras({ callNotes: e.target.value })}
                />
              </section>
            )}

            {/* ==============================================================
                STEP 10: REVIEW, AGREEMENTS & SUBMIT
               ============================================================== */}
            {currentStep.key === 'review' && (
              <section id="review" className="section span-all">
                <div className="step-section-head">
                  <h2>Review, clinical agreements & submission</h2>
                  <p className="hint">
                    Review your complete Home Nurse onboarding summary below. Click <strong>Edit</strong> on any card to make changes before submitting.
                  </p>
                </div>

                <div className="review-summary-grid">
                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>2. Personal information</strong>
                      <button type="button" className="link small" onClick={() => goToStep('about')}>Edit</button>
                    </div>
                    <p className="small"><strong>Name:</strong> {form.fullName || 'Not set'}</p>
                    <p className="small"><strong>Age & Gender:</strong> {form.age ? `${form.age} yrs` : '—'} · {rnExtras.gender || '—'}</p>
                    <p className="small"><strong>Languages:</strong> {(rnExtras.spokenLanguages || [rnExtras.preferredLanguage]).join(', ')}</p>
                    <p className="small"><strong>Address:</strong> {form.address || 'Not set'}</p>
                    <p className="small"><strong>Emergency:</strong> {rnExtras.emergencyContactName} ({rnExtras.emergencyContactPhone})</p>
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>3 & 4. Nursing credentials & experience</strong>
                      <button type="button" className="link small" onClick={() => goToStep('job')}>Edit</button>
                    </div>
                    <p className="small"><strong>Qualification:</strong> {rnExtras.nursingQualificationType || 'Not set'}</p>
                    <p className="small">
                      <strong>Registration:</strong> {rnExtras.registrationBody || '—'}
                      {rnExtras.registrationNumber ? ` (${rnExtras.registrationNumber})` : ''}
                    </p>
                    <p className="small"><strong>Experience:</strong> {form.experienceYears ? `${form.experienceYears} years` : 'Optional / Not specified'}</p>
                    {rnExtras.clinicalAreas?.length > 0 && (
                      <p className="small"><strong>Clinical areas:</strong> {rnExtras.clinicalAreas.join(', ')}</p>
                    )}
                  </div>

                  <div className="summary-card" style={{ gridColumn: '1 / -1' }}>
                    <div className="summary-head">
                      <strong>5. Home nursing services & expected payouts ({selectedServices.length})</strong>
                      <button type="button" className="link small" onClick={() => goToStep('skills')}>Edit</button>
                    </div>
                    {selectedServices.length > 0 ? (
                      <div className="nurse-summary-services">
                        {selectedServices.map((s) => (
                          <div key={s.code} className="nurse-summary-service-row">
                            <div>
                              <strong>{s.name}</strong> <span className="muted small">({s.category} · {s.duration})</span>
                              <div className="muted small">
                                {s.experienceLevel} · Performed {s.timesPerformed} times · Last: {s.lastPerformed}
                                {s.bringsEquipment ? ' · Brings equipment' : ''}
                                {s.providesConsumables ? ' · Provides consumables' : ''}
                              </div>
                            </div>
                            <strong>{formatMoney(Number(s.expectedRate || 0))}</strong>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="small muted">No services selected yet</p>
                    )}
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>8. Visit fees, travel & night rules</strong>
                      <button type="button" className="link small" onClick={() => goToStep('charges')}>Edit</button>
                    </div>
                    <p className="small">
                      <strong>Min home visit fee:</strong>{' '}
                      {rnExtras.minVisitFee ? formatMoney(Number(rnExtras.minVisitFee)) : 'Not set'}
                    </p>
                    <p className="small">
                      <strong>Travel distance:</strong> {rnExtras.maxTravelDistance || 'Not set'}
                      {rnExtras.chargesExtraForTravel ? ` (Extra: ${rnExtras.travelAllowance})` : ' (No extra travel charge)'}
                    </p>
                    <p className="small">
                      <strong>Night visits:</strong>{' '}
                      {rnExtras.providesNightServices ? `Yes (${rnExtras.nightExtraCharge})` : 'No'} ·{' '}
                      <strong>Urgent:</strong> {rnExtras.availableForUrgent ? 'Yes' : 'No'}
                    </p>
                    <p className="small">
                      <strong>Days:</strong> {(rnExtras.availableDays || []).join(', ')}
                    </p>
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>6, 7 & 9. Documents & 10-min call</strong>
                      <button type="button" className="link small" onClick={() => goToStep('interview')}>Edit</button>
                    </div>
                    <div className="chips" style={{ marginBottom: 8 }}>
                      {docRows.map((r) => {
                        const saved = savedDocs[r.type]
                        const queued = newDocs[r.type]
                        return (
                          <span key={r.type} className="chip">
                            {r.label}: <strong>{queued ? 'Queued' : saved ? DOC_LABEL[saved.status] : 'Upload later'}</strong>
                          </span>
                        )
                      })}
                    </div>
                    <p className="small"><strong>10-Min Call:</strong> {rnExtras.callMethod || 'WhatsApp Call'} ({rnExtras.callWhatsAppPhone || 'Not set'})</p>
                    <p className="small"><strong>Normally free:</strong> {(rnExtras.callAvailableTimes || []).join(', ') || 'Not set'}</p>
                  </div>
                </div>

                <div className="consent-box">
                  <strong>Clinical agreements and consent *</strong>
                  <p className="muted small">Please tick all 4 agreements below to submit your Home Nurse application:</p>
                  {NURSING_CONSENTS.map((item) => {
                    const checked = Boolean(rnExtras.consents?.[item.key])
                    return (
                      <label key={item.key} className="consent-row">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            const nextConsents = { ...(rnExtras.consents || {}), [item.key]: e.target.checked }
                            const allDone = NURSING_CONSENTS.every((c) => !!nextConsents[c.key])
                            updateRnExtras({ consents: nextConsents, consented: allDone })
                          }}
                        />
                        <span>
                          <strong>{item.label}</strong>
                          <em className="step-sub">{item.desc}</em>
                        </span>
                      </label>
                    )
                  })}
                </div>
              </section>
            )}

            <div className="actions wizard-actions">
              <div className="wizard-actions-left">
                {currentStepIndex > 0 ? (
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={busy}
                    onClick={() => goToStep(NURSING_WIZARD_STEPS[currentStepIndex - 1].key)}
                  >
                    ← Previous step
                  </button>
                ) : (
                  <Link className="btn-ghost" to="/">Back to dashboard</Link>
                )}
                <button type="button" className="link" disabled={busy} onClick={handleNurseSaveAndExit}>
                  Save and exit
                </button>
              </div>

              <button className="btn-primary" disabled={busy}>
                {busy && <span className="spinner" />}
                {busy
                  ? 'Saving…'
                  : currentStep.key === 'review'
                  ? 'Submit nurse application'
                  : `Save & continue to Step ${currentStep.stepNumber + 1} →`}
              </button>
            </div>
          </form>

          <ProfilePreview
            name={form.fullName.trim()}
            age={form.age}
            years={form.experienceYears}
            qualification={
              selectedServices.length > 0
                ? selectedServices.map((s) => s.name).join(', ')
                : form.qualification
            }
            photo={shownPhoto}
            jobType={form.jobType}
            rates={form.rates}
          />
        </div>
      </main>
    </div>
  )
}
