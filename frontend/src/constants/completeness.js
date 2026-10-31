// Original completeness items for Nursing / Physiotherapist (and general fallback)
export const COMPLETENESS_ITEMS = [
  { key: 'name', label: 'Full name', action: 'Add your name', focus: 'about', done: (p) => !!p.fullName?.trim() },
  { key: 'address', label: 'Address', action: 'Add your address', focus: 'about', done: (p) => !!p.address?.trim() },
  { key: 'job', label: 'What you do', action: 'Choose your job', focus: 'job', done: (p) => !!p.jobType },
  { key: 'photo', label: 'Profile photo', action: 'Add a photo', focus: 'photo', done: (p) => !!p.photoUrl },
  { key: 'charges', label: 'Your charges', action: 'Set your charges', focus: 'charges', done: (p) => Object.keys(p.rates ?? {}).length > 0 },
  { key: 'qualification', label: 'Qualifications', action: 'Add qualifications', focus: 'qualifications', done: (p) => !!p.qualification?.trim() },
  { key: 'experience', label: 'Experience', action: 'Add your experience', focus: 'about', done: (p) => p.experienceYears != null },
  { key: 'age', label: 'Age', action: 'Add your age', focus: 'about', done: (p) => p.age != null },
]

export function completeness(profile) {
  const items = COMPLETENESS_ITEMS.map((i) => ({ ...i, isDone: i.done(profile) }))
  const doneCount = items.filter((i) => i.isDone).length
  return { items, percent: Math.round((doneCount / items.length) * 100), missing: items.filter((i) => !i.isDone) }
}

// Plain advice shown for whatever is still missing (no made-up statistics)
export const TIPS = {
  photo: 'A clear, friendly photo helps families feel comfortable choosing you.',
  charges: 'Families compare prices. Add at least one charge so they know what to expect.',
  qualification: 'Listing your certificates and training builds trust.',
  address: 'Our team uses your address to arrange visits. Families never see it.',
  job: 'Choose what you do so you appear when families look for a caretaker, nurse or physiotherapist.',
  experience: 'Mention how long you have been doing this. Experience matters to families.',
  about: 'Complete your personal details, emergency contact, and photo so our team can verify your profile.',
  skills: 'Select the care services you can provide and how you gained each skill.',
  identity_docs: 'Uploading a clear photo of your NIC and GS letter builds immediate trust with our verification team.',
  certificates: 'Adding your educational level and caregiving or First Aid certificates helps families trust your training.',
  interview: 'Share your WhatsApp number and normal daily free time for a quick 10-minute call with our team.',
  review: 'Review your full summary and accept the caretaker agreements to submit your application.',
}
export const GENERIC_TIP = 'Keep your charges and details up to date so families always see the latest.'

// ============================================================================
// CARETAKER-ONLY (jobType === 'CARETAKER') 10-Step, 4-Phase Onboarding Flow
// ============================================================================

export const CARE_SERVICES = [
  { id: 'Elderly care', label: 'Elderly care', desc: 'Daily companionship, safety monitoring, and senior support' },
  { id: 'Bedridden patient care', label: 'Bedridden patient care', desc: 'Turning, positioning, pressure sore prevention, and bedside care' },
  { id: 'Personal hygiene', label: 'Personal hygiene assistance', desc: 'Bathing, grooming, dressing, and toileting support' },
  { id: 'Feeding assistance', label: 'Feeding assistance', desc: 'Meal preparation, assisted feeding, and hydration monitoring' },
  { id: 'Mobility assistance', label: 'Mobility assistance', desc: 'Walking support, wheelchair transfers, and fall prevention' },
  { id: 'Dementia care', label: 'Dementia care', desc: 'Memory support, calm redirection, and structured routines' },
  { id: 'Companionship', label: 'Companionship', desc: 'Conversation, emotional support, and accompanying to appointments' },
]

export const EXPERIENCE_SOURCES = [
  { value: 'TRAINING', label: 'Professional training' },
  { value: 'EMPLOYMENT', label: 'Previous employment' },
  { value: 'PRACTICAL', label: 'Practical experience' },
  { value: 'FAMILY', label: 'Personal / family caregiving' },
]

export const CARETAKER_SUBCATEGORIES = [
  { value: 'GENERAL_CAREGIVER', label: 'General caregiver', desc: 'Daily home support, companionship, and personal assistance' },
  { value: 'TRAINED_CAREGIVER', label: 'Trained caregiver', desc: 'Completed NVQ or formal caregiving training course' },
  { value: 'NURSING_ASSISTANT', label: 'Nursing assistant / Home health aide', desc: 'Assisted with patient vitals, mobility, and bedside care' },
  { value: 'ELDERLY_SPECIALIST', label: 'Elderly & dementia specialist', desc: 'Experienced with senior mobility, bedridden, or dementia care' },
]

