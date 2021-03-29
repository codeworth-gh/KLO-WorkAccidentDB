package controllers

import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, WorkAccidentDAO}
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import views.PaginationInfo

import java.time.LocalDate
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

object PublicCtrl {
  val PAGE_SIZE = 50
}

class PublicCtrl @Inject()(cc: ControllerComponents, accidents:WorkAccidentDAO, regions:RegionsDAO, businesses:BusinessEntityDAO,
                           citizenships: CitizenshipsDAO, causes:InjuryCausesDAO, industries:IndustriesDAO)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport {
  
  import PublicCtrl._

  def main = Action {implicit req =>
    Ok( views.html.publicside.main() )
  }
  
  def accidentIndex(pSortBy:Option[String]=None, pAsc:Option[String]=None, pPage:Option[Int]=None) = Action.async{ implicit req =>
    val page = pPage.getOrElse(1)
    val asc = pAsc.getOrElse("f").trim=="t"
    val sortBy = WorkAccidentDAO.SortKey.named(pSortBy.getOrElse("Datetime") ).getOrElse(WorkAccidentDAO.SortKey.Datetime)
    for {
      accRows <- accidents.listAccidents((page-1)*PAGE_SIZE, PAGE_SIZE, sortBy, asc)
      accCount <- accidents.accidentCount()
    } yield {
      Ok(views.html.publicside.accidentsList(accRows,
        regions.apply,
        PaginationInfo(page, Math.ceil(accCount/PAGE_SIZE.toDouble).toInt), sortBy, asc))
    }
  }
  
  def accidentDetails( id:Long ) = Action.async{ implicit req =>
    for {
      accident <- accidents.getAccident(id)
    } yield accident match {
      case None => {
        val msgs = request2Messages(req)
        NotFound(views.html.errorPage(404, msgs("error.accidentNotFound"), Some(msgs("error.accidentNotFound.explanation")), None, req, msgs) )
      }
      case Some(acc) => {
        Ok( views.html.publicside.accidentDetails(acc) )
      }
    }
  }
  
  def fatalities(year:Option[Int]) = Action.async{ implicit req =>
    for {
      yearsWithAccidents <- accidents.listYearsWithAccidents
      actualYear = year.getOrElse(LocalDate.now().getYear)
      killed <- accidents.listKilledWorkers(actualYear)
    } yield {
      Ok( views.html.publicside.fatalitiesList(actualYear, yearsWithAccidents, killed) )
    }
    
  }
  
  def datasets = Action.async{ implicit req =>
    for {
      lastUpdate <- accidents.getLastUpdateDate
    } yield {
      Ok(views.html.publicside.datasets(lastUpdate))
    }
  }
  
  // TODO: Cache these.
  def accidentsDataset = TODO
  def injuriesDataset = TODO
}
