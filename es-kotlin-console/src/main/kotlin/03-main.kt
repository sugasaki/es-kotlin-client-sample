import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingSearch

fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val recipeSearch = ThingSearch(thingRepository)

    // indexを作成
    recipeSearch.deleteIndex()
    recipeSearch.createNewIndex()

    // 1. ドキュメント追加
    var id = "first"
    println("Object does not exist: ${thingRepository.get(id)}")

    // so lets store something
    thingRepository.index(id, Thing("A thing", 42))
    println("Now we get back our object: ${thingRepository.get(id)}")

    // 2. ドキュメント上書き
    id = "first"
    try {
        thingRepository.index(id, Thing("A thing", 40))
    } catch (e: ElasticsearchStatusException) {
        println("we already had one of those and es returned ${e.status().status}")
    }

    // this how you do upserts
    thingRepository.index(id, Thing("Another thing", 666), create = false)
    println("It was changed: ${thingRepository.get(id)}")

    // 3. ドキュメント削除
    thingRepository.delete("1")
    println("ドキュメント削除: " + thingRepository.get("1"))
}
