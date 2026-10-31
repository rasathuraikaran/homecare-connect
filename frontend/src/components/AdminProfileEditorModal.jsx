import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { Alert } from './Icons'
import { JOBS } from '../constants/jobs'

const RATES = [
  { type: 'HOURLY', label: 'Hourly' },
  { type: 'DAILY', label: 'Daily' },
  { type: 'MONTHLY', label: 'Monthly' },
]

const NURSING_SERVICE_CATALOG = [
  { code: 'BP_CHECK', name: 'Blood pressure checking', category: 'Health monitoring', defaultDuration: '15 minutes' },
  { code: 'GLUCOSE_CHECK', name: 'Blood glucose checking', category: 'Health monitoring', defaultDuration: '15 minutes' },
  { code: 'WOUND_DRESSING', name: 'Wound dressing', category: 'Wound care', defaultDuration: '30 minutes' },
  { code: 'URINARY_CATHETER', name: 'Urinary catheter replacement', category: 'Catheter care', defaultDuration: '30 minutes' },
  { code: 'PRESCRIBED_INJECTION', name: 'Prescribed injection', category: 'Medication', defaultDuration: '15 minutes' },
  { code: 'NG_TUBE_CARE', name: 'NG tube care', category: 'Tube care', defaultDuration: '30 minutes' },
  { code: 'BEDSIDE_NURSING', name: 'Bedside nursing care', category: 'Patient care', defaultDuration: '60 minutes' },
  { code: 'COMFORT_MASSAGE', name: 'Gentle comfort massage', category: 'Comfort care', defaultDuration: '30 minutes' },
]

const EXPERIENCE_LEVELS = [
  'Trained but limited experience',
  'Performed regularly',
  'Extensive experience',
]

const DURATIONS = ['15 minutes', '30 minutes', '45 minutes', '60 minutes', '90 minutes', '2 hours+']

