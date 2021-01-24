package example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.jillesvangurp.eskotlinwrapper.JacksonModelReaderAndWriter
import example.model.Recipe
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.create
import org.elasticsearch.cluster.health.ClusterHealthStatus

/**
 * Building a Recipe Search Engine
 */
suspend fun main(vararg args: String) {
    val objectMapper = ObjectMapper()

    // Kotlin との統合を有効にし、クラスパス上にあるものは何でも有効にする
    objectMapper.findAndRegisterModules()

    // アンダースコアを含む名前を適切に変換していることを確認してください。
    // camelCase から SNAKE_CASE
    objectMapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE

    val esClient = create(host = "localhost", port = 9999)

    // ktorが終了した後、クライアントをクリーンにシャットダウンします。
    esClient.use {
        val customSerde = JacksonModelReaderAndWriter(Recipe::class, objectMapper)
        val recipeRepository =
            esClient.asyncIndexRepository<Recipe>(
                index = "recipes",
                // objectMapper を再利用したいので、デフォルトをオーバーライドします。
                // そして、スネークケースのセットアップを再利用します。
                modelReaderAndWriter = customSerde
            )
        val recipeSearch = RecipeSearch(recipeRepository, objectMapper)
        if (args.any { it == "-c" }) {
            // レシピ検索は非同期で行うのでコアーチンスコープが必要です
            withContext(Dispatchers.IO) {
                // -c を渡すとインデックスをブートストラップします。
                recipeSearch.deleteIndex()
                recipeSearch.createNewIndex()
                recipeSearch.indexExamples()
            }
        }

        // シンプルな ktor サーバを作成します。
        createServer(objectMapper, recipeSearch).start(wait = true)
    }
}
// END main_function

// BEGIN ktor_setup
private fun createServer(
    objectMapper: ObjectMapper,
    recipeSearch: RecipeSearch
): NettyApplicationEngine {
    return embeddedServer(Netty, port = 8080) {
        // this will allow us to serialize data objects to json
        install(DataConversion)
        install(ContentNegotiation) {
            // lets reuse our mapper for this
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }

        routing {
            get("/") {
                call.respondText("Hello World!", ContentType.Text.Plain)
            }
            post("/recipe_index") {
                withContext(Dispatchers.IO) {
                    recipeSearch.createNewIndex()
                    call.respond(HttpStatusCode.Created)
                }
            }

            delete("/recipe_index") {
                withContext(Dispatchers.IO) {
                    recipeSearch.deleteIndex()
                    call.respond(HttpStatusCode.Gone)
                }
            }

            post("/index_examples") {
                withContext(Dispatchers.IO) {
                    recipeSearch.indexExamples()
                    call.respond(HttpStatusCode.Accepted)
                }
            }

            get("/health") {
                withContext(Dispatchers.IO) {

                    val healthStatus = recipeSearch.healthStatus()
                    if (healthStatus == ClusterHealthStatus.RED) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            "es cluster is $healthStatus"
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            "es cluster is $healthStatus"
                        )
                    }
                }
            }

            get("/search") {
                withContext(Dispatchers.IO) {

                    val params = call.request.queryParameters
                    val query = params["q"].orEmpty()
                    val from = params["from"]?.toInt() ?: 0
                    val size = params["size"]?.toInt() ?: 10

                    call.respond(recipeSearch.search(query, from, size))
                }
            }

            get("/autocomplete") {
                withContext(Dispatchers.IO) {
                    val params = call.request.queryParameters
                    val query = params["q"].orEmpty()
                    val from = params["from"]?.toInt() ?: 0
                    val size = params["size"]?.toInt() ?: 10

                    call.respond(recipeSearch.autocomplete(query, from, size))
                }
            }
        }
    }
}
// END ktor_setup
