package models

import com.github.jferard.fastods.TableCellWalker
import com.github.jferard.fastods.attribute.{BorderAttribute, BorderStyle}
import com.github.jferard.fastods.datastyle.{FloatStyle, FloatStyleBuilder}
import com.github.jferard.fastods.style.TableCellStyle
import controllers.PublicCtrl.integerDataStyle

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.{Date, Locale}

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
  
  def printDate( od:Option[LocalDate], w:TableCellWalker ):Unit = {
    od match {
      case None =>
        w.setStringValue("")
      case Some(d) => printDate(d,w)
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

object RichWalker {
  val titleStyle: TableCellStyle = TableCellStyle.builder("title").fontWeightBold().borderBottom(
    BorderAttribute.builder().borderSize(1).borderStyle(BorderStyle.SOLID).build())
    .build()
  val boldStyle: TableCellStyle = TableCellStyle.builder("bold").fontWeightBold().build()
  val integerDataStyle: FloatStyle = new FloatStyleBuilder("int", Locale.US).decimalPlaces(0).groupThousands(false).build()
}

class RichWalker(cw:TableCellWalker, var isBold:Boolean) {
  
  def this(aCw:TableCellWalker) = this(aCw, false)
  
  def bold: RichWalker = {
    isBold = true
    this
  }
  def plain: RichWalker = {
    isBold = false
    this
  }
  
  def th(s:String): RichWalker = {
    cw.setStringValue(s)
    cw.setStyle(RichWalker.titleStyle)
    cw.next()
    this
  }
  
  def td(s:String): RichWalker = {
    cw.setStringValue(s)
    if ( isBold ) {
      cw.setStyle(RichWalker.boldStyle)
    }
    cw.next()
    this
  }
  
  def td(s:Int): RichWalker = {
    cw.setFloatValue(s.toFloat)
    cw.setDataStyle(RichWalker.integerDataStyle)
    if ( isBold ) {
      cw.setStyle(RichWalker.boldStyle)
    }
    cw.next()
    this
  }
  
  def td(s: LocalDate): RichWalker = {
    val millies = s.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli
    val jd = new Date(millies)
    cw.setDateValue(jd)
    if (isBold) {
      cw.setStyle(RichWalker.boldStyle)
    }
    cw.next()
    this
  }
  
  def skip():RichWalker = {
    cw.next()
    this
  }
  
  def nextRow(): RichWalker = {
    cw.nextRow()
    this
  }
}
