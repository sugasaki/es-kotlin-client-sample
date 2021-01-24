package search

import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import org.elasticsearch.index.query.QueryBuilders.boolQuery
import org.elasticsearch.index.query.QueryBuilders.matchQuery
import org.elasticsearch.search.builder.SearchSourceBuilder.searchSource
import thing.Thing
import thing.ThingService

/**
 * Kotlin Query DSL
 * apply を使うことで、すべての呼び出しを連鎖させる必要がなくなり、少しはマシになります。
 * が、まだ少し冗長です。
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)
    val repo = esClient.indexRepository<Thing>("things", refreshAllowed = true)
    val thingService = ThingService(repo)
    thingService.deleteIndex()

    // テストデータ100件登録
    thingService.bulkInset(100)

    // 検索がすぐに動作するように、ES にすべてをディスクにコミットさせます。
    repo.refresh()

    // ビルダーパターン版
    val results = repo.search {
        source(
            searchSource()
                .size(20)
                .query(
                    boolQuery()
                        .must(matchQuery("name", "quick").boost(2.0f))
                        .must(matchQuery("name", "brown"))
                )
        )
    }
    println("We found ${results.totalHits} results.")

    // apply版
    // applyを使用して、より多くのidomaticを使用しています。
    val results2 = repo.search {
        source(
            searchSource().apply {
                query(
                    boolQuery().apply {
                        must().apply {
                            add(matchQuery("name", "quick").boost(2.0f))
                            add(matchQuery("name", "brown"))
                        }
                    }
                )
            }
        )
    }
    println("We found ${results2.totalHits} results.")
}
