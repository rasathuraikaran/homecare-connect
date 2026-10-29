// What makes a caregiver profile "complete". `focus` is the profile-form section that fixes it.
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
}
export const GENERIC_TIP = 'Keep your charges and details up to date so families always see the latest.'
