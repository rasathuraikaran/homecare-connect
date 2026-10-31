import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Field from '../components/Field'
import StatusChip from '../components/StatusChip'
import { Alert, Check, HeartHouse, Shield } from '../components/Icons'
import { JOB_ICONS } from '../components/jobIcons'
import { JOBS, jobLabel } from '../constants/jobs'
import { CURRENCY, RATE_TYPES, formatMoney } from '../constants/rates'
import { DOCUMENT_FILE_TYPES, DOC_LABEL, DOC_TONE, MAX_DOCUMENT, OVERALL_LABEL, OVERALL_TONE } from '../constants/verification'
import {
  CALL_TIME_SLOTS,
  CARETAKER_ONBOARDING_PHASES,
  CARETAKER_SUBCATEGORIES,
  CARE_SERVICES,
  EDUCATION_LEVELS,
  EXPERIENCE_SOURCES,
  NURSING_QUALIFICATION_TYPES,
  NURSING_REGISTRATION_BODIES,
  NURSING_TRAVEL_DISTANCES,
  ONBOARDING_CONSENTS,
  ROLE_DYNAMIC_REQUIREMENTS,
  SHIFT_OPTIONS,
  START_AVAILABILITY_OPTIONS,
  WEEK_DAYS,
  WIZARD_STEPS,
  caretakerCompleteness,
  loadNursingExtras,
  loadOnboardingExtras,
  saveNursingExtras,
  saveOnboardingExtras,
} from '../constants/completeness'
import NurseOnboardingWizard from '../components/NurseOnboardingWizard'
import ProfilePreview from '../components/ProfilePreview'

const MAX_PHOTO = 2 * 1024 * 1024
const TYPES = ['image/jpeg', 'image/png', 'image/webp']
const SUGGESTIONS = ['Nursing diploma', 'First aid certified', 'Elderly care certificate', 'Dementia care', 'Physiotherapy assistant']

const digits = (v, max) => v.replace(/\D/g, '').slice(0, max)

const FOCUS_ALIAS = {
  about: 'about',
  photo: 'about',
  job: 'job',
  qualifications: 'skills',
  skills: 'skills',
  experience: 'experience',
  documents: 'identity_docs',
  identity_docs: 'identity_docs',
  certificates: 'certificates',
  charges: 'charges',
  interview: 'interview',
  review: 'review',
}

const EMPTY_WORK_ENTRY = { org: '', title: '', duration: '', responsibilities: '', reasonForLeaving: '' }

