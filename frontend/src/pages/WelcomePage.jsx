import { useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import RoleTabs from './RoleTabs'

export default function WelcomePage() {
  const navigate = useNavigate()
  return (
    <AuthLayout
      title="Welcome to HomeCare Connect"
      subtitle="First, tell us who you are. You can sign in or create an account on the next step."
      footer="Your choice sets up the right experience for you."
    >
      <RoleTabs large value={null} onChange={(role) => navigate(`/login?role=${role}`)} />
    </AuthLayout>
  )
}
