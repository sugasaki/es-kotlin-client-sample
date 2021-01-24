package search

import kotlinx.coroutines.runBlocking
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.create
import thing.Thing
import thing.ThingServiceAsync

/**
 * Co-routines
 * AsyncIndexRepository
 */
fun main() {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200)

    // 拡張関数を使用して新しいリポジトリを作成することができます。
    val asyncRepo = esClient.asyncIndexRepository<Thing>("asyncthings", refreshAllowed = true)
    val thingServiceAsync = ThingServiceAsync(asyncRepo)

    // index削除
    runBlocking { thingServiceAsync.deleteIndexAsync() }

    // asyncRepo上のすべての関数はもちろんサスペンドされているので、
    // それらをサスペンドさせるためにはサスペンドされた関数をサブルーチンで実行する必要があります。
    runBlocking { thingServiceAsync.createNewIndexByJsonAsync() }

    // asyncRepo上のすべての関数はもちろんサスペンドされています。
    // コ・ルーティン・スコープで実行する必要があります。
    runBlocking {
        // これらはすべて非同期サスペンド関数を使用しています。
        asyncRepo.index("thing1", Thing("The first thing"))

        // これは `AsyncBulkIndexingSession` を使用します。
        asyncRepo.bulk {
            for (i in 2.rangeTo(10)) {
                index("thing_$i", Thing("thing $i"))
            }
        }

        // refresh
        asyncRepo.refresh()

        // count
        val count = asyncRepo.count { }
        println("indexed $count items")
    }
}
