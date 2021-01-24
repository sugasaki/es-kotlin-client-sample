package search

import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.ActionListener
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import org.elasticsearch.client.indices.ReloadAnalyzersRequest
import org.elasticsearch.client.indices.ReloadAnalyzersResponse
import thing.Thing
import thing.ThingService

/**
 * Co-routines
 * asyncメソッド
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)
    val repo = esClient.indexRepository<Thing>("things", refreshAllowed = true)
    val thingService = ThingService(repo)
    thingService.deleteIndex()

    // テストデータ100件登録
    thingService.bulkInset(100)

    val ic = esClient.indices()

    // 同期バージョン
    // RestHighLevel クライアントによって提供される同期バージョン
    val response = ic.reloadAnalyzers(
        ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT
    )

    // 非同期バージョン
    // RestHighLevel クライアントによって提供されるコールバックを使用した非同期バージョン
    ic.reloadAnalyzersAsync(
        ReloadAnalyzersRequest("myindex"),
        RequestOptions.DEFAULT,
        object : ActionListener<ReloadAnalyzersResponse> {
            override fun onFailure(e: Exception) {
                println("it failed")
            }
            override fun onResponse(response: ReloadAnalyzersResponse) {
                println("it worked")
            }
        }
    )

    // コアーチンフレンドリーなバージョン
    runBlocking {
        // 生成された関数を使用したコアーチンフレンドリーなバージョンです。
        // コードジェネレーターのプラグインはサスペンド版なので、ここでは
        // coroutineスコープを取得するためのrunBlockingは、より適切なものを使用します。
        // もちろん自分のアプリケーションのスコープです。
        val response2 = ic.reloadAnalyzersAsync(
            ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT,
            getActionListener()
        )
    }
}

fun getActionListener(): ActionListener<ReloadAnalyzersResponse> {
    return object : ActionListener<ReloadAnalyzersResponse> {
        override fun onFailure(e: Exception) {
            println("it failed")
        }

        override fun onResponse(response: ReloadAnalyzersResponse) {
            println("it worked")
        }
    }
}
