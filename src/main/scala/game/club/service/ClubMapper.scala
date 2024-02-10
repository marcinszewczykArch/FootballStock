package game.club.service

import game.club.client.domain.FetchedClub
import game.club.client.domain.FetchedClubSimple
import game.club.service.domain.{ClubProfile, ClubSimple}
import game.errors.GameException
import game.errors.GameException.JsonDecodingException
import game.player.client.domain._
import game.player.service.domain._
import io.circe.Json
import utils.Parser.toBigDecimalOrZero
import utils.Parser.toInstantOrFarPastForDate
import utils.Parser.toInstantOrFarPastForUpdateAt

object ClubMapper {

  val fetchedClubSimpleToClubSimple: FetchedClubSimple => ClubSimple = ???
//  {
//    case FetchedPlayerSimple(id, name, position, club, age, nationalities, marketValue) =>
//      PlayerSimple(
//        id = PlayerId(id.getOrElse(0)),
//        name = name.getOrElse("-"),
//        position = position.getOrElse("-"),
//        club = club.flatMap(_.name).getOrElse("-"),
//        age = age.getOrElse("-"),
//        nationalities = nationalities.getOrElse(Nil),
//        marketValue = toBigDecimalOrZero(marketValue)
//      )
//  }

  val fetchedClubToClub: FetchedClub => ClubProfile = ???
//  {
//    case FetchedPlayerProfile(
//          id,
//          url,
//          name,
//          description,
//          imageURL,
//          dateOfBirth,
//          citizenship,
//          isRetired,
//          position,
//          club,
//          marketValue,
//          updatedAt
//        ) =>
//      PlayerProfile(
//        id = PlayerId(id.flatMap(_.toIntOption).getOrElse(0)),
//        url = url.getOrElse("-"),
//        name = name.getOrElse("-"),
//        description = description.getOrElse("-"),
//        imageURL = imageURL.getOrElse("-"),
//        dateOfBirth = dateOfBirth.getOrElse("-"),
//        citizenship = citizenship.getOrElse(Nil).toList,
//        isRetired = isRetired.getOrElse(true),
//        position = position
//          .map { case FetchedPlayerPosition(main, others) =>
//            PlayerPosition(main.getOrElse("-"), others.getOrElse(Nil))
//          }
//          .getOrElse(PlayerPosition.empty),
//        clubId = club.flatMap(_.id).getOrElse(0),
//        club = club.flatMap(_.name).getOrElse("-"),
//        marketValue = toBigDecimalOrZero(marketValue),
//        updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
//      )
//
//  }

  val jsonToFetchedClub: Json => Either[GameException, FetchedClub] =
    _.as[FetchedClub].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

}
