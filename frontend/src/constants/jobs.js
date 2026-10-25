// Keep in sync with the backend JobType enum
export const JOBS = [
  { value: 'CARETAKER', label: 'Caretaker', desc: 'Daily help, companionship, home support' },
  { value: 'NURSING', label: 'Nursing', desc: 'Medical care, medication, wound care' },
  { value: 'PHYSIOTHERAPIST', label: 'Physiotherapist', desc: 'Rehabilitation, mobility, exercise therapy' },
]

export const jobLabel = (value) => JOBS.find((j) => j.value === value)?.label
