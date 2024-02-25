package models

import com.github.jferard.fastods.TableCellWalker
import controllers.PublicCtrl.integerDataStyle

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.Date

class Column[T](val name: String, writer: (T, TableCellWalker) => Any) {
  def write(t: T, w: TableCellWalker): Any = writer(t, w)
}

object Column {
  def apply[T](name: String, extractor: (T, TableCellWalker) => Any) = new Column[T](name, extractor)
  def printLong(i:Long, w:TableCellWalker ):Unit = {
    w.setFloatValue(i.toFloat)
    w.setDataStyle(integerDataStyle)
  }
  
  def printStrOption(os:Option[String], w: TableCellWalker ):Unit = {
    os match {
      case None => w.setStringValue("")
      case Some(s) => w.setStringValue(s)
    }
  }
  
  def printIntOption( os:Option[Int], w: TableCellWalker ):Unit = printOption(os.map(_.toLong), w)
  def printLongOption( ol:Option[Long], w: TableCellWalker ):Unit = printOption(ol, w)
  def printOption( os:Option[Long], w: TableCellWalker ):Unit = {
    os match {
      case None => w.setStringValue("")
      case Some(s) => printLong(s,w)
    }
  }
  
  def printDate( d:LocalDate, w:TableCellWalker ):Unit = {
    val millies = d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli
    val jd = new Date(millies)
    w.setDateValue(jd)
  }
  def printDate( d:LocalDateTime, w:TableCellWalker ):Unit = {
    val jd = new Date(d.toInstant(ZoneOffset.UTC).toEpochMilli)
    w.setDateValue(jd)
  }
}
