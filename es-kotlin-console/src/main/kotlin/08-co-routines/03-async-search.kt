package search

import com.jillesvangurp.eskotlinwrapper.dsl.matchAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.create
import thing.Thing
import thing.ThingServiceAsync

/**
 * Co-routines
 * AsyncIndexRepository
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    // 拡張関数を使用して新しいリポジトリを作成することができます。
    val asyncRepo = esClient.asyncIndexRepository<Thing>("asyncthings", refreshAllowed = true)
    val thingServiceAsync = ThingServiceAsync(asyncRepo)

    // index削除
    runBlocking { thingServiceAsync.deleteIndexAsync() }

    // index作成
    runBlocking { thingServiceAsync.createNewIndexByJsonAsync() }

    // テストデータ作成
    runBlocking { thingServiceAsync.createDataAsync() }

    //
    runBlocking {
        val results = asyncRepo.search(scrolling = true) {
            configure {
                query = matchAll()
            }
        }
        // hits is a Flow<Thing>
        println("Hits: ${results.mappedHits.count()}")
    }
}
