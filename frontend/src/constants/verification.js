// Keep in sync with the backend VerificationStatus and DocumentStatus enums.
// What each job can upload, and what is mandatory, comes from the server, so it is not repeated here.
export const OVERALL_LABEL = {
  NOT_REQUIRED: 'No documents required',
  INCOMPLETE: 'Documents needed',
  IN_REVIEW: 'Waiting for review',
  ACTION_NEEDED: 'Action needed',
  VERIFIED: 'Verified',
}
export const OVERALL_TONE = { NOT_REQUIRED: 'off', INCOMPLETE: 'wait', IN_REVIEW: 'done', ACTION_NEEDED: 'bad', VERIFIED: 'ok' }

export const DOC_LABEL = { PENDING: 'Waiting for review', APPROVED: 'Approved', REJECTED: 'Rejected' }
export const DOC_TONE = { PENDING: 'wait', APPROVED: 'ok', REJECTED: 'bad' }

// What the caregiver is told at the top of the card
export const HEADLINE = {
  NOT_REQUIRED: ['Add your documents (optional)', 'Families can already see you. A document our team approves earns a verified mark on your card and builds trust.'],
  INCOMPLETE: ['Add your documents to appear to families', 'Upload the documents marked required. Our team checks them before families can see you.'],
  IN_REVIEW: ['Waiting for review', 'Our team is checking your documents. Families can see you as soon as they are approved.'],
  ACTION_NEEDED: ['A document needs your attention', 'Read the reason under the rejected document, then upload it again.'],
  VERIFIED: ['You are verified', 'Your required documents are approved, so families can find you when they search.'],
}

/** True when families can see the caregiver in this state. */
export const isListed = (status) => status === 'VERIFIED' || status === 'NOT_REQUIRED'

export const MAX_DOCUMENT = 5 * 1024 * 1024
export const DOCUMENT_FILE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
