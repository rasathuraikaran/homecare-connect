export default function Avatar({ user, initials, size = 36 }) {
  const style = { width: size, height: size, fontSize: size * 0.34 }
  return user?.photoUrl
    ? <img className="avatar" style={style} src={user.photoUrl} alt="" />
    : <span className="avatar" style={style}>{initials}</span>
}
