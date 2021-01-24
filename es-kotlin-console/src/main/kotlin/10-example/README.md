# Example: Building a Recipe Search Engine


Elasticsearch Kotlin Clientは、Elasticsearchと相互作用するコードを簡単に書くことができるように設計されています。

これがどのように動作するかを示す最も簡単な方法は、単純な例で示すことです。以下のコードは、[Elastic examples リポジトリ](https://github.com/elastic/examples/tree/master/Search/recipe_search_java)にある例を非常にゆるくベースにしており、そのプロジェクトからデータを借用しています。

この記事では、レシピのインデックス作成と検索のためのシンプルな Rest サービスを実装したシンプルな [KTor](https://ktor.io/docs/a-ktor-application.html)サーバーを作成します。

ktor とこのライブラリは両方とも`co-routines`をサポートしているので、それをフルに活用するための作業を行います。

この例のソースコードの完全版は[こちら](https://github.com/jillesvangurp/es-kotlin-wrapper-client/src/examples/kotlin/recipesearch)にあります。

以下はコードを抜粋しながら説明します。


## data model

データモデルから始めます。

[チキンのエンチラーダ](https://www.gimmesomeoven.com/best-chicken-enchiladas-ever/)のためのシンプルな例のjsonファイルを考えてみましょう。

```
{
    "title": "Homemade Chicken Enchiladas",
    "description": "These enchiladas are great. Even my 5 year old loves them!",
    "ingredients": [
        "1 tablespoon olive oil",
        "2 cooked chicken breasts, shredded",
        "1 onion, diced",
        "1 green bell pepper, diced",
        "1 1/2 cloves garlic, chopped",
        "1 cup cream cheese",
        "1 cup shredded Monterey Jack cheese",
        "1 (15 ounce) can tomato sauce",
        "1 tablespoon chili powder",
        "1 tablespoon dried parsley",
        "1 teaspoon dried oregano",
        "1/2 teaspoon salt",
        "1/2 teaspoon ground black pepper",
        "8 (10 inch) flour tortillas",
        "2 cups enchilada sauce",
        "1 cup shredded Monterey Jack cheese"
    ],
    "directions": [
        "Preheat oven to 350 degrees F (175 degrees C).",
        "Heat olive oil in a skillet over medium heat. Cook and stir chicken, onion, green bell pepper, garlic, cream cheese, and 1 cup Monterey Jack cheese in hot oil until the cheese melts, about 5 minutes. Stir tomato sauce, chili powder, parsley, oregano, salt, and black pepper into the chicken mixture.",
        "Divide mixture evenly into tortillas, roll the tortillas around the filling, and arrange in a baking dish. Cover with enchilada sauce and remaining 1 cup Monterey Jack cheese.",
        "Bake in preheated oven until cheese topping melts and begins to brown, about 15 minutes."
    ],
    "prep_time_min": 15,
    "cook_time_min": 20,
    "servings": 8,
    "tags": [ "main dish" ],
    "author": {
        "name": "Mary Kate",
        "url": "http://allrecipes.com/cook/14977239/profile.aspx"
    },
    "source_url": "http://allrecipes.com/Recipe/Homemade-Chicken-Enchiladas/Detail.aspx"
}
```


このようなレシピを表現するためのシンプルなデータモデルを作成します。

```
data class Author(val name: String, val url: String)
```

```
data class Recipe(
  val title: String,
  val description: String,
  val ingredients: List<String>,
  val directions: List<String>,
  val prepTimeMin: Int,
  val cookTimeMin: Int,
  val servings: Int,
  val tags: List<String>,
  val author: Author,
  // we will use this as our ID as well
  val sourceUrl: String
)
```


このモデルを使用すると、単純な `AsyncIndexRepository`を作成し、
レシピをインデックス化して検索できる単純な `ktor`サーバを作成することができます。

まずはメインの関数から始めましょう。

ServerMain.kt
```
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
```

これにより、Elasticsearchクライアント、シリアライズに使用するjacksonオブジェクトマッパー、そしてコ・ルーティンを使用できるIndexRepositoryのバージョンであるAsyncIndexRepositoryが作成されます。

これらは RecipeSearch のコンストラクタに注入されます。
このクラスにはビジネスロジックが含まれています。

最後に、シンプルな REST api を実装するためのシンプルな非同期 KTor サーバーを構築する関数に渡します（この記事の最後にあるコードを参照してください）。

## Creating an index

Kotlinクライアントの一部であるカスタムマッピングDSLでカスタムインデックスを作成します。

```
repository.createIndex {
  configure {
    settings {
      replicas = 0
      shards = 1
      // we have some syntactic sugar for adding custom analysis
      // however we don't hava a complete DSL for this
      // so we fall back to using put for things
      // not in the DSL
      addTokenizer("autocomplete") {
        put("type", "edge_ngram")
        put("min_gram", 2)
        put("max_gram", 10)
        put("token_chars", listOf("letter"))
      }
      addAnalyzer("autocomplete") {
        put("tokenizer", "autocomplete")
        put("filter", listOf("lowercase"))
      }
      addAnalyzer("autocomplete_search") {
        put("tokenizer", "lowercase")
      }
    }
    mappings {
      text("allfields")
      text("title") {
        copyTo = listOf("allfields")
        fields {
          text("autocomplete") {
            analyzer = "autocomplete"
            searchAnalyzer = "autocomplete_search"
          }
        }
      }
      text("description") {
        copyTo = listOf("allfields")
      }
      number<Int>("prep_time_min")
      number<Int>("cook_time_min")
      number<Int>("servings")
      keyword("tags")
      objField("author") {
        text("name")
        keyword("url")
      }
    }
  }
}
```

このやや凝ったマッピングの例では、我々の DSL と、その下にある MutableMap への単純な put 呼び出しをどのようにミックスするかを示しています。

DSL は一般的に使用されるものをサポートしていますが、Elasticsearch は非常に多くのカスタムなものを持っているので、それらをすべて DSL に手動でマッピングすることは不可能です。

マップされていないものについては、単純にプリミティブ、マップ、リストなどを使ってputを使うことができます。

お好みであれば、ソースを使用して文字列または InputStream のいずれかから生の json を注入したり、RestHighLevelClient に付属の非常に限定的なビルダーを使用したりすることもできます。


## バルクDSLを使用したインデックス作成

### jsonファイルからインデックス作成

レシピ文書のインデックスを作成するために、src/examples/resources/recipesディレクトリ内のすべてのファイルをバルクインデックス化するために、バルクDSLを利用したシンプルな関数を使用しています。

バルクインデックス化により、Elasticsearchはドキュメントのバッチを効率的に処理することができます。

RecipeSearch.kt

```
suspend fun indexExamples() {
  // 小さなバルクサイズを使用して、潜在的に大量のファイルに対してどのように動作するかを説明します。
  repository.bulk(bulkSize = 3) {
    File("src/examples/resources/recipes")
      .listFiles { f -> f.extension == "json" }?.forEach {
      val parsed = objectMapper.readValue<Recipe>(it.readText())
      // lets use the sourceUrl as an id
      // use create=false to allow updates
      index(parsed.sourceUrl, parsed, create = false)
    }
  }
}
```

このコードの小ささに注目してください。この処理にはほとんど何もありません。

しかし、このコードは安全で、堅牢で、非同期で、何百万ものドキュメントを処理するために簡単に修正することができます。

単純に、より大きなバルクサイズを設定して、より大きなデータソースを反復処理するだけです。
データがどこから来るかは問題ではない。

データベースのテーブル、CSVファイル、ウェブのクロールなどを反復処理することができます。

## 検索

インデックスに文書があれば、以下のように文書を検索することができます。

RecipeSearch.kt

```
suspend fun search(text: String, start: Int, hits: Int):
  SearchResponse<Recipe> {
    return repository.search {
      configure {
        from = start
        resultSize = hits
        query = if(text.isBlank()) {
          matchAll()
        } else {
          bool {
            should(
              matchPhrase("title", text) {
                boost=2.0
              },
              match("title", text) {
                boost=1.5
                fuzziness="auto"
              },
              match("description", text)
            )
          }
        }

      }
    }.toSearchResponse()
  }
```

ご覧のように、検索も同様にシンプルです。

検索拡張機能は、SearchRequestをカスタマイズするためのブロックを取ります。
ブロックの中ではサイズを設定して、検索結果を複数ページに分けて表示することができるようにしています。

最も難しいのはクエリを追加することです。
このためにクライアントはいくつかのオプションを提供しています。

このケースでは、RestHighLevelClientのJavaビルダーの処理をもう少し簡単にするために、Kotlinのapply extension関数を使用しています。

この利点は、ビルダー・メソッドを連鎖させる必要がなく、コンパイル時の安全性を確保できることです。
また、テンプレート化された複数行の文字列をソースとして使用することもできました。

### SearchResponse

**生のElasticsearchレスポンスを返すのはあまり好ましくないので、**独自のレスポンスフォーマットを使い、Elasticsearchが返すオブジェクトを拡張関数を使って変換しています。

SearchResponse.kt
```
data class SearchResponse<T : Any>(val totalHits: Long, val items: List<T>)

suspend fun <T : Any> AsyncSearchResults<T>
.toSearchResponse(): SearchResponse<T> {
  val collectedHits = mutableListOf<T>()
  this.mappedHits.collect {
    collectedHits.add(it)
  }
  return SearchResponse(this.total, collectedHits)
}
```


## シンプルなオートコンプリート

title.autocompleteフィールドにカスタムアナライザーを追加したので、それも実装できます。
そのためのレスポンスフォーマットは同じです。

私たちのマッピングでは、単純なエッジ `ngramアナライザ`を使用しています。

```
suspend fun autocomplete(text: String, start: Int, hits: Int):
  SearchResponse<Recipe> {
    return repository.search {
      configure {
        from = start
        resultSize = hits
        query = if(text.isBlank()) {
          matchAll()
        } else {
          match("title.autocomplete", text)
        }

      }
    }.toSearchResponse()
  }
```


## Creating a Ktor server

シンプルなRESTサービスでビジネスロジックを公開するために、KTorを使用します。

最近のバージョンの`Spring Boot`は`co-routines`もサポートしているので、`Spring Boot`でこの例に沿って使うことができるかもしれません。

```
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
```

KTorでは、サーバを定義するためにDSLを使用します。

この場合、Jackson オブジェクトマッパーを再利用してコンテンツネゴシエーションとデータ変換を設定し、いくつかのシンプルなエンドポイントを持つルータを追加します。

`withContext { ... } ` を使用して、サスペンドするビジネス・ロジックを起動します。

これは、非同期処理が完了するまで ktor のパイプラインを一時停止します。


## リクエストを実行する

サーバを起動するには、IDEからServerMainを実行してElasticsearchを起動するだけです

試しに、es_kibanaディレクトリにあるdocker-composeファイルを使って起動して見ましょう。

起動後、いくつかのcurlリクエストができるようになるはずです。

```
$ curl -X DELETE localhost:8080/recipe_index
$ curl -X POST localhost:8080/recipe_index
$ curl -X POST localhost:8080/index_examples
```

```
$ curl 'localhost:8080/search?q=banana'
```

結果

```
{"total_hits":1,"items":[{"title":"Banana Oatmeal Cookie","description":"This recipe has been handed down in my family for generations. It's a good way to use overripe bananas. It's also a moist cookie that travels well either in the mail or car.","ingredients":["1 1/2 cups sifted all-purpose flour","1/2 teaspoon baking soda","1 teaspoon salt","1/4 teaspoon ground nutmeg","3/4 teaspoon ground cinnamon","3/4 cup shortening","1 cup white sugar","1 egg","1 cup mashed bananas","1 3/4 cups quick cooking oats","1/2 cup chopped nuts"],"directions":["Preheat oven to 400 degrees F (200 degrees C).","Sift together the flour, baking soda, salt, nutmeg and cinnamon.","Cream together the shortening and sugar; beat until light and fluffy. Add egg, banana, oatmeal and nuts. Mix well.","Add dry ingredients, mix well and drop by the teaspoon on ungreased cookie sheet.","Bake at 400 degrees F (200 degrees C) for 15 minutes or until edges turn lightly brown. Cool on wire rack. Store in a closed container."],"prep_time_min":0,"cook_time_min":0,"servings":24,"tags":["dessert","fruit"],"author":{"name":"Blair Bunny","url":"http://allrecipes.com/cook/10179/profile.aspx"},"source_url":"http://allrecipes.com/Recipe/Banana-Oatmeal-Cookie/Detail.aspx"}]}
```