export const EDUCATION_LEVELS = [
  'G.C.E. O/L completed',
  'G.C.E. A/L completed',
  'NVQ Level 3 / 4 Caregiver Certificate',
  'Diploma in Caregiving / Healthcare',
  'Other vocational training',
]

export const WEEK_DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export const SHIFT_OPTIONS = [
  'Daytime shift (8:00 AM – 5:00 PM)',
  'Night shift (8:00 PM – 6:00 AM)',
  '24-hour full day & night care',
  'Flexible / Part-time hours',
]

export const START_AVAILABILITY_OPTIONS = [
  'Immediately',
  'Within 1 week',
  'Within 2 weeks',
  'Next month',
]

export const CALL_TIME_SLOTS = [
  'Morning (8:00 AM – 12:00 PM)',
  'Afternoon (12:00 PM – 4:00 PM)',
  'Evening (4:00 PM – 8:00 PM)',
  'Anytime during the day',
]

export const ROLE_DYNAMIC_REQUIREMENTS = {
  CARETAKER: {
    title: 'Your tailored Caretaker onboarding requirements',
    items: [
      'Identity verification (National Identity Card – NIC & GS Letter)',
      'Care services & how you gained your practical experience',
      'Optional work history & references',
      '10-minute introductory WhatsApp / phone call with our team',
      'Safeguarding & caretaker code of conduct agreement',
    ],
  },
}

export const ONBOARDING_CONSENTS = [
  {
    key: 'serviceAgreement',
    label: 'Service agreement acceptance',
    desc: 'I agree to provide safe, respectful, and reliable home care services through HomeCare Connect.',
  },
  {
    key: 'privacyConsent',
    label: 'Privacy consent',
    desc: 'I consent to HomeCare Connect storing my profile and verification documents securely for matching with families.',
  },
  {
    key: 'backgroundConsent',
    label: 'Background & document verification consent',
    desc: 'I authorise the verification team to review my NIC, Grama Niladhari letter, and references.',
  },
  {
    key: 'codeOfConduct',
    label: 'Code of conduct & safeguarding acceptance',
    desc: 'I commit to patient dignity, confidentiality, punctuality, and safeguarding standards at all times.',
  },
]

const STORAGE_PREFIX = 'hc_caretaker_onboarding_v2_'

