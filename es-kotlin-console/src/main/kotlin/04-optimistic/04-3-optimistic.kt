import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * 楽観的ロックによる更新
 * リトライ無しの動作確認
 * 複数スレッドにて並列実行
 * maxUpdateTries = 0 にて更新を行う
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()

    val id = "4"

    thingRepository.index(id, Thing("First version of the thing", amount = 0))

    // maxUpdateTries = 0 にて更新
    try {
        // 複数スレッドにて並列実行
        1.rangeTo(30).toList().parallelStream().forEach { n ->
            // maxUpdateTriesパラメータはオプションで、デフォルト値は2です。
            // リトライ回数を 0 に設定して同時更新を行うと失敗します。
            thingRepository.update(id, maxUpdateTries = 0) { Thing("nr_$n") }
        }
    } catch (e: Exception) {
        println("It failed because we disabled retries and we got a conflict")
    }
}
