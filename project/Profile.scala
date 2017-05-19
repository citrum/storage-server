// profile you’re going to build against
case class Profile(name: String)

object Profile {
  val local = Profile("local")
  val jenkins = Profile("jenkins")
  val prod = Profile("prod")
}
