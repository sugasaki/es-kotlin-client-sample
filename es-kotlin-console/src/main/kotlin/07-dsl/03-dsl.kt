package search

import com.jillesvangurp.eskotlinwrapper.dsl.bool
import com.jillesvangurp.eskotlinwrapper.dsl.match
import com.jillesvangurp.eskotlinwrapper.dsl.matchPhrase
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Kotlin Query DSL
 * 型安全なDSL構造と、マップの単純なスキーマレス操作の両方を混在させることができるDSLを提供しています。
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

    // apply { ... }を使用して、より多くのidomaticを使用しています。
    val results = repo.search {
        // SearchRequest.dsl is the extension function that allows us to use the dsl.
        configure {
            // SearchDSLはこのようにブロックに渡されます。
            // MapBackedProperties クラスを拡張します。
            // これにより、プロパティを MutableMap にデリゲートすることができます。

            // from はマップに格納されているプロパティです。
            from = 0

            // MapBackedPropertiesは実際にはMutableMapを実装しています。
            // そして、単純なMutableMapにデリゲートします。
            // これでも動作します: this["from"] = 0

            // 残念ながら、マップは独自のサイズプロパティを持っているので、それを使用することはできません。
            // これをクエリサイズのプロパティ名として使用します :-(
            // queryはESQueryのインスタンスを取得する関数です。
            resultSize = 20
            // これは実際にマップに「サイズ」というキーを入れます。

            // queryはESQueryのインスタンスを取得する関数です。
            query =
                // boolはBoolQueryを作成する関数です。
                // ブロックに注入されるESQueryを拡張したものです。
                bool {
                    // BoolQueryにはmustという関数があります。
                    // フィルタ、should、mustNotも持っています。
                    must(
                        // それはESQueryのvarargリストを持っています。
                        match("name", "qiuck") {
                            // match は常にフィールドとクエリを必要とします。
                            // しかし、ブーストはオプションです。
                            boost = 2.0
                            // 誤字脱字にもかかわらず、何かを見つけることができます。
                            fuzziness = "auto"
                        },
                        // しかし、ブロックパラメータはnullableで
                        // デフォルトはnull
                        matchPhrase("name", "quick brown") {
                            slop = 1
                        }
                    )
                }
        }
    }
    println("We found ${results.totalHits} results.")
}