export default function AdminProfileEditorModal({ userId, onClose, onSaved }) {
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    api(`/admin/users/${userId}/profile`)
      .then((data) => {
        if (!active) return
        setForm({
          fullName: data.fullName || '',
          email: data.email || '',
          phone: data.phone || '',
          role: data.role || 'CAREGIVER',
          age: data.age ?? '',
          experienceYears: data.experienceYears ?? '',
          qualification: data.qualification || '',
          jobType: data.jobType || '',
          address: data.address || '',
          available: data.available ?? true,
          listed: data.listed ?? false,
          rates: {
            HOURLY: data.rates?.HOURLY ?? '',
            DAILY: data.rates?.DAILY ?? '',
            MONTHLY: data.rates?.MONTHLY ?? '',
          },
          caretakerOnboarding: {
            dob: '',
            gender: '',
            preferredLanguage: '',
            spokenLanguages: [],
            emergencyContactName: '',
            emergencyContactRelation: '',
            emergencyContactPhone: '',
            caretakerCategory: '',
            skillSources: {},
            isNewCaregiver: false,
            workHistory: [],
            referenceName: '',
            referencePhone: '',
            educationLevel: '',
            firstAidCertified: '',
            availableDays: [],
            preferredShift: '',
            workType: '',
            livingArrangement: '',
            preferredLocations: '',
            startWhen: '',
            callWhatsAppPhone: '',
            callMethod: 'WhatsApp Video Call',
            callAvailableTimes: [],
            callNotes: '',
            consents: {},
            consented: false,
            applicationSubmitted: false,
            ...(data.caretakerOnboarding || {}),
          },
          nursingOnboarding: {
            dob: '',
            gender: '',
            preferredLanguage: '',
            spokenLanguages: [],
            emergencyContactName: '',
            emergencyContactRelation: '',
            emergencyContactPhone: '',
            nursingQualificationType: '',
            trainingInstitute: '',
            graduationYear: '',
            registrationBody: '',
            registrationNumber: '',
            registrationValidUntil: '',
            clinicalAreas: [],
            isNewHomeNurse: false,
            currentWorkStatus: '',
            workHistory: [],
            referenceName: '',
            referencePhone: '',
            services: [],
            offersLongDurationCare: false,
            additionalTraining: '',
            availableDays: [],
            preferredShift: '',
            minVisitFee: '',
            maxTravelDistance: '',
            chargesExtraForTravel: false,
            travelAllowance: '',
            availableForUrgent: false,
            providesNightServices: false,
            nightExtraCharge: '',
            multipleServicesPerVisit: true,
            providesMedicalConsumables: false,
            availableForRecurring: true,
            preferredLocations: '',
            callWhatsAppPhone: '',
            callMethod: 'WhatsApp Video Call',
            callAvailableTimes: [],
            callNotes: '',
            consents: {},
            consented: false,
            applicationSubmitted: false,
            ...(data.nursingOnboarding || {}),
          },
        })
      })
      .catch((e) => {
        if (active) setError(e.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [userId])

  function setField(key, val) {
    setForm((f) => ({ ...f, [key]: val }))
  }

  function setCtField(key, val) {
    setForm((f) => ({
      ...f,
      caretakerOnboarding: { ...f.caretakerOnboarding, [key]: val },
    }))
  }

  function setRnField(key, val) {
    setForm((f) => ({
      ...f,
      nursingOnboarding: { ...f.nursingOnboarding, [key]: val },
    }))
  }

  function toggleNurseService(catalogItem) {
    const list = form.nursingOnboarding.services || []
    const exists = list.some((s) => s.code === catalogItem.code)
    if (exists) {
      setRnField('services', list.filter((s) => s.code !== catalogItem.code))
    } else {
      setRnField('services', [
        ...list,
        {
          code: catalogItem.code,
          name: catalogItem.name,
          category: catalogItem.category,
          expectedRate: 1500,
          duration: catalogItem.defaultDuration,
          experienceLevel: 'Performed regularly',
          timesPerformed: '10–50',
          lastPerformed: 'Within 3 months',
          bringsEquipment: true,
          providesConsumables: false,
        },
      ])
    }
  }

  function updateNurseService(code, patch) {
    const list = form.nursingOnboarding.services || []
    setRnField(
      'services',
      list.map((s) => (s.code === code ? { ...s, ...patch } : s))
    )
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const cleanRates = {}
      for (const [k, v] of Object.entries(form.rates || {})) {
        if (v !== '' && v !== null && !Number.isNaN(Number(v)) && Number(v) > 0) {
          cleanRates[k] = Number(v)
        }
      }

      const payload = {
        fullName: form.fullName.trim(),
        phone: form.phone?.trim() || '',
        age: form.age === '' ? null : Number(form.age),
        experienceYears: form.experienceYears === '' ? null : Number(form.experienceYears),
        qualification: form.qualification?.trim() || null,
        jobType: form.jobType || null,
        address: form.address?.trim() || '',
        available: Boolean(form.available),
        listed: Boolean(form.listed),
        rates: cleanRates,
        caretakerOnboarding: form.jobType === 'CARETAKER' ? form.caretakerOnboarding : null,
        nursingOnboarding:
          form.jobType === 'NURSING'
            ? {
                ...form.nursingOnboarding,
                minVisitFee:
                  form.nursingOnboarding.minVisitFee === '' || form.nursingOnboarding.minVisitFee === null
                    ? null
                    : Number(form.nursingOnboarding.minVisitFee),
                services: (form.nursingOnboarding.services || []).map((s) => ({
                  ...s,
                  expectedRate: Number(s.expectedRate) > 0 ? Number(s.expectedRate) : 1000,
                })),
              }
            : null,
      }

      const updated = await api(`/admin/users/${userId}/profile`, {
        method: 'PUT',
        body: payload,
      })
      onSaved?.(updated)
      onClose()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="block admin-editor-panel" style={{ marginTop: 12, marginBottom: 16 }}>
      <div className="admin-editor-header">
        <div>
          <span className="eyebrow">Admin Profile & Onboarding Editor</span>
          <h3 style={{ margin: '2px 0 0' }}>{form ? `Manage ${form.fullName} (${form.email})` : 'Loading profile…'}</h3>
        </div>
        <button type="button" className="btn-ghost small-btn" onClick={onClose}>Close</button>
      </div>

      {error && <div className="alert" role="alert"><Alert />{error}</div>}
      {loading && <p className="muted small">Loading account and onboarding details…</p>}

      {!loading && form && (
        <form className="admin-editor-form" onSubmit={handleSubmit}>
          {/* 1. Account & Core Profile */}
          <div className="sub-card">
            <h4 className="sub-card-title">1. Account & Core Profile</h4>
            <div className="row-3">
              <label>
                Full name
                <input required value={form.fullName} onChange={(e) => setField('fullName', e.target.value)} />
              </label>
              <label>
                Phone number
                <input value={form.phone} placeholder="+94 77 123 4567" onChange={(e) => setField('phone', e.target.value)} />
              </label>
              <label>
                Email (read-only)
                <input value={form.email} disabled />
              </label>
            </div>

            {form.role === 'CAREGIVER' && (
              <>
                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    Caregiver role (job type)
                    <select value={form.jobType} onChange={(e) => setField('jobType', e.target.value)}>
                      <option value="">Not chosen yet</option>
                      {JOBS.map((j) => (
                        <option key={j.value} value={j.value}>{j.label}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Age
                    <input type="number" min="18" max="100" value={form.age} onChange={(e) => setField('age', e.target.value)} />
                  </label>
                  <label>
                    Years of experience
                    <input type="number" min="0" max="60" value={form.experienceYears} onChange={(e) => setField('experienceYears', e.target.value)} />
                  </label>
                </div>

                <div className="row" style={{ marginTop: 12 }}>
                  <label>
                    Qualification summary (shown on public card)
                    <input value={form.qualification} maxLength={255} onChange={(e) => setField('qualification', e.target.value)} />
                  </label>
                  <label>
                    Home address
                    <input value={form.address} maxLength={300} onChange={(e) => setField('address', e.target.value)} />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  {RATES.map((r) => (
                    <label key={r.type}>
                      {r.label} rate (LKR)
                      <input
                        type="number"
                        min="0"
                        step="50"
                        value={form.rates[r.type] ?? ''}
                        placeholder="Optional"
                        onChange={(e) => setField('rates', { ...form.rates, [r.type]: e.target.value })}
                      />
                    </label>
                  ))}
                </div>

                <div className="row" style={{ marginTop: 12 }}>
                  <label className="consent-row" style={{ cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={Boolean(form.available)}
                      onChange={(e) => setField('available', e.target.checked)}
                    />
                    <div>
                      <strong>Available for work</strong>
                      <span className="muted small">Caregiver is currently accepting new bookings.</span>
                    </div>
                  </label>
                  <label className="consent-row" style={{ cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={Boolean(form.listed)}
                      onChange={(e) => setField('listed', e.target.checked)}
                    />
                    <div>
                      <strong>Visible to families (Listed)</strong>
                      <span className="muted small">Show this caregiver in the public family directory.</span>
                    </div>
                  </label>
                </div>
              </>
            )}
          </div>

          {/* 2A. CARETAKER ONBOARDING */}
          {form.role === 'CAREGIVER' && form.jobType === 'CARETAKER' && (
            <div className="sub-card">
              <h4 className="sub-card-title">2. Caretaker Onboarding Details</h4>
              <div className="row-3">
                <label>
                  Date of birth
                  <input type="date" value={form.caretakerOnboarding.dob || ''} onChange={(e) => setCtField('dob', e.target.value)} />
                </label>
                <label>
                  Gender
                  <input value={form.caretakerOnboarding.gender || ''} onChange={(e) => setCtField('gender', e.target.value)} />
                </label>
                <label>
                  Preferred language
                  <input value={form.caretakerOnboarding.preferredLanguage || ''} onChange={(e) => setCtField('preferredLanguage', e.target.value)} />
                </label>
              </div>

              <div className="row-3" style={{ marginTop: 12 }}>
                <label>
                  Emergency contact name
                  <input value={form.caretakerOnboarding.emergencyContactName || ''} onChange={(e) => setCtField('emergencyContactName', e.target.value)} />
                </label>
                <label>
                  Emergency relation
                  <input value={form.caretakerOnboarding.emergencyContactRelation || ''} onChange={(e) => setCtField('emergencyContactRelation', e.target.value)} />
                </label>
                <label>
                  Emergency phone
                  <input value={form.caretakerOnboarding.emergencyContactPhone || ''} onChange={(e) => setCtField('emergencyContactPhone', e.target.value)} />
                </label>
              </div>

              <div className="row-3" style={{ marginTop: 12 }}>
                <label>
                  Caretaker category
                  <input value={form.caretakerOnboarding.caretakerCategory || ''} onChange={(e) => setCtField('caretakerCategory', e.target.value)} />
                </label>
                <label>
                  Education level
                  <input value={form.caretakerOnboarding.educationLevel || ''} onChange={(e) => setCtField('educationLevel', e.target.value)} />
                </label>
                <label>
                  First Aid / CPR status
                  <input value={form.caretakerOnboarding.firstAidCertified || ''} onChange={(e) => setCtField('firstAidCertified', e.target.value)} />
                </label>
              </div>

              <div className="row-3" style={{ marginTop: 12 }}>
                <label>
                  Preferred shift
                  <input value={form.caretakerOnboarding.preferredShift || ''} onChange={(e) => setCtField('preferredShift', e.target.value)} />
                </label>
                <label>
                  Living arrangement
                  <input value={form.caretakerOnboarding.livingArrangement || ''} onChange={(e) => setCtField('livingArrangement', e.target.value)} />
                </label>
                <label>
                  Preferred locations
                  <input value={form.caretakerOnboarding.preferredLocations || ''} onChange={(e) => setCtField('preferredLocations', e.target.value)} />
                </label>
              </div>

              <div className="row-3" style={{ marginTop: 12 }}>
                <label>
                  10-Min Call WhatsApp / Phone
                  <input value={form.caretakerOnboarding.callWhatsAppPhone || ''} onChange={(e) => setCtField('callWhatsAppPhone', e.target.value)} />
                </label>
                <label>
                  Call method
                  <input value={form.caretakerOnboarding.callMethod || ''} onChange={(e) => setCtField('callMethod', e.target.value)} />
                </label>
                <label>
                  Call notes
                  <input value={form.caretakerOnboarding.callNotes || ''} onChange={(e) => setCtField('callNotes', e.target.value)} />
                </label>
              </div>
            </div>
          )}

          {/* 2B. NURSING ONBOARDING */}
          {form.role === 'CAREGIVER' && form.jobType === 'NURSING' && (
            <>
              <div className="sub-card">
                <h4 className="sub-card-title">2. Nurse Qualifications, Registration & Emergency Contact</h4>
                <div className="row-3">
                  <label>
                    Nursing qualification
                    <input
                      value={form.nursingOnboarding.nursingQualificationType || ''}
                      placeholder="BSc in Nursing / Diploma in Nursing"
                      onChange={(e) => setRnField('nursingQualificationType', e.target.value)}
                    />
                  </label>
                  <label>
                    Training school / institute
                    <input
                      value={form.nursingOnboarding.trainingInstitute || ''}
                      onChange={(e) => setRnField('trainingInstitute', e.target.value)}
                    />
                  </label>
                  <label>
                    Graduation year
                    <input
                      value={form.nursingOnboarding.graduationYear || ''}
                      onChange={(e) => setRnField('graduationYear', e.target.value)}
                    />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    Registration body (SLMC / SLNC)
                    <input
                      value={form.nursingOnboarding.registrationBody || ''}
                      onChange={(e) => setRnField('registrationBody', e.target.value)}
                    />
                  </label>
                  <label>
                    Registration / license number
                    <input
                      value={form.nursingOnboarding.registrationNumber || ''}
                      onChange={(e) => setRnField('registrationNumber', e.target.value)}
                    />
                  </label>
                  <label>
                    License valid until
                    <input
                      type="date"
                      value={form.nursingOnboarding.registrationValidUntil || ''}
                      onChange={(e) => setRnField('registrationValidUntil', e.target.value)}
                    />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    Emergency contact name
                    <input
                      value={form.nursingOnboarding.emergencyContactName || ''}
                      onChange={(e) => setRnField('emergencyContactName', e.target.value)}
                    />
                  </label>
                  <label>
                    Emergency relation
                    <input
                      value={form.nursingOnboarding.emergencyContactRelation || ''}
                      onChange={(e) => setRnField('emergencyContactRelation', e.target.value)}
                    />
                  </label>
                  <label>
                    Emergency phone
                    <input
                      value={form.nursingOnboarding.emergencyContactPhone || ''}
                      onChange={(e) => setRnField('emergencyContactPhone', e.target.value)}
                    />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    Current work status
                    <input
                      value={form.nursingOnboarding.currentWorkStatus || ''}
                      onChange={(e) => setRnField('currentWorkStatus', e.target.value)}
                    />
                  </label>
                  <label>
                    Reference name
                    <input
                      value={form.nursingOnboarding.referenceName || ''}
                      onChange={(e) => setRnField('referenceName', e.target.value)}
                    />
                  </label>
                  <label>
                    Reference phone
                    <input
                      value={form.nursingOnboarding.referencePhone || ''}
                      onChange={(e) => setRnField('referencePhone', e.target.value)}
                    />
                  </label>
                </div>
              </div>

              <div className="sub-card">
                <h4 className="sub-card-title">3. Home Nursing Services & Per-Visit Rates (Admin Editable)</h4>
                <p className="muted small" style={{ marginBottom: 10 }}>
                  Select or deselect services and adjust verified visit rates, duration, and experience level.
                </p>
                <div className="stack">
                  {NURSING_SERVICE_CATALOG.map((item) => {
                    const selected = (form.nursingOnboarding.services || []).find((s) => s.code === item.code)
                    return (
                      <div key={item.code} className="sub-card" style={{ margin: 0, background: selected ? '#f0fdfa' : '#fff' }}>
                        <label className="admin-service-toggle" style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontWeight: 600 }}>
                          <input
                            type="checkbox"
                            checked={Boolean(selected)}
                            onChange={() => toggleNurseService(item)}
                          />
                          <span>{item.name}</span>
                          <span className="status-chip off" style={{ marginLeft: 'auto' }}>{item.category}</span>
                        </label>

                        {selected && (
                          <div className="row-3" style={{ marginTop: 10 }}>
                            <label>
                              Expected payment (LKR)
                              <input
                                type="number"
                                min="1"
                                step="50"
                                value={selected.expectedRate ?? ''}
                                onChange={(e) => updateNurseService(item.code, { expectedRate: e.target.value })}
                              />
                            </label>
                            <label>
                              Expected duration
                              <select
                                value={selected.duration || item.defaultDuration}
                                onChange={(e) => updateNurseService(item.code, { duration: e.target.value })}
                              >
                                {DURATIONS.map((d) => (
                                  <option key={d} value={d}>{d}</option>
                                ))}
                              </select>
                            </label>
                            <label>
                              Experience level
                              <select
                                value={selected.experienceLevel || 'Performed regularly'}
                                onChange={(e) => updateNurseService(item.code, { experienceLevel: e.target.value })}
                              >
                                {EXPERIENCE_LEVELS.map((lvl) => (
                                  <option key={lvl} value={lvl}>{lvl}</option>
                                ))}
                              </select>
                            </label>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>

              <div className="sub-card">
                <h4 className="sub-card-title">4. Travel, Night Visits & 10-Min Call</h4>
                <div className="row-3">
                  <label>
                    Minimum home visit fee (LKR)
                    <input
                      type="number"
                      min="0"
                      step="100"
                      value={form.nursingOnboarding.minVisitFee ?? ''}
                      onChange={(e) => setRnField('minVisitFee', e.target.value)}
                    />
                  </label>
                  <label>
                    Max travel distance
                    <input
                      value={form.nursingOnboarding.maxTravelDistance || ''}
                      placeholder="10 km"
                      onChange={(e) => setRnField('maxTravelDistance', e.target.value)}
                    />
                  </label>
                  <label>
                    Travel allowance (if extra)
                    <input
                      value={form.nursingOnboarding.travelAllowance || ''}
                      placeholder="Rs. 60 per km"
                      onChange={(e) => setRnField('travelAllowance', e.target.value)}
                    />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    Night visit extra charge
                    <input
                      value={form.nursingOnboarding.nightExtraCharge || ''}
                      placeholder="Rs. 1,000"
                      onChange={(e) => setRnField('nightExtraCharge', e.target.value)}
                    />
                  </label>
                  <label>
                    Preferred shift
                    <input
                      value={form.nursingOnboarding.preferredShift || ''}
                      onChange={(e) => setRnField('preferredShift', e.target.value)}
                    />
                  </label>
                  <label>
                    Preferred locations
                    <input
                      value={form.nursingOnboarding.preferredLocations || ''}
                      onChange={(e) => setRnField('preferredLocations', e.target.value)}
                    />
                  </label>
                </div>

                <div className="row-3" style={{ marginTop: 12 }}>
                  <label>
                    10-Min Call WhatsApp / Phone
                    <input
                      value={form.nursingOnboarding.callWhatsAppPhone || ''}
                      onChange={(e) => setRnField('callWhatsAppPhone', e.target.value)}
                    />
                  </label>
                  <label>
                    Call method
                    <input
                      value={form.nursingOnboarding.callMethod || ''}
                      onChange={(e) => setRnField('callMethod', e.target.value)}
                    />
                  </label>
                  <label>
                    Call notes
                    <input
                      value={form.nursingOnboarding.callNotes || ''}
                      onChange={(e) => setRnField('callNotes', e.target.value)}
                    />
                  </label>
                </div>
              </div>
            </>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button type="button" className="btn-ghost" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={saving}>
              {saving ? 'Saving changes…' : 'Save all changes'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
