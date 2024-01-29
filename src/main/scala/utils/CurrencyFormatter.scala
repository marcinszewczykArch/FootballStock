package utils

import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

  val toEuroString: Any => String = euroFormatter.format

  private val euroFormatter = {
    val formatter = java.text.NumberFormat.getCurrencyInstance
    val de        = Currency.getInstance(new Locale("de", "DE"))
    formatter.setCurrency(de)
    formatter
  }

}
