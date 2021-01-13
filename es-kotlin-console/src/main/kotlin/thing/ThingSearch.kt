package thing

import com.jillesvangurp.eskotlinwrapper.IndexRepository
import org.elasticsearch.client.configure
import org.elasticsearch.client.source
import org.elasticsearch.common.xcontent.stringify

data class Thing(val name: String, val amount: Long = 42)

class ThingSearch(
    private val repo: IndexRepository<Thing>
) {

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

    fun deleteIndex() {
        repo.deleteIndex()
    }

    fun consolePrintMappings() { // stringify is a useful extension function we added to the response
        println(repo.getSettings().stringify(true))

        repo.getMappings().mappings()
            .forEach { (name, meta) ->
                print("$name -> ${meta.source().string()}")
            }
    }

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
}
