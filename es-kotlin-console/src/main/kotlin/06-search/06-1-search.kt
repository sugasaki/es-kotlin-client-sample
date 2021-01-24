package search

import com.jillesvangurp.eskotlinwrapper.dsl.match
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Searches
 *
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

    // `brown`で検索します
    val searchText = "brown"

    // searchにQueryを渡して、SearchRequestを取得
    val results = repo.search {
        configure {
            // ここではわかりやすくするために引数に名前を追加しました
            query = match(field = "name", query = searchText)
        }
    }

    // totalHits - 検索ヒット件数
    // 検索ヒット件数
    println("\n---- count --------\n") // Elasticsearchのオブジェクト
    val count = results.totalHits
    println("Found $count")

    // mappedHits - デシリアライズ
    // mappedHits のレスポンスはThingにデシリアライズされています。
    println("\n---- mappedHits --------\n") // Elasticsearchのオブジェクト
    results.mappedHits.forEach {
        // 検索レスポンスからデシリアライズできます
        println("print in forEach: $it")
    }

    // searchHits
    // Elasticsearchのシーケンスオブジェクトを取得する
    println("\n---- searchHits --------\n") // Elasticsearchのオブジェクト
    val firstSearchHit = results.searchHits.first()
    println("print in searchHits - firstSearchHit: $firstSearchHit") // Elasticsearchのオブジェクト
    println("print in searchHits: ${firstSearchHit.id} ${firstSearchHit.seqNo} ${firstSearchHit.primaryTerm} ${firstSearchHit.sourceAsString}")

    // applyを使うことも可能です。    firstSearchHit.apply { println("print in an apply: $id $seqNo $primaryTerm $sourceAsString") }例えば、検索結果を使っての一括更新をしたい場合に便利です。
    firstSearchHit.apply { println("print in an apply: $id $seqNo $primaryTerm $sourceAsString") }

    // hits - `Pair`として取得
    // Elasticsearchのシーケンスオブジェクトと、デシリアライズされたペアオブジェクトを返します。
    println("\n---- hits --------\n") // Elasticsearchのオブジェクト
    val (searchHit, deserialized) = results.hits.first()
    println("print in hits - id: ${searchHit.id}") // document id
    println("print in hits - searchHit: $searchHit") // Elasticsearchのシーケンスオブジェクト
    println("print in hits - deserialized: $deserialized") // デシリアライズされたオブジェクト
}
