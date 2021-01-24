package search

import com.jillesvangurp.eskotlinwrapper.dsl.match
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Searches - count
 * ドキュメントの総数を取得
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)
    val repo = esClient.indexRepository<Thing>("things", refreshAllowed = true)
    val thingService = ThingService(repo)
    thingService.deleteIndex()

    // テストデータ100件登録
    thingService.bulkInset2(100)

    // 検索がすぐに動作するように、ES にすべてをディスクにコミットさせます。
    repo.refresh()

    println("The total number of documents is ${repo.count()}")

    val searchText = "quick"
    val count = repo.count {
        configure {
            query = match("name", searchText)
        }
    }

    println("We found $count results matching $searchText")
}
