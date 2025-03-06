package models

import models.LongRunningProcessStatus.Pending

import java.util.UUID

enum LongRunningProcessStatus {
  case Pending, Started, Done, Error
}

case class LongRunningProcessMonitor(
                                    id: String,
                                    status: LongRunningProcessStatus,
                                    message:Option[String]
                                    )

object LongRunningProcessMonitor {
  def create( m:Option[String] ): LongRunningProcessMonitor = LongRunningProcessMonitor( UUID.randomUUID().toString, Pending, m)
  def create(): LongRunningProcessMonitor = create(None)
}
