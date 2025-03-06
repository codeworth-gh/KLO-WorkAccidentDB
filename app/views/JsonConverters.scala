package views

import models.{Citizenship, ImportMonitor, ImportStatus, Industry, InjuryCause, LongRunningProcessMonitor, LongRunningProcessStatus, Region, RelationToAccident, Sanction}
import play.api.libs.json.{Format, JsString, JsValue, Json, OFormat, OWrites, Writes}

object JsonConverters {
  implicit val regionFmt: OFormat[Region] = Json.format[Region]
  implicit val injuryCauseFmt: OFormat[InjuryCause] = Json.format[InjuryCause]
  implicit val industryFmt: OFormat[Industry] = Json.format[Industry]
  implicit val citizenshipFmt: OFormat[Citizenship] = Json.format[Citizenship]
  implicit val relationToAccidentsFmt: OFormat[RelationToAccident] = Json.format[RelationToAccident]
  implicit val sanctionFmt: OFormat[Sanction] = Json.format[Sanction]
  
  implicit val importStatusWrt:Writes[ImportStatus] = new Writes[ImportStatus](){
    override def writes(s:ImportStatus):JsValue = JsString(s.toString)
  }
  
  implicit val importMonitorWrt: OWrites[ImportMonitor] = Json.writes[ImportMonitor]
  
  implicit val longRunningProcessStatusWrt: Writes[LongRunningProcessStatus] = new Writes[LongRunningProcessStatus]() {
    override def writes(s: LongRunningProcessStatus): JsValue = JsString(s.toString)
  }
  
  implicit val longRunningProcessMonitorWrt: OWrites[LongRunningProcessMonitor] = Json.writes[LongRunningProcessMonitor]
}
