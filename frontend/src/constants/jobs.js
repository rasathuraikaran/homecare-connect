// Keep in sync with the backend JobType enum
export const JOBS = [
  { value: 'CARETAKER', label: 'Caretaker', plural: 'Caretakers', desc: 'Daily help, companionship, home support' },
  { value: 'NURSING', label: 'Nursing', plural: 'Nurses', desc: 'Medical care, medication, wound care' },
  { value: 'PHYSIOTHERAPIST', label: 'Physiotherapist', plural: 'Physiotherapists', desc: 'Rehabilitation, mobility, exercise therapy' },
]

export const jobLabel = (value) => JOBS.find((j) => j.value === value)?.label
