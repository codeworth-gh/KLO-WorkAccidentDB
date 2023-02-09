package models

import java.time.{LocalDate, LocalDateTime}

/**
 * A sanction that an authority can apply on a BusinessEntity.
 */
case class Sanction(
                   id: Long,
                   businessEntityId: Long,
                   authority: String,
                   sanctionType: String,
                   reason: String,
                   applicationDate: LocalDate,
                   remarks: String
                   )
