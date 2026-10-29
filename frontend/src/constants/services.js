// What we tell care seekers about each kind of care, on the "What we provide" page.
// Change the words here and the page follows. Keep the keys the same as the backend JobType values.
export const SERVICES = {
  CARETAKER: {
    title: 'Caretaker',
    headline: 'Everyday help at home',
    summary: 'Someone to look after daily routines and keep your family member company, so they are comfortable and not alone.',
    includes: ['Help with daily routines around the home', 'Company and conversation', 'Support with meals, moving around and small errands'],
    goodFor: 'Elderly parents or relatives who manage mostly on their own but need an extra pair of hands.',
  },
  NURSING: {
    title: 'Nurse',
    headline: 'Medical care at home',
    summary: 'A qualified nurse for care that needs medical knowledge, in the comfort of your own home.',
    includes: ['Giving medication as prescribed', 'Wound care and dressings', 'Watching health and reporting changes'],
    goodFor: 'Someone coming home from hospital, or living with a condition that needs regular medical attention.',
  },
  PHYSIOTHERAPIST: {
    title: 'Physiotherapist',
    headline: 'Rehabilitation at home',
    summary: 'A physiotherapist who helps the body recover strength and movement, with exercises you can keep doing between visits.',
    includes: ['Exercises for strength, balance and mobility', 'Recovery after surgery, injury or illness', 'Advice for moving safely and easing pain'],
    goodFor: 'Anyone getting back on their feet after an operation, a fall or a long illness.',
  },
}

export const STEPS = [
  { title: 'Choose the kind of care', text: 'Pick a caretaker, a nurse or a physiotherapist and see who is available.' },
  { title: 'Pick someone and press Request', text: 'Give your phone number and the address where care is needed. Nothing is charged at this step.' },
  { title: 'Our team calls you', text: 'We arrange the visit with the caregiver and keep you updated. Follow it under "Your requests".' },
]

export const GOOD_TO_KNOW = [
  'Each caregiver lists their own charges on their card, so you can compare before you ask.',
  'A mark such as "NIC verified" means our team checked that document and approved it.',
  'Your phone number and address are only seen by our team. They are used to arrange the visit and never shown to other families.',
]
