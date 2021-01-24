# 2. indexRepositoryの作成

Elasticsearch を使って何かをするためには、ドキュメントを何らかのインデックスに格納しなければならない。

Java クライアントはこれを行うために必要なものをすべて提供しているが、これを正しい方法で使用するには、何を行う必要があるのかを深く理解するだけでなく、かなりのボイラープレートが必要となる。

このライブラリ`es-kotlin-clinent`の重要な部分は、ユーザーフレンドリーな抽象化を提供することです。

`Spring`や`Ruby on Rails`などの最新のフレームワークを使ってデータベースアプリケーションを書いたことがある人には馴染みのある機能です。

このようなフレームワークでは、リポジトリは特定のデータベーステーブル内のオブジェクトと対話するためのプリミティブを提供します。

`es-kotlin-clinent`では同様の抽象化として[IndexRepository](https://github.com/jillesvangurp/es-kotlin-client/blob/master/src/main/kotlin/com/jillesvangurp/eskotlinwrapper/IndexRepository.kt)を提供しています。
インデックスごとに[IndexRepository](https://github.com/jillesvangurp/es-kotlin-client/blob/master/src/main/kotlin/com/jillesvangurp/eskotlinwrapper/IndexRepository.kt)を作成することで、作成、読み込み、更新、削除、等のCRUD操作や他のいくつかの操作を行うことができます。

ElasticsearchはJsonドキュメントを格納しているので、Kotlin側でデータクラスを使って表現し、シリアライズ/デシリアライズはIndexRepositoryに任せます。


## indexRepositoryの作成

#### データクラス

いくつかのフィールドを持つシンプルなデータクラスを使ってみましょう。

```
data class Thing(val name: String, val amount: Long = 42)
```

#### IndexRepositoryを作成

indexRepository拡張関数を使用して、`Thing`用のIndexRepositoryを作成することができます。

```
// we pass in the index name
val repo = esClient.indexRepository<Thing>("things")
```


#### インデックスを作成

オブジェクトを保存する前に、インデックスを作成する必要があります。
これはオプション扱いですが、スキーマレスモードでElasticsearchを使用することはおそらくあなたが望むものではないことに注意してください。
ここではシンプルなマッピングを使用しています。

```
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
```

結果の確認

```
// stringify is a useful extension function we added to the response
println(repository.getSettings().stringify(true))

repository.getMappings().mappings()
    .forEach { (name, meta) ->
        print("$name -> ${meta.source().string()}")
    }
```

結果確認：キャプチャされた出力

```
{
  "test-6a263b18-11de-4d88-b5d3-fc7bc52aaeab" : {
  "settings" : {
    "index" : {
    "creation_date" : "1609078953070",
    "number_of_shards" : "1",
    "number_of_replicas" : "0",
    "uuid" : "C-ZY_-KIRjGdpoju5IkiAw",
    "version" : {
      "created" : "7100099"
    },
    "provided_name" : "test-6a263b18-11de-4d88-b5d3-fc7bc52aaeab"
    }
  }
  }
}
test-6a263b18-11de-4d88-b5d3-fc7bc52aaeab -> {"_meta":{"content_hash":"ZLExK0PCG
9+CpgXySXotIQ==","timestamp":"2020-12-27T14:22:33.052411Z"},"properties":{"amoun
t":{"type":"long","fields":{"abetterway":{"type":"double"},"imadouble":{"type":"
double"},"somesubfield":{"type":"keyword"}}},"name":{"type":"text"}}}
```

## jsonファイルを使ってのインデックス作成

設定用のjsonを使って設定することもできます。
これは、マッピングを別の`json`ファイルとして管理している場合に便利です。

```
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
```

結果確認：キャプチャされた出力

```
{"things":{"mappings":{"properties":{"amount":{"type":"long"},"name":{"type":"text"}}}}}
```

## カスタムシリアル化

デフォルトでは、人気のあるJacksonフレームワークを使用します。
ただし、他のものが必要な場合は、シリアル化と逆シリアル化の動作をカスタマイズできます。これを行うには、`ModelReaderAndWriter`の実装を渡すことができます。

デフォルト値は付属のJacksonModelReaderAndWriterのインスタンスで、ThingオブジェクトのシリアライズとデシリアライズにJacksonを使用しています。

デフォルトのJacksonベースのシリアライズが不要な場合や、Jacksonオブジェクトマッパーをカスタマイズしたい場合は、独自のインスタンスを作成して`IndexRepository`に渡すだけです。


```
// this is what is used by default but you can use your own implementation
val modelReaderAndWriter = JacksonModelReaderAndWriter(
  Thing::class,
  ObjectMapper().findAndRegisterModules()
)

val thingRepository = esClient.indexRepository<Thing>(
  index = "things", modelReaderAndWriter = modelReaderAndWriter
)
```


## Co-routine サポート

このライブラリのほとんどと同じ機能が、コルーチンに適した`AsyncIndexRepository`でも利用できます。

バリアントでも利用できます`AsyncIndexRepository`。これを使用するには、`esClient.asyncIndexRepository`を使用する必要があります

これは、すべての関数が`AsyncIndexRepository`クラスで`suspend`としてマークされていることを除いて、同期版とほぼ同じように機能します。
さらに、検索メソッドの戻り値の型は異なり、`Flow API`を利用します。

ES Kotlinクライアントでコルーチンを使用する方法の詳細については、[コルーチンを使用した非同期IO](https://www.jillesvangurp.com/es-kotlin-manual/coroutines.html)を参照してください。

