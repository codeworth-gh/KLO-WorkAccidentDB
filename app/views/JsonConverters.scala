package views

import models.{Citizenship, Industry, InjuryCause, Region}
import play.api.libs.json.Json

object JsonConverters {
  implicit val regionFmt = Json.format[Region]
  implicit val injuryCauseFmt = Json.format[InjuryCause]
  implicit val industryFmt = Json.format[Industry]
  implicit val citizenshipFmt = Json.format[Citizenship]
  
}
