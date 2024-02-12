package utils

import game.GameException

object Type {
  type ErrorOr[A] = Either[GameException, A]
}
