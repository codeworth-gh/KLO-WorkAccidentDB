package models

import java.time.LocalDateTime
import java.util.UUID

enum ImportStatus {
  case Pending, Started, Done, Error
}

case class ImportMonitor(
                       id: String,
                       started: LocalDateTime,
                       ended: Option[LocalDateTime],
                       status: ImportStatus,
                       added: Int,
                       existed: Int,
                       ignored: Int,
                       errorCount: Int,
                       originalFilename:String,
                       message:Option[String]
                    )

object ImportMonitors {
  def create(filename:String):ImportMonitor = ImportMonitor(UUID.randomUUID().toString,
    LocalDateTime.now(), None, ImportStatus.Pending, 0,0,0,0,filename, None )
}