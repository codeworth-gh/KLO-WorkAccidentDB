package views

import controllers.routes
import play.api.mvc.Call
import play.twirl.api.Html

/*
This file contains classes and data structures that describe the site structure (or structure*s*, in case
there are a few sections).
 */

abstract sealed class SectionItem
case class PageSectionItem(title:String, call:Call) extends SectionItem
case object SeparatorSectionItem extends SectionItem
case class JsSectionItem(title:String, html:Html) extends SectionItem

abstract sealed class TopSiteSection[T]{
  def id:T
  def title:String
}

case class PageSection[T](title:String, id:T, call:Call) extends TopSiteSection[T]
case class MultiPageSection[T](title:String, id:T, children:Seq[SectionItem]) extends TopSiteSection[T]

object PublicSections extends Enumeration {
  val Home = Value("Home")
  val AccidentList = Value("Accidents")
  val BizEntList = Value("BizEnts")
  val Fatalities = Value("Dead")
  val Datasets = Value("Datasets")
}

object BackOfficeSections extends Enumeration {
  val Home = Value("Home")
  val HelperTables = Value("Helper Tables")
  val BusinessEntities = Value("Business Entities")
  val WorkAccidents = Value("Work Accidents")
  val Users = Value("Users")
}


/**
  * Holds data about the site structure.
  */
object Structure {
  
  val publicItems:Seq[TopSiteSection[PublicSections.Value]] = Seq(
    PageSection("navbar.publicHome", PublicSections.Home, routes.PublicCtrl.main()),
    PageSection("navbar.accidents", PublicSections.AccidentList, routes.PublicCtrl.accidentIndex(None, None, None, None, None, None, None, None)),
    PageSection("navbar.businessEntities", PublicSections.BizEntList, routes.PublicCtrl.bizEntIndex(None, None, None, "")),
    PageSection("navbar.dead", PublicSections.Fatalities, routes.PublicCtrl.fatalities(None)),
    PageSection("navbar.datasets", PublicSections.Datasets, routes.PublicCtrl.datasets())
  )
  
  val backOfficeSections:Seq[TopSiteSection[BackOfficeSections.Value]] = Seq(
    PageSection("navbar.main", BackOfficeSections.Home, routes.UserCtrl.userHome() ),
    PageSection("navbar.workAccidents", BackOfficeSections.WorkAccidents, routes.WorkAccidentCtrl.backofficeIndex(None, None, None) ),
    PageSection("navbar.businessEntities", BackOfficeSections.BusinessEntities, routes.BusinessEntityCtrl.backofficeIndex(None,None,None,None) ),
    PageSection("navbar.helperTables", BackOfficeSections.HelperTables, routes.HelperTableCtrl.helperTablesIndex() ),
    MultiPageSection("navbar.users", BackOfficeSections.Users, Seq(
      PageSectionItem("navbar.users.list", routes.UserCtrl.showUserList()),
      PageSectionItem("navbar.users.editMyProfile", routes.UserCtrl.showEditMyProfile()),
      SeparatorSectionItem,
      PageSectionItem("navbar.users.invite", routes.UserCtrl.showInviteUser())
    ))
  )
  
}
