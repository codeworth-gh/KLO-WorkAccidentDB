package models

import java.time.{LocalDate, LocalDateTime}

/**
 * A single warrant, harvested from the .gov site.
 */
case class SafetyWarrant(
                        id: Long,
                        sentDate: LocalDate,
                        // Operator or site id (prob. a number)
                        operatorTextId: String,
                        // Operator or site name
                        operatorName:String,
                        cityName:String,
                        executorName:String,
                        categoryName:String,
                        felony:String,
                        law:String,
                        clause:String,
                        scrapeDate:LocalDateTime,
                        /** Id of business entity acting as entrepreneur */
                        kloOperatorId:Option[Long],
                        /** Id of business entity acting as constructor */
                        kloExecutorId:Option[Long],
                        /** Id of the industry in our db. */
                        kloIndustryId:Option[Int]
                        )


case class BusinessEntityMapping(
                                id:Long,
                                name:String,
                                bizEntId:Long
                                )

case class IndustryMapping(
                            id:Long,
                            name:String,
                            industryId:Int
                          )