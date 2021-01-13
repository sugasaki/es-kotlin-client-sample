package thing

import com.jillesvangurp.eskotlinwrapper.IndexRepository
import org.elasticsearch.ElasticsearchStatusException
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
     * 楽観的ロックによる更新
     */
    fun optimisticLocking01() {
        repo.index("2", Thing("Another thing"))

        val (obj, rawGetResponse) = repo.getWithGetResponse("2")
            ?: throw IllegalStateException("We just created this?!")

        println(
            "obj with name '${obj.name}' has id: ${rawGetResponse.id}, " +
                "primaryTerm: ${rawGetResponse.primaryTerm}, and " +
                "seqNo: ${rawGetResponse.seqNo}"
        )
        // This works
        repo.index(
            "2",
            Thing("Another Thing"),
            seqNo = rawGetResponse.seqNo,
            primaryTerm = rawGetResponse.primaryTerm,
            create = false
        )
        try {
            // ... but if we use these values again it fails
            repo.index(
                "2",
                Thing("Another Thing"),
                seqNo = rawGetResponse.seqNo,
                primaryTerm = rawGetResponse.primaryTerm,
                create = false
            )
        } catch (e: ElasticsearchStatusException) {
            println("Version conflict! Es returned ${e.status().status}")
        }
    }
}
