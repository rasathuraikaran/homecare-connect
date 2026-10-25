import { Search, Stethoscope } from '../components/Icons'

export const ROLES = [
  { value: 'CARE_SEEKER', label: 'Care seeker', desc: 'I need a caregiver', icon: Search },
  { value: 'CAREGIVER', label: 'Caregiver', desc: 'I provide care', icon: Stethoscope },
]

export default function RoleTabs({ value, onChange, large = false }) {
  return (
    <div className={large ? 'roles large' : 'roles'} role="radiogroup" aria-label="Account type">
      {ROLES.map(({ value: v, label, desc, icon: Icon }) => (
        <button key={v} type="button" role="radio" data-theme={v === 'CARE_SEEKER' ? 'seeker' : 'caregiver'} aria-checked={value === v}
                className={value === v ? 'role active' : 'role'} onClick={() => onChange(v)}>
          <Icon />
          <span><strong>{label}</strong><em>{desc}</em></span>
        </button>
      ))}
    </div>
  )
}
