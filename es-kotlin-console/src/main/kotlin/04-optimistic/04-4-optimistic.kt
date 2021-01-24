import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import thing.Thing
import thing.ThingService

/**
 * 楽観的ロックによる更新
 * リトライ10回の動作確認
 * 複数スレッドにて並列実行
 * maxUpdateTries = 10 にて更新を行う
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    val thingRepository = esClient.indexRepository<Thing>("things")
    val thingService = ThingService(thingRepository)

    // indexを作成
    thingService.deleteIndex()
    thingService.createNewIndex()

    val id = "5"

    thingRepository.index(id, Thing("First version of the thing", amount = 0))

    // maxUpdateTries = 10 にて更新
    1.rangeTo(30).toList().parallelStream().forEach { n ->
        // but if we let it retry a few times, it will be eventually consistent
        thingRepository.update(id, maxUpdateTries = 10) { Thing("nr_$n", amount = it.amount + 1) }
    }
    println("All updates succeeded! amount = ${thingRepository.get(id)?.amount}.")

    // スレッド10000 maxUpdateTries = 10 にて更新
    // この更新処理はリトライ回数を10でも失敗する（可能性が高い）
    try {
        // 複数スレッドにて並列実行
        1.rangeTo(10000).toList().parallelStream().forEach { n ->
            // この更新処理はリトライ回数を10でも失敗する（可能性が高い）
            thingRepository.update(id, maxUpdateTries = 10) { Thing("nr_$n", amount = it.amount + 1) }
        }
        println("All updates succeeded! amount = ${thingRepository.get(id)?.amount}.")
    } catch (e: Exception) {
        println("It failed because we disabled retries and we got a conflict")
        println("amount = ${thingRepository.get(id)?.amount}.")
    }
}
