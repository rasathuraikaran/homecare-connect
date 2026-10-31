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
  NURSING: {
    title: 'Your tailored Home Nurse onboarding requirements',
    items: [
      'Nursing qualification & SLNC / SLMC professional registration details',
      'Home nursing procedures you perform & your expected LKR payout per visit',
      'NIC & Nursing qualification certificate verification',
      'Minimum home visit fee, travel distance, night visit & urgent booking rules',
      '10-minute introductory WhatsApp / phone call with our clinical coordinator',
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

// ============================================================================
// NURSING-ONLY (jobType === 'NURSING') 10-Step, 4-Phase Onboarding Flow
// ============================================================================

export const HOME_NURSING_SERVICES = [
  {
    code: 'BP_CHECKING',
    name: 'Blood pressure checking',
    category: 'Health monitoring',
    defaultDuration: '15 minutes',
    suggestedRate: '1000',
  },
  {
    code: 'GLUCOSE_CHECKING',
    name: 'Blood glucose checking',
    category: 'Health monitoring',
    defaultDuration: '15 minutes',
    suggestedRate: '1000',
  },
  {
    code: 'WOUND_DRESSING',
    name: 'Wound dressing',
    category: 'Wound care',
    defaultDuration: '30 minutes',
    suggestedRate: '1800',
  },
  {
    code: 'CATHETER_REPLACEMENT',
    name: 'Urinary catheter replacement',
    category: 'Catheter care',
    defaultDuration: '30 minutes',
    suggestedRate: '2000',
  },
  {
    code: 'PRESCRIBED_INJECTION',
    name: 'Prescribed injection',
    category: 'Medication',
    defaultDuration: '20 minutes',
    suggestedRate: '1200',
  },
  {
    code: 'NG_TUBE_CARE',
    name: 'NG tube care',
    category: 'Tube care',
    defaultDuration: '30 minutes',
    suggestedRate: '2000',
  },
  {
    code: 'BEDSIDE_NURSING',
    name: 'Bedside nursing care',
    category: 'Patient care',
    defaultDuration: '60 minutes',
    suggestedRate: '2500',
  },
  {
    code: 'COMFORT_MASSAGE',
    name: 'Gentle comfort massage',
    category: 'Comfort care',
    defaultDuration: '30 minutes',
    suggestedRate: '1800',
  },
]

export const NURSING_SERVICE_EXPERIENCE_LEVELS = [
  'Trained but limited experience',
  'Performed regularly',
  'Extensive experience',
]

export const NURSING_TIMES_PERFORMED = [
  'Under 10',
  '10–50',
  '50+',
]

export const NURSING_LAST_PERFORMED = [
  'Within 3 months',
  'Within 1 year',
  'More than 1 year ago',
]

export const NURSING_DURATIONS = [
  '15 minutes',
  '20 minutes',
  '30 minutes',
  '45 minutes',
  '60 minutes',
]

export const NURSING_QUALIFICATION_TYPES = [
  'Diploma in Nursing (3-Year General Nursing)',
  'BSc in Nursing (Honours / General)',
  'Post-Basic Nursing Diploma',
  'Nursing & Midwifery Diploma',
  'Enrolled Nurse / Assistant Nurse Certificate',
]

export const NURSING_REGISTRATION_BODIES = [
  'Sri Lanka Nursing Council (SLNC)',
  'Sri Lanka Medical Council (SLMC)',
  'Private Health Services Regulatory Council (PHSRC)',
  'Registration in progress / Pending',
]

export const NURSING_CLINICAL_AREAS = [
  'General Medical / Surgical Ward',
  'ICU / HDU / Critical Care',
  'Emergency / ETU',
  'Post-Operative & Wound Care',
  'Geriatric & Palliative Care',
  'Community / Home Nursing',
]

export const NURSING_WORK_STATUSES = [
  'Government hospital nurse (available for part-time home visits)',
  'Private hospital / clinic nurse',
  'Full-time home care nurse',
  'Former / retired hospital nurse',
]

export const NURSING_TRAVEL_DISTANCES = [
  '5 km',
  '10 km',
  '20 km',
  '30+ km',
]

export const NURSING_CONSENTS = [
  {
    key: 'accurateInfo',
    label: 'Self-reported competency & pricing accuracy',
    desc: 'I confirm that my nursing qualifications, procedure experience levels, and expected rates are accurate and subject to clinical verification before activation.',
  },
  {
    key: 'clinicalScope',
    label: 'Clinical scope of practice & prescription adherence',
    desc: 'I agree to perform invasive procedures and administer medications strictly according to a valid doctor’s prescription and safe nursing protocols.',
  },
  {
    key: 'backgroundCheck',
    label: 'Credential & identity verification consent',
    desc: 'I authorise HomeCare Connect to verify my NIC, Nursing certificate, SLNC/SLMC registration, and professional references.',
  },
  {
    key: 'termsPrivacy',
    label: 'Patient confidentiality & privacy consent',
    desc: 'I commit to strict patient medical confidentiality, infection control standards, and HomeCare Connect privacy terms.',
  },
]

const NURSING_STORAGE_PREFIX = 'hc_nursing_onboarding_v1_'

export function loadNursingExtras(userKey) {
  if (!userKey) return {}
  try {
    const raw = localStorage.getItem(`${NURSING_STORAGE_PREFIX}${userKey}`)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function saveNursingExtras(userKey, patch) {
  if (!userKey) return {}
  try {
    const current = loadNursingExtras(userKey)
    const next = { ...current, ...patch, lastSavedAt: new Date().toISOString() }
    localStorage.setItem(`${NURSING_STORAGE_PREFIX}${userKey}`, JSON.stringify(next))
    return next
  } catch {
    return patch
  }
}

function hasNursingIdentityDoc(verification, extras) {
  const docs = verification?.documents ?? []
  return docs.some((d) => d.type === 'NIC' && !!d.status) || Boolean(extras?.identityStepSaved)
}

function hasNursingCertificateDoc(verification, extras) {
  const docs = verification?.documents ?? []
  return docs.some((d) => d.type === 'NURSING_CERTIFICATE' && !!d.status) || Boolean(extras?.certificatesReviewed)
}

function hasAllNursingConsents(extras) {
  const c = extras?.consents ?? {}
  return NURSING_CONSENTS.every((item) => !!c[item.key])
}

export const NURSING_ONBOARDING_PHASES = [
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
    id: 'clinical-profile',
    phaseNumber: 2,
    title: 'Clinical profile',
    steps: [
      {
        stepNumber: 3,
        key: 'job',
        focus: 'job',
        label: 'Step 3 – Nursing qualifications & registration',
        shortTitle: 'Qualifications & registration',
        action: 'Nursing diploma/degree & SLNC/SLMC registration',
        done: (p, _v, extras) =>
          p?.jobType === 'NURSING' &&
          !!extras?.nursingQualificationType &&
          !!extras?.registrationBody &&
          !!extras?.credentialsStepSaved,
      },
      {
        stepNumber: 4,
        key: 'experience',
        focus: 'experience',
        label: 'Step 4 – Employment experience',
        shortTitle: 'Employment experience',
        action: 'Hospital / clinical work history & references (optional)',
        done: (p, _v, extras) => Boolean(extras?.experienceStepSaved) || p?.experienceYears != null,
      },
      {
        stepNumber: 5,
        key: 'skills',
        focus: 'skills',
        label: 'Step 5 – Your home nursing services & rates',
        shortTitle: 'Home nursing services & rates',
        action: 'Select home nursing procedures & expected LKR payout per visit',
        done: (_p, _v, extras) =>
          Boolean(
            extras?.servicesStepSaved &&
              Array.isArray(extras?.services) &&
              extras.services.length > 0 &&
              extras.services.every((s) => Number(s.expectedRate) > 0)
          ),
      },
    ],
  },
  {
    id: 'verification',
    phaseNumber: 3,
    title: 'Verification & logistics',
    steps: [
      {
        stepNumber: 6,
        key: 'identity_docs',
        focus: 'identity_docs',
        label: 'Step 6 – Identity documents',
        shortTitle: 'Identity documents',
        action: 'Upload your National Identity Card (NIC)',
        done: (_p, v, extras) => hasNursingIdentityDoc(v, extras),
      },
      {
        stepNumber: 7,
        key: 'certificates',
        focus: 'certificates',
        label: 'Step 7 – Nursing certificates & training',
        shortTitle: 'Nursing certificates',
        action: 'Upload Nursing qualification certificate & specialist training',
        done: (_p, v, extras) => hasNursingCertificateDoc(v, extras),
      },
      {
        stepNumber: 8,
        key: 'charges',
        focus: 'charges',
        label: 'Step 8 – Availability & travel preferences',
        shortTitle: 'Availability & travel',
        action: 'Min visit payment, travel distance, night visits & urgent bookings',
        done: (_p, _v, extras) =>
          Boolean(
            extras?.availabilityStepSaved &&
              Number(extras?.minVisitFee) > 0 &&
              !!extras?.maxTravelDistance &&
              (extras?.availableDays?.length ?? 0) > 0
          ),
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
        label: 'Step 9 – 10-min WhatsApp / phone call',
        shortTitle: '10-min WhatsApp call',
        action: 'Share your daily free time for a 10-min verification call',
        done: (_p, _v, extras) =>
          Boolean(extras?.interviewScheduled && extras?.callWhatsAppPhone?.trim() && (extras?.callAvailableTimes?.length ?? 0) > 0),
      },
      {
        stepNumber: 10,
        key: 'review',
        focus: 'review',
        label: 'Step 10 – Review and submit',
        shortTitle: 'Review & submit',
        action: 'Review services, rates & accept clinical agreements',
        done: (p, _v, extras) => (hasAllNursingConsents(extras) || Boolean(extras?.applicationSubmitted)) && !!p?.address?.trim(),
      },
    ],
  },
]

export const NURSING_ONBOARDING_STEPS = NURSING_ONBOARDING_PHASES.flatMap((phase) =>
  phase.steps.map((s) => ({ ...s, phaseId: phase.id, phaseNumber: phase.phaseNumber, phaseTitle: phase.title }))
)

export const NURSING_WIZARD_STEPS = NURSING_ONBOARDING_STEPS.filter((s) => s.stepNumber >= 2)

export function nursingCompleteness(profile, verification = null, extras = {}) {
  let previousAllDone = true
  const items = NURSING_ONBOARDING_STEPS.map((step) => {
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
  const nextStep = missing[0] ?? NURSING_WIZARD_STEPS[NURSING_WIZARD_STEPS.length - 1]
  const maxUnlockedStepNumber = nextStep ? nextStep.stepNumber : 10

  const phases = NURSING_ONBOARDING_PHASES.map((phase) => ({
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
