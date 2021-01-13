import com.jillesvangurp.eskotlinwrapper.dsl.matchAll
import org.elasticsearch.action.search.dsl
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository

data class Foo(val message: String)

fun main(args: Array<String>) {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val fooRepo = esClient.indexRepository<Foo>("my-index", refreshAllowed = true)
    fooRepo.index(obj = Foo("Hello Elastic World!"))

    // ensure the document is committed
    fooRepo.refresh()
    val results = fooRepo.search {
        dsl {
            query = matchAll()
        }
    }
    println(results.mappedHits.first().message)
}
