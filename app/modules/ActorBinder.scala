package modules

import actors.{DataProductsActor, EntityMergeActor, ImportDataActor, SafetyViolationSanctionScrapingActor, WarrantScrapingActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.PekkoGuiceSupport

class ActorBinder extends AbstractModule with PekkoGuiceSupport {
  override def configure():Unit = {
    bindActor[ImportDataActor]("ImportDataActor")
    bindActor[WarrantScrapingActor]("WarrantScrapingActor")
    bindActor[SafetyViolationSanctionScrapingActor]("svsScrapingActor")
    bindActor[DataProductsActor]("DataProductsActor")
    bindActor[EntityMergeActor]("EntityMergeActor")
  }
}
