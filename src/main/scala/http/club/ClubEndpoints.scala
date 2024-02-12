package http.club

import http.BaseEndpoint.baseEndpoint
import http.club.domain.{ClubPlayersResponse, ClubProfileResponse, ClubSearchResponse}
import http.player.domain.{MarketValueHistoryResponse, PlayerProfileResponse, PlayerSearchResponse}
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object ClubEndpoints {

  lazy val endpoints: List[AppEndpointSecretWithError[_, _]] = List(
    getClub,
    getClubSearch,
    getClubPlayers
  )

  lazy val getClub: AppEndpointSecretWithError[Int, ClubProfileResponse] = baseEndpoint
    .get
    .in("club")
    .in(query[Int]("clubId"))
    .summary("Get club profile by id")
    .description("Scan DB to find club profile, if not exist make call to Transfermatrk api")
    .tag("Club")
    .out(jsonBody[ClubProfileResponse])

  lazy val getClubSearch: AppEndpointSecretWithError[String, ClubSearchResponse] = baseEndpoint
    .get
    .in("clubSearch")
    .in(query[String]("clubName"))
    .summary("Search clubs by given name")
    .description("Request Transfermatrk api to search club by given name")
    .tag("Club")
    .out(jsonBody[ClubSearchResponse])

  lazy val getClubPlayers: AppEndpointSecretWithError[Int, ClubPlayersResponse] = baseEndpoint
    .get
    .in("clubPlayers")
    .in(query[Int]("clubId"))
    .summary("Get club players by id")
    .description("Request Transfermatrk api to search club players")
    .tag("Club")
    .out(jsonBody[ClubPlayersResponse])

}
