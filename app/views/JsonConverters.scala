package views

import models.{Citizenship, Industry, InjuryCause, Region, RelationToAccident, Sanction}
import play.api.libs.json.{Json, OFormat}

object JsonConverters {
  implicit val regionFmt: OFormat[Region] = Json.format[Region]
  implicit val injuryCauseFmt: OFormat[InjuryCause] = Json.format[InjuryCause]
  implicit val industryFmt: OFormat[Industry] = Json.format[Industry]
  implicit val citizenshipFmt: OFormat[Citizenship] = Json.format[Citizenship]
  implicit val relationToAccidentsFmt: OFormat[RelationToAccident] = Json.format[RelationToAccident]
  implicit val sanctionFmt: OFormat[Sanction] = Json.format[Sanction]
  
}
