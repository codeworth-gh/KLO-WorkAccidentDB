package actors

import com.google.inject.AbstractModule
import play.libs.akka.AkkaGuiceSupport

class ActorBinder extends AbstractModule with AkkaGuiceSupport {
  override def configure():Unit = {
    bindActor[ImportDataActor](classOf[ImportDataActor], "ImportDataActor")
    bindActor[WarrantScrapingActor](classOf[WarrantScrapingActor], "WarrantScrapingActor")
    bindActor[DataProductsActor](classOf[DataProductsActor], "DataProductsActor")
  }
}