export default function ProfilePage() {
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const focus = params.get('focus')
  const requestedJobType = params.get('jobType')
  const requestedStep = FOCUS_ALIAS[params.get('step') || params.get('focus')] || null

  const firstTime = !user.profileCompleted
  const userKey = user?.id ?? user?.email
  const fileRef = useRef(null)
  const docRef = useRef(null)
  const docTarget = useRef(null)
  const bannerRef = useRef(null)

  const [form, setForm] = useState({
    fullName: user.fullName || '',
    age: '',
    experienceYears: '',
    qualification: '',
    jobType: requestedJobType || '',
    address: '',
    rates: {},
    available: true,
  })

  const [extras, setExtras] = useState(() => {
    const saved = loadOnboardingExtras(userKey)
    return {
      dob: '',
      gender: '',
      preferredLanguage: 'Tamil',
      spokenLanguages: ['Tamil'],
      emergencyContactName: '',
      emergencyContactRelation: 'Family member',
      emergencyContactPhone: '',
      caretakerCategory: 'GENERAL_CAREGIVER',
      skillSources: {},
      isNewCaregiver: false,
      workHistory: [{ ...EMPTY_WORK_ENTRY }],
      referenceName: '',
      referencePhone: '',
      educationLevel: '',
      firstAidCertified: 'Yes, certified',
      availableDays: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'],
      preferredShift: SHIFT_OPTIONS[0],
      workType: 'Full-time',
      livingArrangement: 'Live-out',
      preferredLocations: '',
      startWhen: 'Immediately',
      callWhatsAppPhone: user.phone || '',
      callMethod: 'WhatsApp Call',
      callAvailableTimes: [CALL_TIME_SLOTS[0]],
      callNotes: '',
      assessmentChecks: { punctuality: false, hygiene: false, emergency: false },
      consents: {},
      ...saved,
    }
  })

  const [rnExtras, setRnExtras] = useState(() => {
    const saved = loadNursingExtras(userKey)
    return {
      dob: '',
      gender: '',
      preferredLanguage: 'Tamil',
      spokenLanguages: ['Tamil'],
      emergencyContactName: '',
      emergencyContactRelation: 'Spouse / Partner',
      emergencyContactPhone: '',
      nursingQualificationType: NURSING_QUALIFICATION_TYPES[0],
      trainingInstitute: '',
      graduationYear: '',
      registrationBody: NURSING_REGISTRATION_BODIES[0],
      registrationNumber: '',
      registrationValidUntil: '',
      clinicalAreas: [],
      isNewHomeNurse: false,
      currentWorkStatus: '',
      workHistory: [{ org: '', title: '', department: '', duration: '', responsibilities: '' }],
      referenceName: '',
      referencePhone: '',
      services: [],
      offersLongDurationCare: false,
      additionalTraining: '',
      availableDays: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'],
      preferredShift: SHIFT_OPTIONS[0],
      minVisitFee: '',
      maxTravelDistance: NURSING_TRAVEL_DISTANCES[1],
      chargesExtraForTravel: false,
      travelAllowance: '',
      availableForUrgent: true,
      providesNightServices: false,
      nightExtraCharge: '',
      multipleServicesPerVisit: true,
      providesMedicalConsumables: false,
      availableForRecurring: true,
      preferredLocations: '',
      callWhatsAppPhone: user.phone || '',
      callMethod: 'WhatsApp Call',
      callAvailableTimes: [CALL_TIME_SLOTS[0]],
      callNotes: '',
      consents: {},
      ...saved,
    }
  })

  function updateRnExtras(patch) {
    setRnExtras((prev) => {
      const next = { ...prev, ...patch }
      saveNursingExtras(userKey, next)
      return next
    })
  }

  const [photoUrl, setPhotoUrl] = useState(null)       // saved photo
  const [newPhoto, setNewPhoto] = useState(null)       // { file, preview }
  const [removePhoto, setRemovePhoto] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [serverIssues, setServerIssues] = useState([])
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [savedNotice, setSavedNotice] = useState(false)

  const [requirements, setRequirements] = useState([]) // what each job can upload, and whether the admin made it mandatory
  const [verification, setVerification] = useState(null)
  const [savedDocs, setSavedDocs] = useState({})       // type -> what is already uploaded
  const [newDocs, setNewDocs] = useState({})           // type -> { file, preview } chosen now

  useEffect(() => {
    Promise.all([
      api('/caregivers/me/profile').catch(() => null),
      api('/document-requirements').catch(() => []),
      api('/caregivers/me/verification').catch(() => null),
    ]).then(([p, reqs, ver]) => {
      if (p) {
        setForm({
          fullName: p.fullName || user.fullName || '',
          age: p.age ?? '',
          experienceYears: p.experienceYears ?? '',
          qualification: p.qualification ?? '',
          jobType: requestedJobType || p.jobType || '',
          address: p.address ?? '',
          rates: Object.fromEntries(Object.entries(p.rates ?? {}).map(([k, v]) => [k, String(v)])),
          available: p.available ?? true,
        })
        setPhotoUrl(p.photoUrl)
        if (p.caretakerOnboarding) {
          const fromServer = Object.fromEntries(
            Object.entries(p.caretakerOnboarding).filter(([, v]) => v !== null && v !== undefined)
          )
          if (!fromServer.workHistory || fromServer.workHistory.length === 0) {
            fromServer.workHistory = [{ ...EMPTY_WORK_ENTRY }]
          }
          setExtras((prev) => {
            const merged = { ...prev, ...fromServer }
            saveOnboardingExtras(userKey, merged)
            return merged
          })
        }
        if (p.nursingOnboarding) {
          const fromServer = Object.fromEntries(
            Object.entries(p.nursingOnboarding).filter(([, v]) => v !== null && v !== undefined)
          )
          if (!fromServer.workHistory || fromServer.workHistory.length === 0) {
            fromServer.workHistory = [{ org: '', title: '', department: '', duration: '', responsibilities: '' }]
          }
          if (fromServer.minVisitFee != null) {
            fromServer.minVisitFee = String(fromServer.minVisitFee)
          }
          if (Array.isArray(fromServer.services)) {
            fromServer.services = fromServer.services.map((s) => ({
              ...s,
              expectedRate: s.expectedRate != null ? String(s.expectedRate) : '',
            }))
          }
          setRnExtras((prev) => {
            const merged = { ...prev, ...fromServer }
            saveNursingExtras(userKey, merged)
            return merged
          })
        }
      }
      setRequirements(reqs || [])
      if (ver) {
        setVerification(ver)
        setSavedDocs(Object.fromEntries((ver.documents || []).filter((d) => d.status).map((d) => [d.type, d])))
      }
      setLoaded(true)
    })
  }, [user.id])

  useEffect(() => {
    if (requestedJobType) {
      setForm((prev) => (prev.jobType === requestedJobType ? prev : { ...prev, jobType: requestedJobType }))
    }
  }, [requestedJobType])

  // CARETAKER and NURSING use their respective 10-step onboarding wizards; PHYSIOTHERAPIST keeps the standard profile form
  const isCaretakerFlow = form.jobType === 'CARETAKER'
  const isNursingFlow = form.jobType === 'NURSING'

  // Standard form: arriving from a "Set your charges" style link scrolls to that section and highlights it briefly
  useEffect(() => {
    if (!loaded || !focus || isCaretakerFlow || isNursingFlow) return
    const el = document.getElementById(focus)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('flash')
    const t = setTimeout(() => el.classList.remove('flash'), 1800)
    return () => clearTimeout(t)
  }, [loaded, focus, isCaretakerFlow])

  useEffect(() => () => newPhoto && URL.revokeObjectURL(newPhoto.preview), [newPhoto])

  const set = (k, clean = (v) => v) => (e) => setForm({ ...form, [k]: clean(e.target.value) })
  const num = (v) => (v === '' ? null : Number(v))

  const ageError = form.age !== '' && (Number(form.age) < 18 || Number(form.age) > 100) ? 'Age must be between 18 and 100' : ''
  const expError = form.experienceYears !== '' && Number(form.experienceYears) > 60 ? 'Enter 60 or fewer years' : ''
  const rateErrors = Object.fromEntries(
    Object.entries(form.rates).map(([k, v]) => [k, v === '' || Number(v) <= 0 ? 'Enter an amount' : ''])
  )
  const nameError = !form.fullName.trim() ? 'Full name is required' : ''
  const addressError = !form.address.trim() ? 'Address is required' : ''

  // Everything wrong with the full form right now (used by standard Nursing/Physiotherapist flow)
  const allProblems = [
    nameError,
    addressError,
    ageError,
    expError,
    ...RATE_TYPES.filter((r) => rateErrors[r.value]).map((r) => `Enter an amount for "${r.label}" or untick it`),
  ].filter(Boolean)

  function toggleRate(type) {
    const rates = { ...form.rates }
    if (type in rates) delete rates[type]
    else rates[type] = ''
    setForm({ ...form, rates })
  }
  const setRate = (type) => (e) => setForm({ ...form, rates: { ...form.rates, [type]: digits(e.target.value, 7) } })

  function showServerIssues(list) {
    setServerIssues(list)
    setTimeout(() => bannerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
  }

  function acceptPhoto(file) {
    if (!file) return
    if (!TYPES.includes(file.type)) return showServerIssues(['Photo must be a JPG, PNG or WebP image'])
    if (file.size > MAX_PHOTO) return showServerIssues(['Photo must be 2 MB or smaller'])
    setServerIssues([])
    setRemovePhoto(false)
    setNewPhoto({ file, preview: URL.createObjectURL(file) })
  }

  const selectedSkills = form.qualification
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)

  function toggleSkillCard(skillLabel) {
    const exists = selectedSkills.some((s) => s.toLowerCase() === skillLabel.toLowerCase())
    const nextList = exists
      ? selectedSkills.filter((s) => s.toLowerCase() !== skillLabel.toLowerCase())
      : [...selectedSkills, skillLabel]
    setForm({ ...form, qualification: nextList.join(', ').slice(0, 255) })
  }

  function addSuggestion(text) {
    if (selectedSkills.some((t) => t.toLowerCase() === text.toLowerCase())) return
    setForm({ ...form, qualification: [...selectedSkills, text].join(', ').slice(0, 255) })
  }

  // The documents this job can take, mandatory ones first
  const docRows = requirements
    .filter((r) => r.job === form.jobType)
    .sort((a, b) => Number(b.required) - Number(a.required))

  function pickDocument(type) {
    docTarget.current = type
    docRef.current.click()
  }

  function acceptDocument(file) {
    const type = docTarget.current
    if (!file || !type) return
    if (!DOCUMENT_FILE_TYPES.includes(file.type)) return showServerIssues(['A document must be a JPG, PNG, WebP or PDF file'])
    if (file.size > MAX_DOCUMENT) return showServerIssues(['A document must be 5 MB or smaller'])
    setServerIssues([])
    const preview = file.type.startsWith('image/') ? URL.createObjectURL(file) : null
    setNewDocs((d) => ({ ...d, [type]: { file, preview } }))
  }

  // Standard submit for Nursing / Physiotherapist single-page form
  async function submitStandardForm(e) {
    e.preventDefault()
    setServerIssues([])
    if (allProblems.length) {
      setAttempted(true)
      setTimeout(() => bannerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
      return
    }
    setBusy(true)
    try {
      await api('/caregivers/me/profile', {
        method: 'PUT',
        body: {
          fullName: form.fullName,
          address: form.address.trim(),
          age: num(form.age),
          experienceYears: num(form.experienceYears),
          qualification: form.qualification,
          jobType: form.jobType || null,
          rates: Object.fromEntries(Object.entries(form.rates).map(([k, v]) => [k, Number(v)])),
        },
      })
      if (newPhoto) {
        const fd = new FormData()
        fd.append('file', newPhoto.file)
        await api('/caregivers/me/photo', { method: 'POST', form: fd })
      } else if (removePhoto && photoUrl) {
        await api('/caregivers/me/photo', { method: 'DELETE' })
      }
      const failed = []
      const left = {}
      for (const row of docRows) {
        const entry = newDocs[row.type]
        if (!entry?.file) continue
        try {
          const fd = new FormData()
          fd.append('file', entry.file)
          await api(`/caregivers/me/documents/${row.type}`, { method: 'POST', form: fd })
        } catch (err) {
          failed.push(`${row.label}: ${err.message}`)
          left[row.type] = entry
        }
      }
      await refresh()
      if (failed.length) {
        setNewDocs(left)
        showServerIssues(['Your profile was saved, but some documents did not upload:', ...failed])
        return
      }
      navigate('/')
    } catch (err) {
      showServerIssues(err.errors?.length ? err.errors : [err.message])
    } finally {
      setBusy(false)
    }
  }

  // ==========================================================================
  // CARETAKER 10-Step Wizard Helpers & Sequential Gating
  // ==========================================================================
  const liveProfileForCompleteness = {
    fullName: form.fullName,
    address: form.address,
    age: form.age === '' ? null : Number(form.age),
    jobType: form.jobType || null,
    qualification: form.qualification,
    experienceYears: form.experienceYears === '' ? null : Number(form.experienceYears),
    rates: form.rates,
    photoUrl: newPhoto?.preview ?? (removePhoto ? null : photoUrl),
  }
  const liveVerificationForCompleteness = {
    status: verification?.status ?? 'INCOMPLETE',
    documents: Object.keys({ ...savedDocs, ...newDocs }).map((type) => ({
      type,
      status: savedDocs[type]?.status ?? 'PENDING',
    })),
  }

  const ctProgress = caretakerCompleteness(liveProfileForCompleteness, liveVerificationForCompleteness, extras)

  // Strict sequential gating: if the requested step is locked (because an earlier step is incomplete),
  // clamp the user to the first incomplete step (`ctProgress.nextStep`) so they cannot skip steps!
  const requestedWizardStep = WIZARD_STEPS.find((s) => s.key === requestedStep)
  const isRequestedStepAllowed =
    requestedWizardStep && requestedWizardStep.stepNumber <= ctProgress.maxUnlockedStepNumber
  const activeStepKey = isRequestedStepAllowed ? requestedWizardStep.key : (ctProgress.nextStep?.focus || 'about')
  const currentStepIndex = Math.max(0, WIZARD_STEPS.findIndex((s) => s.key === activeStepKey))
  const currentStep = WIZARD_STEPS[currentStepIndex] || WIZARD_STEPS[0]

  function goToStep(stepKey) {
    const target = WIZARD_STEPS.find((s) => s.key === stepKey)
    if (!target) return
    if (target.stepNumber > ctProgress.maxUnlockedStepNumber) {
      showServerIssues([`Please complete Step ${ctProgress.maxUnlockedStepNumber} first before moving to Step ${target.stepNumber}.`])
      return
    }
    setAttempted(false)
    setServerIssues([])
    setParams({ step: stepKey }, { replace: true })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function updateExtras(patch) {
    setExtras((prev) => saveOnboardingExtras(userKey, { ...prev, ...patch }))
  }

  // Per-step required validation so no step can be skipped without its required data
  function getStepValidationProblems(stepKey) {
    const list = []
    if (!form.fullName.trim()) list.push('Full name is required')
    if (!form.address.trim()) list.push('Residential address is required')

    if (stepKey === 'about') {
      if (form.age === '') list.push('Please enter your age (or select your date of birth)')
      else if (ageError) list.push(ageError)
      if (!extras.gender) list.push('Please select your gender')
      if (!extras.emergencyContactName?.trim()) list.push('Emergency contact name is required')
      if (!extras.emergencyContactPhone?.trim()) list.push('Emergency contact phone number is required')
    }

    if (stepKey === 'skills') {
      if (!form.qualification.trim()) {
        list.push('Please select at least one care service you can provide')
      }
    }

    if (stepKey === 'experience') {
      // Both work experience and references are optional in Step 5
      if (form.experienceYears !== '' && expError) {
        list.push(expError)
      }
    }

    if (stepKey === 'certificates') {
      if (!extras.educationLevel) {
        list.push('Please select your highest educational qualification')
      }
    }

    if (stepKey === 'charges') {
      if (!extras.availableDays?.length) {
        list.push('Please select at least one available day of the week')
      }
      const badRates = RATE_TYPES.filter((r) => rateErrors[r.value]).map((r) => `Enter an amount for "${r.label}" or untick it`)
      list.push(...badRates)
      const validRateCount = Object.values(form.rates).filter((v) => v !== '' && Number(v) > 0).length
      if (validRateCount === 0) {
        list.push('Please tick at least one charge (Per hour, Per session, Per day, or Full day & night) and enter your expected rate in LKR')
      }
    }

    if (stepKey === 'interview') {
      if (!extras.callWhatsAppPhone?.trim()) {
        list.push('Please enter your phone / WhatsApp number so our team can call you')
      }
      if (!extras.callAvailableTimes?.length) {
        list.push('Please select when you are normally available during the day for a 10-minute call')
      }
    }

    if (stepKey === 'review') {
      const allConsented = ONBOARDING_CONSENTS.every((c) => !!extras.consents?.[c.key])
      if (!allConsented) {
        list.push('Please accept all 4 agreements and consents to submit your application')
      }
    }

    return list
  }

  const stepProblems = getStepValidationProblems(currentStep.key)

  async function persistCaretakerStep({ exitAfter = false, isSaveAndExit = false, advanceToKey = null } = {}) {
    setServerIssues([])
    if (!isSaveAndExit && stepProblems.length) {
      setAttempted(true)
      setTimeout(() => bannerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
      return false
    }

    setBusy(true)
    try {
      const stepCompletionPatch = { lastStep: currentStep.key }
      if (currentStep.key === 'job') stepCompletionPatch.backgroundStepSaved = true
      if (currentStep.key === 'skills') stepCompletionPatch.skillsStepSaved = true
      if (currentStep.key === 'experience') stepCompletionPatch.experienceStepSaved = true
      if (currentStep.key === 'identity_docs') stepCompletionPatch.identityStepSaved = true
      if (currentStep.key === 'certificates') stepCompletionPatch.certificatesReviewed = true
      if (currentStep.key === 'charges') stepCompletionPatch.availabilityStepSaved = true
      if (currentStep.key === 'interview') stepCompletionPatch.interviewScheduled = true
      if (currentStep.key === 'review' && !isSaveAndExit) {
        stepCompletionPatch.applicationSubmitted = true
        stepCompletionPatch.consented = true
      }

      const nextExtras = { ...extras, ...stepCompletionPatch }

      const validRates = Object.fromEntries(
        Object.entries(form.rates)
          .filter(([, v]) => v !== '' && Number(v) > 0)
          .map(([k, v]) => [k, Number(v)])
      )
      await api('/caregivers/me/profile', {
        method: 'PUT',
        body: {
          fullName: form.fullName.trim(),
          address: form.address.trim(),
          age: num(form.age),
          experienceYears: num(form.experienceYears),
          qualification: form.qualification,
          jobType: form.jobType || 'CARETAKER',
          rates: validRates,
          caretakerOnboarding: nextExtras,
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

      updateExtras(stepCompletionPatch)
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

  async function handleCaretakerSubmit(e) {
    e.preventDefault()
    const nextWizardStep = WIZARD_STEPS[currentStepIndex + 1]
    if (nextWizardStep) {
      await persistCaretakerStep({ advanceToKey: nextWizardStep.key })
    } else {
      await persistCaretakerStep({ exitAfter: true })
    }
  }

  async function handleCaretakerSaveAndExit() {
    if (!form.address.trim() || !form.fullName.trim()) {
      navigate('/')
      return
    }
    await persistCaretakerStep({ exitAfter: true, isSaveAndExit: true })
  }

  function updateWorkEntry(index, field, value) {
    const list = [...(extras.workHistory || [{ ...EMPTY_WORK_ENTRY }])]
    list[index] = { ...list[index], [field]: value }
    updateExtras({ workHistory: list })
  }

  function addWorkEntry() {
    const list = [...(extras.workHistory || []), { ...EMPTY_WORK_ENTRY }]
    updateExtras({ workHistory: list })
  }

  function removeWorkEntry(index) {
    const list = (extras.workHistory || []).filter((_, i) => i !== index)
    updateExtras({ workHistory: list.length ? list : [{ ...EMPTY_WORK_ENTRY }] })
  }

  function toggleAvailableDay(day) {
    const current = extras.availableDays || []
    const next = current.includes(day) ? current.filter((d) => d !== day) : [...current, day]
    updateExtras({ availableDays: next })
  }

  const shownPhoto = newPhoto?.preview ?? (removePhoto ? null : photoUrl)
  const initials = (form.fullName || '?').split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase()

  if (!loaded) return <p className="center">Loading…</p>

  // ==========================================================================
  // RENDER 1: Home Nurse 10-Step Post-Onboarding Wizard (jobType === 'NURSING')
  // ==========================================================================
  if (isNursingFlow) {
    return (
      <NurseOnboardingWizard
        user={user}
        form={form}
        setForm={setForm}
        rnExtras={rnExtras}
        updateRnExtras={updateRnExtras}
        photoUrl={photoUrl}
        setPhotoUrl={setPhotoUrl}
        newPhoto={newPhoto}
        setNewPhoto={setNewPhoto}
        removePhoto={removePhoto}
        setRemovePhoto={setRemovePhoto}
        shownPhoto={shownPhoto}
        pickPhoto={acceptPhoto}
        dragging={dragging}
        setDragging={setDragging}
        fileRef={fileRef}
        docRows={docRows}
        savedDocs={savedDocs}
        setSavedDocs={setSavedDocs}
        newDocs={newDocs}
        setNewDocs={setNewDocs}
        verification={verification}
        setVerification={setVerification}
        openDocPicker={pickDocument}
        removeQueuedDoc={(type) =>
          setNewDocs((d) => {
            const copy = { ...d }
            delete copy[type]
            return copy
          })
        }
        docRef={docRef}
        onDocInputChange={(e) => {
          acceptDocument(e.target.files?.[0])
          e.target.value = ''
        }}
        requestedStep={requestedStep}
        setParams={setParams}
        refresh={refresh}
        logout={logout}
      />
    )
  }

  // ==========================================================================
  // RENDER 2: Standard Profile Form for PHYSIOTHERAPIST / Unselected
  // ==========================================================================
  if (!isCaretakerFlow) {
    const standardIssues = attempted && allProblems.length ? allProblems : serverIssues
    return (
      <div className="app">
        <header className="topbar">
          <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
          {firstTime
            ? <button className="btn-ghost" onClick={logout}>Sign out</button>
            : <Link className="btn-ghost" to="/">Back to dashboard</Link>}
        </header>

        <main className="content wide">
          <div className="page-head">
            <h1>{firstTime ? 'Set up your caregiver profile' : 'Edit your profile'}</h1>
            <p className="subtitle">
              {firstTime ? 'Your name and address are required. Add the rest now or come back to it later.' : 'Keep your details up to date so families can find you.'}
            </p>
          </div>

          <div className="profile-grid">
            <form onSubmit={submitStandardForm} className="panel" noValidate>
              {standardIssues.length > 0 && (
                <div ref={bannerRef} className="banner" role="alert" tabIndex={-1}>
                  <Alert />
                  <div>
                    <strong>{standardIssues.length === 1 ? 'There is a problem with your profile' : `Please fix these ${standardIssues.length} problems`}</strong>
                    <ul>{standardIssues.map((m) => <li key={m}>{m}</li>)}</ul>
                  </div>
                </div>
              )}

              <section id="job" className="section span-all">
                <h2>What do you do? <span className="optional">Optional</span></h2>
                <div className="jobs" role="radiogroup" aria-label="Job">
                  {JOBS.map(({ value, label, desc }) => {
                    const Icon = JOB_ICONS[value]
                    const active = form.jobType === value
                    return (
                      <button type="button" key={value} role="radio" aria-checked={active}
                              className={active ? 'job active' : 'job'}
                              onClick={() => {
                                const nextJob = active ? '' : value
                                setForm({ ...form, jobType: nextJob })
                                if (nextJob === 'CARETAKER') setParams({ step: 'about' }, { replace: true })
                              }}>
                        <span className="job-icon"><Icon /></span>
                        <span className="job-text"><strong>{label}</strong><em>{desc}</em></span>
                      </button>
                    )
                  })}
                </div>
              </section>

              <section id="about" className="section">
                <h2>About you</h2>
                <div className="identity">
                  <div
                    id="photo"
                    className={`photo-block${dragging ? ' dragging' : ''}`}
                    onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
                    onDragLeave={() => setDragging(false)}
                    onDrop={(e) => { e.preventDefault(); setDragging(false); acceptPhoto(e.dataTransfer.files[0]) }}
                  >
                    <button type="button" className="photo-btn" onClick={() => fileRef.current.click()} aria-label="Choose a profile photo">
                      {shownPhoto ? <img src={shownPhoto} alt="" /> : <span className="initials">{initials || '?'}</span>}
                      <span className="camera">+</span>
                    </button>
                    {shownPhoto
                      ? <button type="button" className="link small" onClick={() => { setNewPhoto(null); setRemovePhoto(true) }}>Remove</button>
                      : <span className="photo-caption">Photo <span className="muted-inline">(optional)</span></span>}
                    <input ref={fileRef} type="file" accept={TYPES.join(',')} hidden onChange={(e) => { acceptPhoto(e.target.files[0]); e.target.value = '' }} />
                  </div>

                  <div className="identity-fields">
                    <Field label="Full name *" required maxLength={150} autoComplete="name"
                           error={attempted ? nameError : ''} value={form.fullName} onChange={set('fullName')} />
                    <div className="row">
                      <Field label="Age" inputMode="numeric" placeholder="e.g. 34" error={ageError}
                             value={form.age} onChange={set('age', (v) => digits(v, 3))} />
                      <Field label="Experience" inputMode="numeric" placeholder="e.g. 5" suffix="years" error={expError}
                             value={form.experienceYears} onChange={set('experienceYears', (v) => digits(v, 2))} />
                    </div>
                  </div>
                </div>
                <Field label="Address *" autoComplete="street-address" maxLength={300} placeholder="House number, street, town"
                       hint="Only our team sees your address. Families do not." error={attempted ? addressError : ''}
                       value={form.address} onChange={set('address')} />
                <span className="hint">Photo: drag onto the circle or click it. JPG, PNG or WebP, up to 2 MB.</span>
              </section>

              <section id="qualifications" className="section">
                <h2>Qualifications <span className="optional">Optional</span></h2>
                <Field label="Qualifications and certificates" maxLength={255} placeholder="Separate with commas"
                       value={form.qualification} onChange={set('qualification')} />
                <div className="chips" aria-label="Suggestions">
                  {SUGGESTIONS.map((s) => (
                    <button type="button" key={s} className="chip" onClick={() => addSuggestion(s)}>+ {s}</button>
                  ))}
                </div>
              </section>

              <section id="charges" className="section span-wide">
                <h2>Your charges <span className="optional">Optional</span></h2>
                <p className="hint">Tick the ways you charge and enter an amount ({CURRENCY}). You can offer more than one.</p>
                <div className="rates">
                  {RATE_TYPES.map(({ value, label, unit, desc }) => {
                    const on = value in form.rates
                    return (
                      <div key={value} className={on ? 'rate on' : 'rate'}>
                        <label className="rate-head">
                          <input type="checkbox" checked={on} onChange={() => toggleRate(value)} />
                          <span><strong>{label}</strong><em>{desc}</em></span>
                        </label>
                        {on && (
                          <div className="rate-amount">
                            <div className={`input-wrap has-prefix has-suffix${form.rates[value] === '' ? ' invalid' : ''}`}>
                              <span className="prefix">{CURRENCY}</span>
                              <input inputMode="numeric" placeholder="0" aria-label={`${label} amount`}
                                     value={form.rates[value]} onChange={setRate(value)} autoFocus={form.rates[value] === ''} />
                              <span className="suffix">{unit}</span>
                            </div>
                            {form.rates[value] === '' && <span className="hint error-text">{rateErrors[value]}</span>}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </section>

              <section id="documents" className="section span-wide">
                <h2>Documents {!docRows.some((r) => r.required) && <span className="optional">Optional</span>}</h2>
                <p className="hint">
                  Our team checks each document you add. An approved one earns a verified mark on your card.
                  {docRows.some((r) => r.required) && ' Families can see you only once the ones marked Required are approved.'}
                </p>
                {!form.jobType ? (
                  <p className="muted small">Choose what you do above to see which documents you can add.</p>
                ) : (
                  <ul className="doc-list">
                    {docRows.map((r) => {
                      const saved = savedDocs[r.type]
                      const queued = newDocs[r.type]
                      return (
                        <li key={r.type} className="doc-row">
                          <div className="doc-main">
                            <strong>{r.label} {r.required ? <span className="required-tag">Required</span> : <span className="optional">Optional</span>}</strong>
                            <span className="muted small">{r.hint}</span>
                            {queued && <span className="small">Ready to upload when you save: {queued.file.name}</span>}
                            {saved?.status === 'REJECTED' && saved.rejectReason && <span className="error-text small">Rejected: {saved.rejectReason}</span>}
                          </div>
                          <div className="doc-side">
                            {saved && !queued && <StatusChip label={DOC_LABEL[saved.status]} tone={DOC_TONE[saved.status]} />}
                            <button type="button" className="btn-ghost small-btn" onClick={() => pickDocument(r.type)}>
                              {queued || saved ? 'Choose another file' : 'Choose file'}
                            </button>
                            {queued && (
                              <button type="button" className="link small"
                                      onClick={() => setNewDocs((d) => Object.fromEntries(Object.entries(d).filter(([k]) => k !== r.type)))}>Remove</button>
                            )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                )}
                <span className="hint">JPG, PNG, WebP or PDF, up to 5 MB. Only our team can open your documents.</span>
                <input ref={docRef} type="file" accept={DOCUMENT_FILE_TYPES.join(',')} hidden
                       onChange={(e) => { acceptDocument(e.target.files[0]); e.target.value = '' }} />
              </section>

              <div className="actions">
                <Link className="btn-ghost" to="/">Cancel</Link>
                <button className="btn-primary" disabled={busy}>
                  {busy && <span className="spinner" />}{busy ? 'Saving…' : firstTime ? 'Save and continue' : 'Save changes'}
                </button>
              </div>
            </form>

            <ProfilePreview name={form.fullName.trim()} age={form.age} years={form.experienceYears}
                            qualification={form.qualification} photo={shownPhoto} jobType={form.jobType} rates={form.rates} />
          </div>
        </main>
      </div>
    )
  }

  // ==========================================================================
  // RENDER 2: CARETAKER-ONLY 10-Step, 4-Phase Guided Onboarding Wizard
  // ==========================================================================
  const wizardIssues = attempted && stepProblems.length ? stepProblems : serverIssues

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        <div className="topbar-actions">
          {savedNotice && <span className="status-chip ok">✓ Progress saved</span>}
          <button type="button" className="btn-ghost" onClick={handleCaretakerSaveAndExit} disabled={busy}>
            Save and exit
          </button>
        </div>
      </header>

      <main className="content wide">
        {/* Best-Practice 4-Phase & 10-Step Header */}
        <div className="wizard-header block">
          <div className="wizard-meta">
            <span className="eyebrow">
              CARE PROFESSIONAL REGISTRATION · PHASE {currentStep.phaseNumber} OF 4: {currentStep.phaseTitle.toUpperCase()}
            </span>
            <span className="wizard-progress-label">
              {ctProgress.doneCount} of 10 steps completed ({ctProgress.percent}%) · Auto-saves on continue
            </span>
          </div>

          <div className="wizard-title-row">
            <div>
              <h1>Step {currentStep.stepNumber} of 10 · {currentStep.shortTitle}</h1>
              <p className="subtitle">Complete each step in order. You can save and continue later from your phone or computer.</p>
            </div>
          </div>

          {/* 4-Phase Progress Track */}
          <div className="phase-track" role="list" aria-label="Onboarding phases">
            {CARETAKER_ONBOARDING_PHASES.map((phase) => {
              const phaseSteps = ctProgress.items.filter((i) => i.phaseId === phase.id)
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

          <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={ctProgress.percent} aria-label="Caretaker onboarding progress">
            <span style={{ width: `${ctProgress.percent}%` }} />
          </div>

          {/* Sequential 10-Step Pills (Future steps are locked until prior steps are done) */}
          <nav className="wizard-steps-nav" aria-label="Caretaker onboarding steps">
            <span className="step-pill done" title="Step 1 completed at account creation">
              <span className="step-pill-num"><Check /></span>
              <span>1. Account</span>
            </span>
            {WIZARD_STEPS.map((s) => {
              const item = ctProgress.items.find((i) => i.key === s.key)
              const active = s.key === currentStep.key
              const isLocked = s.stepNumber > ctProgress.maxUnlockedStepNumber
              return (
                <button
                  key={s.key}
                  type="button"
                  disabled={isLocked}
                  title={isLocked ? `Complete Step ${ctProgress.maxUnlockedStepNumber} first to unlock` : s.label}
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

        <div className="profile-grid">
          <form onSubmit={handleCaretakerSubmit} className="panel wizard-panel" noValidate>
            {wizardIssues.length > 0 && (
              <div ref={bannerRef} className="banner" role="alert" tabIndex={-1}>
                <Alert />
                <div>
                  <strong>{wizardIssues.length === 1 ? 'Please complete this required detail to continue' : `Please complete these ${wizardIssues.length} required details to continue`}</strong>
                  <ul>{wizardIssues.map((m) => <li key={m}>{m}</li>)}</ul>
                </div>
              </div>
            )}

            {/* ==============================================================
                STEP 2: PERSONAL INFORMATION
               ============================================================== */}
            {currentStep.key === 'about' && (
              <section id="about" className="section span-all">
                <div className="step-section-head">
                  <h2>Tell us about yourself</h2>
                  <p className="hint">
                    We collect your basic personal details, preferred language, and an emergency contact so our team can verify your identity and support you during home visits.
                  </p>
                </div>

                <div className="identity">
                  <div
                    id="photo"
                    className={`photo-block${dragging ? ' dragging' : ''}`}
                    onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
                    onDragLeave={() => setDragging(false)}
                    onDrop={(e) => { e.preventDefault(); setDragging(false); acceptPhoto(e.dataTransfer.files[0]) }}
                  >
                    <button type="button" className="photo-btn" onClick={() => fileRef.current.click()} aria-label="Choose a profile photo">
                      {shownPhoto ? <img src={shownPhoto} alt="" /> : <span className="initials">{initials || '?'}</span>}
                      <span className="camera">+</span>
                    </button>
                    {shownPhoto
                      ? <button type="button" className="link small" onClick={() => { setNewPhoto(null); setRemovePhoto(true) }}>Remove photo</button>
                      : <span className="photo-caption">Profile photo</span>}
                    <input ref={fileRef} type="file" accept={TYPES.join(',')} hidden onChange={(e) => { acceptPhoto(e.target.files[0]); e.target.value = '' }} />
                  </div>

                  <div className="identity-fields">
                    <Field label="Full name *" required maxLength={150} autoComplete="name"
                           error={attempted ? nameError : ''} value={form.fullName} onChange={set('fullName')} />
                    <div className="row">
                      <div className="field">
                        <label htmlFor="dob-input">Date of birth</label>
                        <div className="input-wrap">
                          <input
                            id="dob-input"
                            type="date"
                            value={extras.dob || ''}
                            onChange={(e) => {
                              const dob = e.target.value
                              updateExtras({ dob })
                              if (dob) {
                                const birthYear = new Date(dob).getFullYear()
                                const calcAge = new Date().getFullYear() - birthYear
                                if (calcAge >= 18 && calcAge <= 100) {
                                  setForm((f) => ({ ...f, age: String(calcAge) }))
                                }
                              }
                            }}
                          />
                        </div>
                      </div>
                      <Field label="Age *" inputMode="numeric" placeholder="e.g. 34" error={attempted && form.age === '' ? 'Age is required' : ageError}
                             value={form.age} onChange={set('age', (v) => digits(v, 3))} />
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
                          className={`chip${extras.gender === g ? ' chip-active' : ''}`}
                          onClick={() => updateExtras({ gender: g })}
                        >
                          {g}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="field">
                    <label>Preferred & spoken languages *</label>
                    <div className="chips">
                      {['Tamil', 'Sinhala', 'English'].map((lang) => {
                        const spoken = extras.spokenLanguages || [extras.preferredLanguage || 'Tamil']
                        const active = spoken.includes(lang)
                        return (
                          <button
                            key={lang}
                            type="button"
                            className={`chip${active ? ' chip-active' : ''}`}
                            onClick={() => {
                              const next = active ? spoken.filter((l) => l !== lang) : [...spoken, lang]
                              const safeNext = next.length ? next : [lang]
                              updateExtras({ spokenLanguages: safeNext, preferredLanguage: safeNext[0] })
                            }}
                          >
                            {lang}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                </div>

                <Field label="Residential address *" autoComplete="street-address" maxLength={300} placeholder="House number, street, town/district"
                       hint="🔒 Why we ask: Only our verification team sees your address to arrange local care visits. Families never see it."
                       error={attempted ? addressError : ''}
                       value={form.address} onChange={set('address')} />

                <div className="sub-card">
                  <strong>Emergency contact *</strong>
                  <p className="muted small">Who should our team contact in case of an emergency while you are on duty?</p>
                  <div className="row">
                    <Field
                      label="Emergency contact full name *"
                      maxLength={120}
                      placeholder="e.g. Kamala Rasathurai"
                      value={extras.emergencyContactName || ''}
                      onChange={(e) => updateExtras({ emergencyContactName: e.target.value })}
                    />
                    <Field
                      label="Emergency contact phone *"
                      type="tel"
                      maxLength={20}
                      placeholder="e.g. 077 123 4567"
                      value={extras.emergencyContactPhone || ''}
                      onChange={(e) => updateExtras({ emergencyContactPhone: e.target.value })}
                    />
                  </div>
                  <div className="chips">
                    {['Spouse / Partner', 'Parent', 'Sibling', 'Adult child', 'Relative'].map((rel) => (
                      <button
                        key={rel}
                        type="button"
                        className={`chip${extras.emergencyContactRelation === rel ? ' chip-active' : ''}`}
                        onClick={() => updateExtras({ emergencyContactRelation: rel })}
                      >
                        {rel}
                      </button>
                    ))}
                  </div>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 3: PROFESSIONAL BACKGROUND
               ============================================================== */}
            {currentStep.key === 'job' && (
              <section id="job" className="section span-all">
                <div className="step-section-head">
                  <h2>Your professional background</h2>
                  <p className="hint">
                    Choose your caretaker category below so we can tailor the rest of your onboarding requirements.
                  </p>
                </div>

                <div className="service-select-grid" role="radiogroup" aria-label="Caretaker category">
                  {CARETAKER_SUBCATEGORIES.map(({ value, label, desc }) => {
                    const active = (extras.caretakerCategory || 'GENERAL_CAREGIVER') === value
                    return (
                      <button
                        type="button"
                        key={value}
                        role="radio"
                        aria-checked={active}
                        className={active ? 'job active' : 'job'}
                        onClick={() => updateExtras({ caretakerCategory: value })}
                      >
                        <span className={`tick${active ? '' : ' open'}`}>{active && <Check />}</span>
                        <span className="job-text"><strong>{label}</strong><em>{desc}</em></span>
                      </button>
                    )
                  })}
                </div>

                <div className="conditional-box">
                  <div className="conditional-head">
                    <Shield />
                    <strong>{ROLE_DYNAMIC_REQUIREMENTS.CARETAKER.title}</strong>
                  </div>
                  <p className="muted small">
                    Selected category: <strong>{CARETAKER_SUBCATEGORIES.find((c) => c.value === (extras.caretakerCategory || 'GENERAL_CAREGIVER'))?.label}</strong>
                  </p>
                  <ul className="conditional-list">
                    {ROLE_DYNAMIC_REQUIREMENTS.CARETAKER.items.map((item) => (
                      <li key={item}><Check /> <span>{item}</span></li>
                    ))}
                  </ul>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 4: SKILLS & CARE SERVICES
               ============================================================== */}
            {currentStep.key === 'skills' && (
              <section id="qualifications" className="section span-all">
                <div className="step-section-head">
                  <h2>What care can you provide?</h2>
                  <p className="hint">
                    Select each care service you can perform and tell us how you gained that experience.
                  </p>
                </div>

                <div className="service-select-grid" role="group" aria-label="Care services">
                  {CARE_SERVICES.map((service) => {
                    const active = selectedSkills.some((s) => s.toLowerCase() === service.id.toLowerCase())
                    const source = extras.skillSources?.[service.id] || 'PRACTICAL'
                    return (
                      <div key={service.id} className={active ? 'skill-card active' : 'skill-card'}>
                        <button
                          type="button"
                          className="skill-card-main"
                          aria-pressed={active}
                          onClick={() => toggleSkillCard(service.id)}
                        >
                          <span className={`tick${active ? '' : ' open'}`}>{active && <Check />}</span>
                          <span className="skill-card-text">
                            <strong>{service.label}</strong>
                            <em>{service.desc}</em>
                          </span>
                        </button>
                        {active && (
                          <div className="skill-source-row">
                            <label className="small muted">How did you gain this experience?</label>
                            <div className="chips">
                              {EXPERIENCE_SOURCES.map((src) => (
                                <button
                                  key={src.value}
                                  type="button"
                                  className={`chip${source === src.value ? ' chip-active' : ''}`}
                                  onClick={() =>
                                    updateExtras({
                                      skillSources: { ...(extras.skillSources || {}), [service.id]: src.value },
                                    })
                                  }
                                >
                                  {src.label}
                                </button>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>

                <div className="conditional-box">
                  <p className="muted small">
                    <strong>Note:</strong> Self-declared skills help families see what services you offer, but they are not treated as verified clinical competencies until reviewed by our team.
                  </p>
                </div>

                <Field
                  label="Selected care services & special skills *"
                  maxLength={255}
                  placeholder="Select cards above or type additional skills separated by commas"
                  value={form.qualification}
                  onChange={set('qualification')}
                />
              </section>
            )}

            {/* ==============================================================
                STEP 5: WORK EXPERIENCE & REFERENCES (OPTIONAL)
               ============================================================== */}
            {currentStep.key === 'experience' && (
              <section id="experience" className="section span-all">
                <div className="step-section-head">
                  <h2>Your work experience & references (Optional)</h2>
                  <p className="hint">
                    Both work experience and references are <strong>optional</strong>. Share any previous caregiving experience if you have it, or click <strong>Save & continue to Step 6 →</strong> to proceed directly.
                  </p>
                </div>

                <div className="roles">
                  <button
                    type="button"
                    className={!extras.isNewCaregiver ? 'role active' : 'role'}
                    onClick={() => updateExtras({ isNewCaregiver: false })}
                  >
                    <span>
                      <strong>I have worked as a caretaker before</strong>
                      <em>Worked in private homes, elders homes, or hospitals (optional details below)</em>
                    </span>
                  </button>
                  <button
                    type="button"
                    className={extras.isNewCaregiver ? 'role active' : 'role'}
                    onClick={() => {
                      updateExtras({ isNewCaregiver: true })
                      setForm({ ...form, experienceYears: '0' })
                    }}
                  >
                    <span>
                      <strong>I'm new to professional caregiving</strong>
                      <em>Entry-level or transitioning from family caregiving</em>
                    </span>
                  </button>
                </div>

                {extras.isNewCaregiver ? (
                  <div className="conditional-box">
                    <strong>Welcome to professional caregiving!</strong>
                    <p className="muted small">
                      We have recorded <strong>0 years</strong> of prior commercial experience. You can still be matched with entry-level companionship and home support roles after verification.
                    </p>
                  </div>
                ) : (
                  <>
                    <Field
                      label="Total years of caregiving experience (optional)"
                      inputMode="numeric"
                      placeholder="e.g. 4"
                      suffix="years"
                      error={expError}
                      value={form.experienceYears}
                      onChange={(e) => setForm({ ...form, experienceYears: digits(e.target.value, 2) })}
                    />

                    <div className="work-history-list">
                      {(extras.workHistory || [{ ...EMPTY_WORK_ENTRY }]).map((entry, idx) => (
                        <div key={idx} className="sub-card">
                          <div className="summary-head">
                            <strong>Work experience #{idx + 1} (optional)</strong>
                            {(extras.workHistory?.length ?? 0) > 1 && (
                              <button type="button" className="link small" onClick={() => removeWorkEntry(idx)}>
                                Remove
                              </button>
                            )}
                          </div>
                          <div className="row">
                            <Field
                              label="Previous organisation / family (optional)"
                              maxLength={120}
                              placeholder="e.g. St. Mary's Elders Home / Private household"
                              value={entry.org || ''}
                              onChange={(e) => updateWorkEntry(idx, 'org', e.target.value)}
                            />
                            <Field
                              label="Job title (optional)"
                              maxLength={100}
                              placeholder="e.g. Home Caretaker / Senior Companion"
                              value={entry.title || ''}
                              onChange={(e) => updateWorkEntry(idx, 'title', e.target.value)}
                            />
                          </div>
                          <div className="row">
                            <Field
                              label="Duration (optional)"
                              maxLength={80}
                              placeholder="e.g. 2022 – 2025 (3 years)"
                              value={entry.duration || ''}
                              onChange={(e) => updateWorkEntry(idx, 'duration', e.target.value)}
                            />
                            <Field
                              label="Reason for leaving (optional)"
                              maxLength={120}
                              placeholder="e.g. Contract completed / family relocated"
                              value={entry.reasonForLeaving || ''}
                              onChange={(e) => updateWorkEntry(idx, 'reasonForLeaving', e.target.value)}
                            />
                          </div>
                          <Field
                            label="Key responsibilities (optional)"
                            maxLength={200}
                            placeholder="e.g. Assisted with bathing, mobility, feeding, and daily companionship"
                            value={entry.responsibilities || ''}
                            onChange={(e) => updateWorkEntry(idx, 'responsibilities', e.target.value)}
                          />
                        </div>
                      ))}

                      <button type="button" className="btn-ghost small-btn" onClick={addWorkEntry}>
                        + Add another work experience
                      </button>
                    </div>
                  </>
                )}

                <div className="sub-card">
                  <strong>Reference contact (optional)</strong>
                  <p className="muted small">A previous employer, doctor, community leader, or family who can vouch for your character:</p>
                  <div className="row">
                    <Field
                      label="Reference name (optional)"
                      maxLength={120}
                      placeholder="e.g. Dr. K. Selvarajah / Former employer"
                      value={extras.referenceName || ''}
                      onChange={(e) => updateExtras({ referenceName: e.target.value })}
                    />
                    <Field
                      label="Reference phone (optional)"
                      type="tel"
                      maxLength={20}
                      placeholder="e.g. 077 987 6543"
                      value={extras.referencePhone || ''}
                      onChange={(e) => updateExtras({ referencePhone: e.target.value })}
                    />
                  </div>
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 6: IDENTITY DOCUMENTS
               ============================================================== */}
            {currentStep.key === 'identity_docs' && (
              <section id="identity_docs" className="section span-all">
                <div className="step-section-head">
                  <h2>Verify your identity</h2>
                  <p className="hint">
                    🔒 Why we ask: Identity verification keeps families and caretakers safe. Only our verification team can open your documents — they are never shown publicly.
                  </p>
                </div>

                <div className="conditional-box">
                  <strong>How to take a clear document photo</strong>
                  <ul className="conditional-list">
                    <li><Check /> <span>Place your NIC or GS letter on a flat surface in good lighting.</span></li>
                    <li><Check /> <span>Make sure all 4 corners, your photo, and your NIC number are clearly readable.</span></li>
                    <li><Check /> <span>Preview your upload below and retake if blurry.</span></li>
                  </ul>
                </div>

                <ul className="doc-list">
                  {docRows.map((r) => {
                    const saved = savedDocs[r.type]
                    const queued = newDocs[r.type]
                    return (
                      <li key={r.type} className="doc-row">
                        <div className="doc-main">
                          <strong>{r.label} {r.required ? <span className="required-tag">Required</span> : <span className="optional">Recommended</span>}</strong>
                          <span className="muted small">{r.hint}</span>
                          {queued && (
                            <div className="doc-preview-box">
                              {queued.preview && <img src={queued.preview} alt="Document preview" className="doc-thumb" />}
                              <div>
                                <span className="small">Selected file: <strong>{queued.file.name}</strong></span>
                                <span className="step-sub">Check that the image is clear before continuing.</span>
                              </div>
                            </div>
                          )}
                          {saved?.status === 'REJECTED' && saved.rejectReason && (
                            <span className="error-text small">Rejected: {saved.rejectReason}</span>
                          )}
                        </div>
                        <div className="doc-side">
                          <StatusChip
                            label={queued ? 'Ready to upload' : saved ? DOC_LABEL[saved.status] : 'Not uploaded'}
                            tone={queued ? 'done' : saved ? DOC_TONE[saved.status] : 'off'}
                          />
                          <button type="button" className="btn-ghost small-btn" onClick={() => pickDocument(r.type)}>
                            {queued || saved ? 'Retake / Replace' : 'Camera or upload'}
                          </button>
                          {queued && (
                            <button
                              type="button"
                              className="link small"
                              onClick={() => setNewDocs((d) => Object.fromEntries(Object.entries(d).filter(([k]) => k !== r.type)))}
                            >
                              Remove
                            </button>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
                <span className="hint">Accepted formats: JPG, PNG, WebP or PDF, up to 5 MB. If you don't have your document right now, you can still save and upload it from your dashboard later.</span>
                <input ref={docRef} type="file" accept={DOCUMENT_FILE_TYPES.join(',')} capture="environment" hidden
                       onChange={(e) => { acceptDocument(e.target.files[0]); e.target.value = '' }} />
              </section>
            )}

            {/* ==============================================================
                STEP 7: QUALIFICATIONS & CERTIFICATES
               ============================================================== */}
            {currentStep.key === 'certificates' && (
              <section id="certificates" className="section span-all">
                <div className="step-section-head">
                  <h2>Qualifications and certificates</h2>
                  <p className="hint">
                    Tell us about your education and any caregiving or First Aid / CPR training you have completed.
                  </p>
                </div>

                <div className="field">
                  <label>Highest educational qualification *</label>
                  <div className="chips">
                    {EDUCATION_LEVELS.map((lvl) => (
                      <button
                        key={lvl}
                        type="button"
                        className={`chip${extras.educationLevel === lvl ? ' chip-active' : ''}`}
                        onClick={() => updateExtras({ educationLevel: lvl })}
                      >
                        {lvl}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="field">
                  <label>Do you have First Aid / CPR training? *</label>
                  <div className="chips">
                    {['Yes, certified', 'Basic practical knowledge', 'No formal training yet'].map((opt) => (
                      <button
                        key={opt}
                        type="button"
                        className={`chip${extras.firstAidCertified === opt ? ' chip-active' : ''}`}
                        onClick={() => {
                          updateExtras({ firstAidCertified: opt })
                          if (opt === 'Yes, certified') addSuggestion('First aid certified')
                        }}
                      >
                        {opt}
                      </button>
                    ))}
                  </div>
                </div>

                <Field
                  label="Caregiving certificates & skills summary"
                  maxLength={255}
                  placeholder="e.g. NVQ Caregiver Certificate, First aid certified, Elderly care certificate"
                  hint="Tap any certificate below to add it to your profile card:"
                  value={form.qualification}
                  onChange={set('qualification')}
                />
                <div className="chips" aria-label="Certificate suggestions">
                  {SUGGESTIONS.map((s) => (
                    <button type="button" key={s} className="chip" onClick={() => addSuggestion(s)}>+ {s}</button>
                  ))}
                </div>
              </section>
            )}

            {/* ==============================================================
                STEP 8: AVAILABILITY & PREFERENCES
               ============================================================== */}
            {currentStep.key === 'charges' && (
              <section id="charges" className="section span-all">
                <div className="step-section-head">
                  <h2>Availability and preferences</h2>
                  <p className="hint">
                    This information helps us match you with families looking for your exact schedule, location, and rates ({CURRENCY}).
                  </p>
                </div>

                <div className={form.available ? 'block availability on' : 'block availability'}>
                  <div>
                    <strong>{form.available ? 'Available for work' : 'Not available right now'}</strong>
                    <p className="muted small">You can switch this on or off anytime from your dashboard.</p>
                  </div>
                  <button
                    type="button"
                    role="switch"
                    aria-checked={form.available}
                    className="switch"
                    onClick={() => setForm({ ...form, available: !form.available })}
                  >
                    <span />
                  </button>
                </div>

                <div className="field">
                  <div className="summary-head">
                    <label>Which days are you available? *</label>
                    <button
                      type="button"
                      className="link small"
                      onClick={() => updateExtras({ availableDays: [...WEEK_DAYS] })}
                    >
                      Select all 7 days
                    </button>
                  </div>
                  <div className="chips">
                    {WEEK_DAYS.map((day) => {
                      const active = (extras.availableDays || []).includes(day)
                      return (
                        <button
                          key={day}
                          type="button"
                          className={`chip${active ? ' chip-active' : ''}`}
                          onClick={() => toggleAvailableDay(day)}
                        >
                          {day}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <div className="row">
                  <div className="field">
                    <label>Employment type *</label>
                    <div className="chips">
                      {['Full-time', 'Part-time', 'Flexible / Both'].map((wt) => (
                        <button
                          key={wt}
                          type="button"
                          className={`chip${extras.workType === wt ? ' chip-active' : ''}`}
                          onClick={() => updateExtras({ workType: wt })}
                        >
                          {wt}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="field">
                    <label>Living arrangement *</label>
                    <div className="chips">
                      {['Live-out (Go home daily)', 'Live-in (Stay with family)', 'Either Live-in or Live-out'].map((la) => (
                        <button
                          key={la}
                          type="button"
                          className={`chip${extras.livingArrangement === la ? ' chip-active' : ''}`}
                          onClick={() => updateExtras({ livingArrangement: la })}
                        >
                          {la}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="field">
                  <label>Preferred working hours *</label>
                  <div className="chips">
                    {SHIFT_OPTIONS.map((shift) => (
                      <button
                        key={shift}
                        type="button"
                        className={`chip${extras.preferredShift === shift ? ' chip-active' : ''}`}
                        onClick={() => updateExtras({ preferredShift: shift })}
                      >
                        {shift}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="row">
                  <Field
                    label="Preferred locations / towns"
                    maxLength={150}
                    placeholder="e.g. Jaffna, Nallur, Chavakachcheri, Colombo"
                    value={extras.preferredLocations || ''}
                    onChange={(e) => updateExtras({ preferredLocations: e.target.value })}
                  />
                  <div className="field">
                    <label>When can you start? *</label>
                    <div className="chips">
                      {START_AVAILABILITY_OPTIONS.map((st) => (
                        <button
                          key={st}
                          type="button"
                          className={`chip${extras.startWhen === st ? ' chip-active' : ''}`}
                          onClick={() => updateExtras({ startWhen: st })}
                        >
                          {st}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="field">
                  <label>Expected charges / salary ({CURRENCY}) *</label>
                  <p className="hint">Tick at least one way you charge and enter your rate in {CURRENCY}:</p>
                  <div className="rates">
                    {RATE_TYPES.map(({ value, label, unit, desc }) => {
                      const on = value in form.rates
                      return (
                        <div key={value} className={on ? 'rate on' : 'rate'}>
                          <label className="rate-head">
                            <input type="checkbox" checked={on} onChange={() => toggleRate(value)} />
                            <span><strong>{label}</strong><em>{desc}</em></span>
                          </label>
                          {on && (
                            <div className="rate-amount">
                              <div className={`input-wrap has-prefix has-suffix${form.rates[value] === '' ? ' invalid' : ''}`}>
                                <span className="prefix">{CURRENCY}</span>
                                <input inputMode="numeric" placeholder="0" aria-label={`${label} amount`}
                                       value={form.rates[value]} onChange={setRate(value)} autoFocus={form.rates[value] === ''} />
                                <span className="suffix">{unit}</span>
                              </div>
                              {form.rates[value] === '' && <span className="hint error-text">{rateErrors[value]}</span>}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
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
                    Instead of a long formal interview, our HomeCare Connect team will give you a quick, friendly <strong>10-minute phone or WhatsApp call</strong> during the time of day you are normally free.
                  </p>
                </div>

                <div className="meet-card">
                  <div className="meet-card-head">
                    <div className="meet-badge">
                      <span className="meet-dot" />
                      <strong>WhatsApp / Phone · 10-Minute Introductory Call</strong>
                    </div>
                    <StatusChip
                      label={extras.interviewScheduled ? 'Availability saved' : 'Share your availability'}
                      tone={extras.interviewScheduled ? 'ok' : 'wait'}
                    />
                  </div>
                  <p className="small muted">
                    What happens on this 10-minute call:
                  </p>
                  <ul className="conditional-list">
                    <li><Check /> <span>Quick 2-minute greeting with our HomeCare Connect coordinator</span></li>
                    <li><Check /> <span>5-minute chat about your caregiving background and preferred schedule</span></li>
                    <li><Check /> <span>3 minutes for your questions about bookings, safety, and payments</span></li>
                  </ul>
                </div>

                <div className="row">
                  <Field
                    label="Your WhatsApp / phone number *"
                    type="tel"
                    maxLength={20}
                    placeholder="e.g. 077 123 4567"
                    value={extras.callWhatsAppPhone || ''}
                    onChange={(e) => updateExtras({ callWhatsAppPhone: e.target.value })}
                  />

                  <div className="field">
                    <label>How should we contact you? *</label>
                    <div className="chips">
                      {['WhatsApp Call', 'Normal Phone Call', 'Either WhatsApp or Phone'].map((method) => (
                        <button
                          key={method}
                          type="button"
                          className={`chip${(extras.callMethod || 'WhatsApp Call') === method ? ' chip-active' : ''}`}
                          onClick={() => updateExtras({ callMethod: method })}
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
                      const selected = extras.callAvailableTimes || []
                      const active = selected.includes(slot)
                      return (
                        <button
                          key={slot}
                          type="button"
                          className={`chip${active ? ' chip-active' : ''}`}
                          onClick={() => {
                            const next = active
                              ? selected.filter((s) => s !== slot)
                              : [...selected, slot]
                            updateExtras({ callAvailableTimes: next })
                          }}
                        >
                          {slot}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <Field
                  label="Any specific notes on your daily availability? (optional)"
                  maxLength={160}
                  placeholder="e.g. Usually free after 2 PM on weekdays, or anytime on weekends"
                  value={extras.callNotes || ''}
                  onChange={(e) => updateExtras({ callNotes: e.target.value })}
                />
              </section>
            )}

            {/* ==============================================================
                STEP 10: REVIEW, CONSENT & SUBMIT
               ============================================================== */}
            {currentStep.key === 'review' && (
              <section id="review" className="section span-all">
                <div className="step-section-head">
                  <h2>Review, agreements and submission</h2>
                  <p className="hint">
                    Check your complete caretaker onboarding summary below. Click <strong>Edit</strong> on any section to make changes before submitting.
                  </p>
                </div>

                <div className="review-summary-grid">
                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>2. Personal information</strong>
                      <button type="button" className="link small" onClick={() => goToStep('about')}>Edit</button>
                    </div>
                    <p className="small"><strong>Name:</strong> {form.fullName || 'Not set'}</p>
                    <p className="small"><strong>Age & Gender:</strong> {form.age ? `${form.age} yrs` : '—'} · {extras.gender || '—'}</p>
                    <p className="small"><strong>Languages:</strong> {(extras.spokenLanguages || [extras.preferredLanguage]).join(', ')}</p>
                    <p className="small"><strong>Address:</strong> {form.address || 'Not set'}</p>
                    <p className="small"><strong>Emergency:</strong> {extras.emergencyContactName} ({extras.emergencyContactPhone})</p>
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>3 & 5. Background & experience</strong>
                      <button type="button" className="link small" onClick={() => goToStep('experience')}>Edit</button>
                    </div>
                    <p className="small">
                      <strong>Category:</strong>{' '}
                      {CARETAKER_SUBCATEGORIES.find((c) => c.value === extras.caretakerCategory)?.label || 'General caregiver'}
                    </p>
                    <p className="small">
                      <strong>Experience:</strong>{' '}
                      {extras.isNewCaregiver ? 'New to professional caregiving (Entry-level)' : `${form.experienceYears || 0} years`}
                    </p>
                    {!extras.isNewCaregiver && extras.workHistory?.[0]?.org && (
                      <p className="small"><strong>Recent role:</strong> {extras.workHistory[0].title} at {extras.workHistory[0].org}</p>
                    )}
                    {extras.referenceName && (
                      <p className="small"><strong>Reference:</strong> {extras.referenceName} ({extras.referencePhone})</p>
                    )}
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>4 & 7. Skills & qualifications</strong>
                      <button type="button" className="link small" onClick={() => goToStep('skills')}>Edit</button>
                    </div>
                    <p className="small"><strong>Services:</strong> {form.qualification || 'None selected'}</p>
                    <p className="small"><strong>Education:</strong> {extras.educationLevel || 'Not set'}</p>
                    <p className="small"><strong>First Aid / CPR:</strong> {extras.firstAidCertified || 'Not set'}</p>
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>8. Availability & charges</strong>
                      <button type="button" className="link small" onClick={() => goToStep('charges')}>Edit</button>
                    </div>
                    <p className="small"><strong>Days:</strong> {(extras.availableDays || []).join(', ')}</p>
                    <p className="small"><strong>Schedule:</strong> {extras.workType} · {extras.livingArrangement}</p>
                    <p className="small"><strong>Start:</strong> {extras.startWhen} {extras.preferredLocations ? `(${extras.preferredLocations})` : ''}</p>
                    <p className="small">
                      <strong>Rates:</strong>{' '}
                      {Object.keys(form.rates).length > 0
                        ? RATE_TYPES.filter((r) => form.rates[r.value] > 0)
                            .map((r) => `${r.label}: ${formatMoney(Number(form.rates[r.value]))}`)
                            .join(' · ')
                        : 'No charges set'}
                    </p>
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>6. Identity documents</strong>
                      <button type="button" className="link small" onClick={() => goToStep('identity_docs')}>Edit</button>
                    </div>
                    <div className="chips">
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
                  </div>

                  <div className="summary-card">
                    <div className="summary-head">
                      <strong>9. 10-Min WhatsApp / Phone Call</strong>
                      <button type="button" className="link small" onClick={() => goToStep('interview')}>Edit</button>
                    </div>
                    <p className="small"><strong>Contact method:</strong> {extras.callMethod || 'WhatsApp Call'}</p>
                    <p className="small"><strong>WhatsApp / Phone:</strong> {extras.callWhatsAppPhone || 'Not set'}</p>
                    <p className="small"><strong>Normally free:</strong> {(extras.callAvailableTimes || []).join(', ') || 'Not set'}</p>
                    {extras.callNotes && <p className="small"><strong>Notes:</strong> {extras.callNotes}</p>}
                  </div>
                </div>

                <div className="consent-box">
                  <strong>Agreements and consent *</strong>
                  <p className="muted small">Please tick all 4 agreements below to submit your caretaker application:</p>
                  {ONBOARDING_CONSENTS.map((item) => {
                    const checked = Boolean(extras.consents?.[item.key])
                    return (
                      <label key={item.key} className="consent-row">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            const nextConsents = { ...(extras.consents || {}), [item.key]: e.target.checked }
                            const allDone = ONBOARDING_CONSENTS.every((c) => !!nextConsents[c.key])
                            updateExtras({ consents: nextConsents, consented: allDone })
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
                    onClick={() => goToStep(WIZARD_STEPS[currentStepIndex - 1].key)}
                  >
                    ← Previous step
                  </button>
                ) : (
                  <Link className="btn-ghost" to="/">Back to dashboard</Link>
                )}
                <button type="button" className="link" disabled={busy} onClick={handleCaretakerSaveAndExit}>
                  Save and exit
                </button>
              </div>

              <button className="btn-primary" disabled={busy}>
                {busy && <span className="spinner" />}
                {busy
                  ? 'Saving…'
                  : currentStep.key === 'review'
                  ? 'Submit caretaker application'
                  : `Save & continue to Step ${currentStep.stepNumber + 1} →`}
              </button>
            </div>
          </form>

          <ProfilePreview
            name={form.fullName.trim()}
            age={form.age}
            years={form.experienceYears}
            qualification={form.qualification}
            photo={shownPhoto}
            jobType={form.jobType}
            rates={form.rates}
          />
        </div>
      </main>
    </div>
  )
}
