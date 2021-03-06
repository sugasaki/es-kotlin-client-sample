
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * 楽観的ロックによる更新
 *
 * Elasticsearchの便利な機能の1つは、楽観的ロックを実行できることです。（同時更新の抑制）
 * その方法は、ドキュメントの`seqNo`と`primaryTerm`を追跡(チェック)することです。
 * `seqNo`と`primaryTerm`の両方をインデックス登録時に指定すると、
 * `seqNo`と`primaryTerm`が既存データと一致するかどうかがチェックされ、
 * ドキュメントが一致する場合にのみドキュメントが上書きされます。
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()

    // データ追加
    val id = "2"

    // 初期データ投入、初期データはseqNo = 0,primaryTerm = 1となる。
    thingRepository.index(id, Thing("Another thing"))
    thingService.consolePrintThing(id)

    // update... ①
    // seqNo = 0 and primaryTerm = 1のデータを更新する
    thingRepository.index(
        id,
        Thing("Another Thing"),
        seqNo = 0,
        primaryTerm = 1,
        create = false
    )
    // 更新結果をデバッグプリント。更新の結果、seqNo = 1,primaryTerm = 1となる。
    thingService.consolePrintThing(id)

    // update... ②
    // seqNo = 1 and primaryTerm = 1のデータを更新する
    thingRepository.index(
        id,
        Thing("Another Thing"),
        seqNo = 1,
        primaryTerm = 1,
        create = false
    )
    // 更新結果をデバッグプリント。更新の結果、seqNo = 2,primaryTerm = 1となる。
    thingService.consolePrintThing(id)

    // update... ③
    // seqNo = 1 and primaryTerm = 1のデータを更新する。（②と同時に更新したと過程）
    // seqNo = ②の処理によって、seqNo=2となっているので、更新が失敗する
    try {
        thingRepository.index(
            id,
            Thing("Another Thing"),
            seqNo = 1,
            primaryTerm = 1,
            create = false
        )
    } catch (e: ElasticsearchStatusException) {
        // seqNo = 2となっているので、更新が失敗する
        println("Version conflict! Es returned ${e.status().status}")
    }
}
