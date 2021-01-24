import com.jillesvangurp.eskotlinwrapper.BulkOperation
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Bulk Indexing
 * コールバック
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)
    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()
    // Bulk Indexing
    thingService.bulkInset(2)

    // コールバック処理付きのBulk処理
    // 更新が失敗したことが、コールバックで検知しています。
    thingRepository.bulk(
        // コールバック
        itemCallback = object : (BulkOperation<Thing>, BulkItemResponse) -> Unit {
            // Elasticsearchがバルクリクエストの各項目に対して何をしたかを確認する
            // そして、このコールバックを実装することで、カスタムなことを行うことができます。
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
        // bulk処理
        // seqNo、primaryTermsを指定しての楽観的ロック更新
        // この場合は取得をスキップすることができます。これらを効率的に取得する良い方法は scrolling検索です。
        // 以下の例ではあえて更新を失敗しています。
        update(
            id = "doc-2",
            // these values are wrong and this will now fail instead of retry
            // これらの値は間違っており、再試行する代わりに失敗するようになりました
            seqNo = 12,
            primaryTerms = 34,
            original = Thing("updated 2", 666)
        ) { currentVersion ->
            currentVersion.copy(name = "safely updated 3")
        }
    }
    println("" + thingRepository.get("doc-2"))
}