export function loadOnboardingExtras(userKey) {
  if (!userKey) return {}
  try {
    const raw = localStorage.getItem(`${STORAGE_PREFIX}${userKey}`)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function saveOnboardingExtras(userKey, patch) {
  if (!userKey) return {}
  try {
    const current = loadOnboardingExtras(userKey)
    const next = { ...current, ...patch, lastSavedAt: new Date().toISOString() }
    localStorage.setItem(`${STORAGE_PREFIX}${userKey}`, JSON.stringify(next))
    return next
  } catch {
    return patch
  }
}

function hasIdentityDoc(verification, extras) {
  const docs = verification?.documents ?? []
  return docs.some((d) => (d.type === 'NIC' || d.type === 'GS_LETTER') && !!d.status) || Boolean(extras?.identityStepSaved)
}

function hasAllConsents(extras) {
  const c = extras?.consents ?? {}
  return ONBOARDING_CONSENTS.every((item) => !!c[item.key])
}

export const CARETAKER_ONBOARDING_PHASES = [
  {
    id: 'getting-started',
    phaseNumber: 1,
    title: 'Getting started',
    steps: [
      {
        stepNumber: 1,
        key: 'account',
        focus: 'about',
        label: 'Step 1 – Account creation',
        shortTitle: 'Account creation',
        action: 'Account created & verified',
        done: () => true,
      },
      {
        stepNumber: 2,
        key: 'about',
        focus: 'about',
        label: 'Step 2 – Personal information',
        shortTitle: 'Personal information',
        action: 'Name, DOB/age, gender, address & emergency contact',
        done: (p, _v, extras) =>
          !!p?.fullName?.trim() &&
          !!p?.address?.trim() &&
          p?.age != null &&
          !!extras?.gender &&
          !!extras?.emergencyContactName?.trim() &&
          !!extras?.emergencyContactPhone?.trim(),
      },
    ],
  },
  {
    id: 'about-you',
    phaseNumber: 2,
    title: 'About you',
    steps: [
      {
        stepNumber: 3,
        key: 'job',
        focus: 'job',
        label: 'Step 3 – Professional background',
        shortTitle: 'Professional background',
        action: 'Choose your caretaker category',
        done: (p, _v, extras) => p?.jobType === 'CARETAKER' && !!extras?.backgroundStepSaved,
      },
      {
        stepNumber: 4,
        key: 'skills',
        focus: 'skills',
        label: 'Step 4 – Skills and care services',
        shortTitle: 'Skills & care services',
        action: 'Select care services & how you learned them',
        done: (p, _v, extras) => !!p?.qualification?.trim() && !!extras?.skillsStepSaved,
      },
      {
        stepNumber: 5,
        key: 'experience',
        focus: 'experience',
        label: 'Step 5 – Experience and references',
        shortTitle: 'Experience & references',
        action: 'Add work history & references (optional)',
        done: (p, _v, extras) => Boolean(extras?.experienceStepSaved) || p?.experienceYears != null,
      },
    ],
  },
  {
    id: 'verification',
    phaseNumber: 3,
    title: 'Verification',
    steps: [
      {
        stepNumber: 6,
        key: 'identity_docs',
        focus: 'identity_docs',
        label: 'Step 6 – Identity documents',
        shortTitle: 'Identity documents',
        action: 'Upload NIC & Grama Niladhari letter',
        done: (p, v, extras) => hasIdentityDoc(v, extras),
      },
      {
        stepNumber: 7,
        key: 'certificates',
        focus: 'certificates',
        label: 'Step 7 – Qualifications and certificates',
        shortTitle: 'Qualifications & certificates',
        action: 'Education level & First Aid / caregiving certificates',
        done: (_p, _v, extras) => Boolean(extras?.certificatesReviewed && extras?.educationLevel),
      },
      {
        stepNumber: 8,
        key: 'charges',
        focus: 'charges',
        label: 'Step 8 – Availability and preferences',
        shortTitle: 'Availability & preferences',
        action: 'Days available, work type, locations & expected rates',
        done: (p, _v, extras) =>
          Object.keys(p?.rates ?? {}).length > 0 &&
          (extras?.availableDays?.length ?? 0) > 0 &&
          !!extras?.availabilityStepSaved,
      },
    ],
  },
  {
    id: 'final-steps',
    phaseNumber: 4,
    title: 'Final steps',
    steps: [
      {
        stepNumber: 9,
        key: 'interview',
        focus: 'interview',
        label: 'Step 9 – Interview and assessment',
        shortTitle: '10-min WhatsApp call',
        action: 'Share your daily free time for a 10-min WhatsApp call',
        done: (_p, _v, extras) =>
          Boolean(extras?.interviewScheduled && extras?.callWhatsAppPhone?.trim() && (extras?.callAvailableTimes?.length ?? 0) > 0),
      },
      {
        stepNumber: 10,
        key: 'review',
        focus: 'review',
        label: 'Step 10 – Agreements and submission',
        shortTitle: 'Agreements & submit',
        action: 'Review full summary & accept agreements',
        done: (p, _v, extras) => (hasAllConsents(extras) || Boolean(extras?.applicationSubmitted)) && !!p?.address?.trim(),
      },
    ],
  },
]

export const CARETAKER_ONBOARDING_STEPS = CARETAKER_ONBOARDING_PHASES.flatMap((phase) =>
  phase.steps.map((s) => ({ ...s, phaseId: phase.id, phaseNumber: phase.phaseNumber, phaseTitle: phase.title }))
)

export const WIZARD_STEPS = CARETAKER_ONBOARDING_STEPS.filter((s) => s.stepNumber >= 2)

/**
 * Computes sequential completeness: a step can only count as unlocked if all prior steps are done.
 * This prevents jumping to Step 5 before completing Step 2, 3, and 4.
 */
export function caretakerCompleteness(profile, verification = null, extras = {}) {
  let previousAllDone = true
  const items = CARETAKER_ONBOARDING_STEPS.map((step) => {
    const rawDone = Boolean(step.done(profile, verification, extras))
    const isUnlocked = previousAllDone
    const isDone = isUnlocked && rawDone
    if (!isDone) previousAllDone = false
    return {
      ...step,
      rawDone,
      isDone,
      isUnlocked,
    }
  })

  const doneCount = items.filter((i) => i.isDone).length
  const missing = items.filter((i) => !i.isDone)
  const nextStep = missing[0] ?? WIZARD_STEPS[WIZARD_STEPS.length - 1]
  const maxUnlockedStepNumber = nextStep ? nextStep.stepNumber : 10

  const phases = CARETAKER_ONBOARDING_PHASES.map((phase) => ({
    ...phase,
    steps: phase.steps.map((s) => items.find((i) => i.key === s.key)),
  }))

  return {
    items,
    phases,
    doneCount,
    totalCount: items.length,
    percent: Math.round((doneCount / items.length) * 100),
    missing,
    nextStep,
    maxUnlockedStepNumber,
  }
}
