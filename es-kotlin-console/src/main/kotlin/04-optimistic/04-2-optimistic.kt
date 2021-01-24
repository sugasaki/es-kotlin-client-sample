import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * 楽観的更新
 * updateを使っての楽観的更新（後勝ちルール更新）
 * seqNoでのチェックは行われない
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

    // 楽観的更新
    thingRepository.update(id) { Thing("Another Thing", amount = it.amount + 1) }
    thingService.consolePrintThing(id)

    // 楽観的更新
    thingRepository.update("3") { currentThing ->
        // updateメソッドの中でデータ取得後にコールされる
        currentThing.copy(name = "we can do this again and again", amount = currentThing.amount + 1)
    }
    thingService.consolePrintThing(id)
}
