# Kotlin Query DSL

Elasticsearch には Query DSL があり、Java Rest High Level Client にはプログラムでクエリを構築するための非常に拡張性の高いビルダーが付属しています。
もちろん、Kotlinではビルダーは避けるべきものです。

このページでは、Javaクライアントに付属のビルダーを使ってプログラム的にクエリを構築する方法、json文字列を使ってクエリを構築する方法、Kotlin DSLを使ってクエリを構築する方法をいくつか紹介します。

ここでは先ほどの Search と同じ例を使います。

## Java Builders

Javaクライアントには｀org.elasticsearch.index.query.QueryBuilders｀が付属しており、様々なクエリ用のビルダーを作成するための静的メソッドを提供しています。

これはクエリDSLのほとんどをカバーしていますが、おそらくすべてをカバーしているわけではありませんが、一般的によく使われるものをカバーしているはずです。

```
val results = repo.search {
  source(
    searchSource()
      .size(20)
      .query(
        boolQuery()
          .must(matchQuery("name", "quick").boost(2.0f))
          .must(matchQuery("name", "brown"))
      )
  )
}
println("We found ${results.totalHits} results.")
```

Captured Output:

```
We found 3 hits results.
```


これは残念ながらKotlinの観点から見ると非常に醜いものです。

これを少しきれいにできるかどうか見てみましょう。

### apply

```
// applyを使用して、より多くのidomaticを使用しています。
val results = repo.search {
  source(
    searchSource().apply {
      query(
        boolQuery().apply {
          must().apply {
            add(matchQuery("name", "quick").boost(2.0f))
            add(matchQuery("name", "brown"))
          }
        }
      )
    }
  )
}
println("We found ${results.totalHits} results.")

```
Captured Output:

```
We found 3 hits results.
```

apply を使うことで、すべての呼び出しを連鎖させる必要がなくなり、少しはマシになりましたが、まだ少し冗長です。

## Kotlin Search DSL

このライブラリでは、型安全なDSL構造と、マップの単純なスキーマレス操作の両方を混在させることができるDSLを提供しています。

これがどのように動作するかを示すために、上記の同じクエリのいくつかのバージョンを示します。

以下の例では、先ほどと同じクエリを設定するためにタイプセーフな方法を使用しています。

```
// apply { ... }を使用して、より多くのidomaticを使用しています。
val results = repo.search {
    // SearchRequest.dsl is the extension function that allows us to use the dsl.
    configure {
        // SearchDSLはこのようにブロックに渡されます。
        // MapBackedProperties クラスを拡張します。
        // これにより、プロパティを MutableMap にデリゲートすることができます。

        // from はマップに格納されているプロパティです。
        from = 0

        // MapBackedPropertiesは実際にはMutableMapを実装しています。
        // そして、単純なMutableMapにデリゲートします。
        // これでも動作します: this["from"] = 0

        // 残念ながら、マップは独自のサイズプロパティを持っているので、それを使用することはできません。
        // これをクエリサイズのプロパティ名として使用します :-(
        // queryはESQueryのインスタンスを取得する関数です。
        resultSize = 20
        // これは実際にマップに「サイズ」というキーを入れます。

        // queryはESQueryのインスタンスを取得する関数です。
        query =
            // boolはBoolQueryを作成する関数です。
            // ブロックに注入されるESQueryを拡張したものです。
            bool {
                // BoolQueryにはmustという関数があります。
                // フィルタ、should、mustNotも持っています。
                must(
                    // それはESQueryのvarargリストを持っています。
                    match("name", "qiuck") {
                        // match は常にフィールドとクエリを必要とします。
                        // しかし、ブーストはオプションです。
                        boost = 2.0
                        // 誤字脱字にもかかわらず、何かを見つけることができます。
                        fuzziness = "auto"
                    },
                    // しかし、ブロックパラメータはnullableで
                    // デフォルトはnull
                    matchPhrase("name", "quick brown") {
                        slop = 1
                    }
                )
            }
    }
}
println("We found ${results.totalHits} results.")
```

Captured Output:

```
We found 3 hits results.
```

## Extending the DSL

注。以下はライブラリで実装されています。

Elasticsearch DSL は巨大なものであり、Kotlin DSL でカバーされているのはほんの一部に過ぎない。
DSL をスキーマレスモードで使用することで、この問題を回避することができ、もちろん両方のアプローチをミックスすることができます。

しかし、DSLに何かを追加する必要がある場合は、自分で簡単にできます。

例えば、上で使用したマッチの実装は以下のようになります。


```
enum class MatchOperator { AND, OR }

@Suppress("EnumEntryName")
enum class ZeroTermsQuery { all, none }

@SearchDSLMarker
class MatchQueryConfig : MapBackedProperties() {
  var query by property<String>()
  var boost by property<Double>()
  var analyzer by property<String>()
  var autoGenerateSynonymsPhraseQuery by property<Boolean>()
  var fuzziness by property<String>()
  var maxExpansions by property<Int>()
  var prefixLength by property<Int>()
  var transpositions by property<Boolean>()
  var fuzzyRewrite by property<String>()
  var lenient by property<Boolean>()
  var operator by property<MatchOperator>()
  var minimumShouldMatch by property<String>()
  var zeroTermsQuery by property<ZeroTermsQuery>()
}

@SearchDSLMarker
class MatchQuery(
  field: String,
  query: String,
  matchQueryConfig: MatchQueryConfig = MatchQueryConfig(),
  block: (MatchQueryConfig.() -> Unit)? = null
) : ESQuery(name = "match") {
  // The map is empty until we assign something
  init {
    putNoSnakeCase(field, matchQueryConfig)
    matchQueryConfig.query = query
    block?.invoke(matchQueryConfig)
  }
}

fun SearchDSL.match(
  field: String,
  query: String, block: (MatchQueryConfig.() -> Unit)? = null
) = MatchQuery(field, query, block = block)
```


詳細は、次の章(Extending and Customizing the Kotlin DSLs)を参照してください。

