package thing

import com.jillesvangurp.eskotlinwrapper.IndexRepository
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.configure
import org.elasticsearch.client.source
import org.elasticsearch.common.xcontent.stringify

class ThingService(
    private val repo: IndexRepository<Thing>
) {

    /**
     * Index作成
     */
    fun createNewIndex() {
        repo.createIndex {
            // use our friendly DSL to configure the index
            configure {
                settings {
                    replicas = 0
                    shards = 1
                }
                mappings {
                    // in the block you receive FieldMappings as this
                    // a simple text field "title": {"type":"text"}
                    text("name")
                    // a numeric field with sub fields, use generics
                    // to indicate what kind of number
                    number<Long>("amount") {
                        // we can customize the FieldMapping object
                        // that we receive in the block
                        fields {
                            // we get another FieldMappings
                            // lets add a keyword field
                            keyword("somesubfield")
                            // if you want, you can manipulate the
                            // FieldMapping as a map
                            // this is great for accessing features
                            // not covered by our Kotlin DSL
                            this["imadouble"] = mapOf("type" to "double")
                            number<Double>("abetterway")
                        }
                    }
                }
            }
        }
    }

    /**
     * Index削除
     */
    fun deleteIndex() {
        repo.deleteIndex()
    }

    /**
     * マッピング取得＆デバッグプリント
     */
    fun consolePrintMappings() { // stringify is a useful extension function we added to the response
        println(repo.getSettings().stringify(true))

        repo.getMappings().mappings()
            .forEach { (name, meta) ->
                print("$name -> ${meta.source().string()}")
            }
    }

    /**
     * JSONからIndexを作成
     */
    fun createNewIndexByJson() {
        // delete the previous version of our index
        repo.deleteIndex()
        // create a new one using json source
        repo.createIndex {
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
                      "name": {
                      "type": "text"
                      },
                      "amount": {
                      "type": "long"
                      }
                    }
                    }
                  }
                """
            )
        }
    }

    /**
     * IDを指定してデータを取得し、コンソールに内容を出力
     * @param id String
     */
    fun consolePrintThing(id: String) {
        val (obj, rawGetResponse) = repo.getWithGetResponse(id)
            ?: throw IllegalStateException("We just created this?!")

        println(
            "obj with name '${obj.name}' has id: ${rawGetResponse.id}, " +
                "amount: '${obj.amount}', " +
                "primaryTerm: ${rawGetResponse.primaryTerm}, and " +
                "seqNo: ${rawGetResponse.seqNo}"
        )
    }

    /**
     * Bulk Indexing
     * @param bulkSize Int
     */
    fun bulkInset(bulkSize: Int = 500) {
        // BulkIndexingSession<Thing>を作成し、ブロックに渡します。
        repo.bulk {
            1.rangeTo(bulkSize).forEach {
                index("doc-$it", Thing("indexed $it", 666))
            }
        }
    }

    /**
     * Bulk Indexing
     * @param bulkSize Int
     */
    fun bulkInset2(bulkSize: Int = 100) {
        repo.bulk(refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE) {
            index("1", Thing("The quick brown fox"))
            index("2", Thing("The quick brown emu"))
            index("3", Thing("The quick brown gnu"))
            index("4", Thing("Another thing"))
            5.rangeTo(bulkSize).forEach {
                index("$it", Thing("Another thing: $it"))
            }
        }
    }

}
