package views

import models.{Citizenship, Industry, InjuryCause, Region, RelationToAccident, Sanction}
import play.api.libs.json.Json

object JsonConverters {
  implicit val regionFmt = Json.format[Region]
  implicit val injuryCauseFmt = Json.format[InjuryCause]
  implicit val industryFmt = Json.format[Industry]
  implicit val citizenshipFmt = Json.format[Citizenship]
  implicit val relationToAccidentsFmt = Json.format[RelationToAccident]
  implicit val sanctionFmt = Json.format[Sanction]
  
}
