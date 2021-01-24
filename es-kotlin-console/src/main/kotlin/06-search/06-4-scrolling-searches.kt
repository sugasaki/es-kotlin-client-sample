package search

import com.jillesvangurp.eskotlinwrapper.dsl.matchAll
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Searches
 * スクロール検索
 * スクロールは、大量の結果を処理したい場合に便利です。
 * スクロールを使用するための古典的なユースケースは、ドキュメントを一括更新することです。
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

    repo.bulk {
        // 単純にスクロールをtrueに設定すると、インデックス全体をスクロールすることができます。
        // これはインデックスのサイズに関係なくスケールされます。もし
        // スクロールする場合は、スクロールの ttl を設定することもできます (デフォルトは 1m)。
        val results = repo.search(
            scrolling = true,
            scrollTtlInMinutes = 10
        ) {
            configure {
                // スクロール検索のページサイズ
                // note, resultSizeはsizeに変換されます。しかし、サイズも
                // Map上の関数、我々はこれを回避するために動作します。
                resultSize = 5
                query = matchAll()
            }
        }
        // 結果をたくさんprintしないようにしましょう
        results.hits.take(15).forEach { (hit, thing) ->
            // マッピングのソースをオフにした場合は null になる可能性があります。
            println("${hit.id}: ${thing?.name}")
        }
        // 結果の最後のページの後、スクロールがクリーンアップされます。
    }
}
