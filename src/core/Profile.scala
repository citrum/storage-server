package core


object Profile extends Enumeration {

  type Profile = Value

  val Local = Value("local")
  val Jenkins = Value("jenkins")
  val Prod = Value("prod")

  private var _current: Value = null
  def current: Value = {
    if (_current == null) _current = Prod
    _current
  }

  def init(profile: Profile) {
    require(_current == null, "Profile already initialized")
    _current = profile
  }

  def isLocal: Boolean = current == Local
  def isJenkins: Boolean = current == Jenkins
  def isProd: Boolean = current == Prod

  def isLocalOrJenkins: Boolean = current == Local || current == Jenkins
}
