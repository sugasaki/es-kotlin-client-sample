package search

import org.elasticsearch.action.search.source
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Searches - json
 * Kotlin DSL を使うのは、プログラム的に型安全な方法でクエリを構築したい場合に便利です。
 * しかし、Kibana開発コンソールから直接json形式のクエリを実行したい場合もあるでしょう。
 * Kotlinには複数行の文字列があるので、これは簡単に実行できます。
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

    val results = repo.search {
        // Kotlin の文字列テンプレートを使うことができます。
        val text = "brown"
        source(
            """
    {
      "size": 10,
      "query": {
        "match": {
          // ESはJSONにコメントを入れることができるって知ってましたか？
          // 見てください、私たちの変数を注入することができます
          // しかし、もちろんスクリプトインジェクションには注意してください!
          "name": "$text"
        }
      }
    }          
            """.trimIndent()
        )
    }
    println("Found ${results.totalHits}")
}
