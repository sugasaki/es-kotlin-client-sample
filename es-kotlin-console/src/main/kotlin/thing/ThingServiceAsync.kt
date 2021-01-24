package thing

import com.jillesvangurp.eskotlinwrapper.AsyncIndexRepository
import com.jillesvangurp.eskotlinwrapper.IndexRepository
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.configure
import org.elasticsearch.client.source
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.common.xcontent.stringify

class ThingServiceAsync(
    private val asyncRepo: AsyncIndexRepository<Thing>
) {

    /**
     * JSONからIndexを作成
     */
    suspend fun createNewIndexByJsonAsync() {
        asyncRepo.createIndex {
            source(
                """
    {
      "settings": {
      "index": {
        "number_of_shards": 3,
        "number_of_replicas": 0,
        "blocks": {
        "read_only_allow_delete": "false"
        }
      }
      },
      "mappings": {
      "properties": {
        "title": {
        "type": "text"
        }
      }
      }
    }
  """,
                XContentType.JSON
            )
        }
    }

    /**
     * Index削除
     */
    suspend fun deleteIndexAsync() {
        asyncRepo.deleteIndex()
    }

    /**
     * Bulk Indexing
     */
    suspend fun createDataAsync() {
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
