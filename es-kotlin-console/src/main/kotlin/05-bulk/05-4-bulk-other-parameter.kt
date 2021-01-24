import com.jillesvangurp.eskotlinwrapper.BulkOperation
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Bulk Indexing
 * オーバーライドできるパラメーターは他にもいくつかあります。
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)
    val repo = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(repo)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()
    // Bulk Indexing
    thingService.bulkInset(2)

    // オーバーライドできるパラメーターサンプル
    repo.bulk(
        // Elasticsearchに送信する項目数を制御する
        // 何が最適かは、ドキュメントのサイズとクラスタの設定を行います。
        bulkSize = 10,
        // デフォルトでドキュメントを再試行する頻度を制御します。
        retryConflictingUpdates = 3,
        // Elasticsearch の更新方法と更新するかどうかを制御します。
        // ES がリフレッシュするまでの間、バルクリクエストをブロックするかどうかを指定します。
        refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE,
        // item callback
        itemCallback = object : (BulkOperation<Thing>, BulkItemResponse) -> Unit {
            override fun invoke(op: BulkOperation<Thing>, response: BulkItemResponse) {
                if (response.isFailed) {
                    // ここを通るはずです。
                    println(
                        "${op.id}: ${op.operation.opType().name} failed, " +
                            "code: ${response.failure.status}"
                    )
                } else {
                    println("${op.id}: ${op.operation.opType().name} succeeded!")
                }
            }
        }
    ) {

        // 削除
        delete("doc-1")

        // update
        getAndUpdate("doc-2") { currentVersion ->
            // これはupdateと同じように動作しリトライも行います。
            currentVersion.copy(name = "updated 2")
        }

        // seqNo, primaryTermsを指定しての更新
        update(
            id = "doc-2",
            // these values are wrong so this will be retried
            seqNo = 12,
            primaryTerms = 34,
            original = Thing("updated 2", 666)
        ) { currentVersion ->
            currentVersion.copy(name = "safely updated 3")
        }
    }
    println("" + repo.get("doc-2"))
}
