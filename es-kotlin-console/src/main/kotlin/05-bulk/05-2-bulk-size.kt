import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * Bulk Indexing
 * bulkSizeパラメータを設定することで、
 * 各リクエストで送信される操作の数を制御することができます。
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
    thingService.bulkInset(4)

    // bulkSize = 50
    // 以下の例では4件の処理を行っています。
    thingRepository.bulk(bulkSize = 50) {
        // create=false の設定が上書きされて適切なものになります。
        // ドキュメントを一括で置換する場合に行うこと
        index("doc-1", Thing("upserted 1", 666), create = false)

        // getAndUpdate
        // CRUDアップデートと同じように、安全な一括アップデートを行うことができます。
        // しかし、これはアイテムごとに1つの取得を行うという欠点があり、スケールしない可能性があります。
        getAndUpdate("doc-2") { currentVersion ->
            // これはupdateと同じように動作しリトライも行います。
            currentVersion.copy(name = "updated 2")
        }

        // update
        // repo.update()とは違う動作なので注意
        // seqNo、primaryTermsを指定する必要があります。
        // この場合は取得をスキップすることができます。これらを効率的に取得する良い方法は scrolling検索です。
        // 以下の例ではあえて更新を失敗しています。
        update(
            id = "doc-3",
            // この2つの値(seqNo, primaryTerms)はあえて間違っている値をセットしています。
            // getAndUpdate.
            seqNo = 12,
            primaryTerms = 34,
            original = Thing("indexed $it", 666)
        ) { currentVersion ->
            currentVersion.copy(name = "safely updated 3")
        }

        // アイテムを削除することもできます。
        delete("doc-4")
    }

    println(thingRepository.get("doc-1"))
    println(thingRepository.get("doc-2"))
    println(thingRepository.get("doc-3"))
    // should print null
    println(thingRepository.get("doc-4"))
}
