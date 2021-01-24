import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * 楽観的ロックによる更新
 * updateを使っての更新
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()

    val id = "3"

    // データ追加
    thingRepository.index(id, Thing("Yet another thing"))
    thingService.consolePrintThing(id)

    // 更新
    thingRepository.update(id) { Thing("Another Thing") }
    thingService.consolePrintThing(id)

    // 更新
    thingRepository.update(id) { Thing(name = "we can do this again and again") }
    thingService.consolePrintThing(id)
}
