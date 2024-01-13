package http.security

sealed trait RoleSelection extends Product with Serializable

object RoleSelection {

  final case class Any(x: String, xs: String*) extends RoleSelection {
    lazy val toSet: Set[String] = Set(x) ++ xs.toSet
  }

  final case class All(x: String, xs: String*) extends RoleSelection {
    lazy val toSet: Set[String] = Set(x) ++ xs.toSet
  }

  case object None extends RoleSelection

  implicit class RoleSelectionOps(roleSelection: RoleSelection) {

    def isAppropriateFor(authorities: Option[List[String]]): Boolean = roleSelection match {
      case RoleSelection.None                   => true
      case selection @ RoleSelection.All(_, _*) =>
        selection.toSet.forall(authorities.getOrElse(List.empty).contains)
      case selection @ RoleSelection.Any(_, _*) =>
        selection.toSet.exists(authorities.getOrElse(List.empty).contains)
    }

  }

}

object Roles {
  val Admin = "ADMIN_ROLE"
  val User  = "USER_ROLE"
}
