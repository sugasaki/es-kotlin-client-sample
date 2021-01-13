import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingSearch

fun main(args: Array<String>) {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val recipeSearch = ThingSearch(thingRepository)

    // indexn作成
    recipeSearch.deleteIndex()
    recipeSearch.createNewIndex()
    recipeSearch.consolePrintMappings() // debug print

    // jsonからIndexを作成
    recipeSearch.deleteIndex()
    recipeSearch.createNewIndexByJson()
    recipeSearch.consolePrintMappings() // debug print
}
