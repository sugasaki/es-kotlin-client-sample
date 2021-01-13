import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()
    thingService.consolePrintMappings() // debug print

    // jsonからIndexを作成
    thingService.deleteIndex()
    thingService.createNewIndexByJson()

    // 楽観的ロックによる更新
    thingService.optimisticLocking() // debug print
}
