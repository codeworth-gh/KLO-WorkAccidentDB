package actors

import play.api.libs.json.{JsNull, JsNumber, JsObject, JsString, JsUndefined, JsValue}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Utility trait for classes that scrape JSON data.
 */
trait JsonScraper  {
  private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val isoDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val ldtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  
  protected def safeExtractStr(obj: JsObject, key: String): Option[String] = {
    safeExtractValue(obj, key).map {
      case jsStr: JsString => jsStr.value.trim
      case jsNum: JsNumber => jsNum.value.toString
      case v => v.toString
    }
  }
  
  protected def safeExtractDate(obj: JsObject, key: String): Option[LocalDate] = {
    safeExtractStr(obj, key).map(str =>
      val dateOnlyPart = str.trim.takeWhile(_ != ' ')
      try {
        LocalDate.parse(dateOnlyPart, isoDateFmt)
      } catch {
        case e: java.time.format.DateTimeParseException => LocalDate.parse(dateOnlyPart, dateFmt)
      }
    )
  }
  
  protected def safeExtractLong(obj: JsObject, key: String): Option[Long] = {
    safeExtractValue(obj, key).flatMap {
      case jsNum: JsNumber => Some(jsNum.value.toLong)
      case jsStr: JsString => try {
        Some(jsStr.value.trim.toLong)
      } catch {
        case nfe: NumberFormatException =>
          play.api.Logger(this.getClass).warn(s"NumberFormatException while extracting Int from $obj.$key='${jsStr.value}'")
          None
      }
      case v =>
        play.api.Logger(this.getClass).warn(s"Cannot extract Int from $obj.$key='$v'")
        None
    }
  }
  
  protected def safeExtractValue(obj: JsObject, key: String): Option[JsValue] = {
    if (!obj.keys(key)) return None
    val jsValue = obj(key)
    if (jsValue == JsNull) return None
    if (jsValue == JsUndefined) return None
    Some(obj(key))
  }
}
