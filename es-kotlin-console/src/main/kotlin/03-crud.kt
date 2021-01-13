import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * CRUD操作
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()

    // 1. ドキュメント追加
    val id = "first"
    println("Object does not exist: ${thingRepository.get(id)}")

    // so lets store something
    thingRepository.index(id, Thing("A thing", 42))
    println("Now we get back our object: ${thingRepository.get(id)}")

    // 2. ドキュメント上書き
    // 2.1. ドキュメント上書き。こちらは失敗する
    try {
        thingRepository.index(id, Thing("A thing", 40))
    } catch (e: ElasticsearchStatusException) {
        println("we already had one of those and es returned ${e.status().status}")
    }

    // 2.2. create = falseに設定してのドキュメント上書き。こちらは成功する
    // this how you do upserts
    thingRepository.index(id, Thing("Another thing", 666), create = false)
    println("It was changed: ${thingRepository.get(id)}")

    // 3. ドキュメント削除
    thingRepository.delete("1")
    println("ドキュメント削除: " + thingRepository.get("1"))
}
