# Asynchronous IO with Co-routines

RestHighLevelClientは、レスポンスが返ってきたときにコールバックを受けて処理するほとんどのAPIの非同期バージョンを公開しています。これを使用するのは、ちょっとした手間がかかります。

幸いなことに、Kotlinには非同期プログラミングのための`co-routine`があり、このライブラリはこれらの関数の`co-routine`フレンドリーなバージョンを提供しています。
これらの`suspend`関数は、`suspend`としてマークされ、Kotlinの`suspendCancellableCoroutine`を使った`SuspendingActionListener`を使って、残りの高レベルクライアントが期待するコールバックをラップするという点を除けば、同期版とほとんど同じように動作します。

Elasticsearch 7.5.0の時点では、すべての非同期呼び出しはタスクをキャンセルできる`Cancellable`オブジェクトを返すようになっています。
`suspendCancellableCoRoutine`を使用すると、何らかの障害が発生したり、コアーチンスコープを中止したりすると、実行中のタスクはすべてキャンセルされます。

`Ktor`や`Spring Boot 2.x`のような非同期サーバーフレームワークを使用している場合(リアクティブモードで)、非同期関数を使用したいと思うでしょう。

## asyncメソッド

`co-routines`をサポートするために、このプロジェクトでは[コード生成プラグイン](https://github.com/jillesvangurp/es-kotlin-codegen-plugin)を使用して、`Rest High Level async`関数のそれぞれの`co-routines`フレンドリーなバージョンを生成しています。
現時点では、それらのほとんどがカバーされています。その数は100以上あります。

[jillesvangurp/コード生成プラグイン](https://github.com/jillesvangurp/es-kotlin-codegen-plugin)

例として、ここでは`reloadAnalyzers API`を使用する3つの方法を紹介します。


### 1. 同期バージョン

RestHighLevel クライアントによって提供される同期バージョン

```
val ic = esClient.indices()
```

```
val response = ic.reloadAnalyzers(
  ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT
)
```

### 2. 非同期バージョン

RestHighLevel クライアントによって提供されるコールバックを使用した非同期バージョン

```
ic.reloadAnalyzersAsync(
  ReloadAnalyzersRequest("myindex"),
  RequestOptions.DEFAULT,
  object : ActionListener<ReloadAnalyzersResponse> {
    override fun onFailure(e: Exception) {
      println("it failed")
    }

    override fun onResponse(response: ReloadAnalyzersResponse) {
      println("it worked")
    }
  }
)
```

### 3. コアーチンフレンドリーなバージョン

`co-routine`フレンドリーなバージョンです。

コードジェネレーターのプラグインで生成された関数を使用したcoroutineフレンドリーなバージョンは、サスペンドバージョンなので、coroutineスコープを取得するためにrunBlockingに入れていますが、
もちろんあなた自身のアプリケーションでより適切なスコープを使用してください。

```
runBlocking {
  val response2 = ic.reloadAnalyzersAsync(
    ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT
  )
}
```


## AsyncIndexRepository

`RestHighLevelClient`のほとんどの関数のサスペンド版を持つことに加えて、`IndexRepository`には`AsyncIndexRepository`のカウンター部分があります。

これのAPIは通常のリポジトリと似ています。

indexの作成

```
// 拡張関数を使用して新しいリポジトリを作成することができます。
val asyncRepo = esClient.asyncIndexRepository<Thing>("asyncthings")

// asyncRepo上のすべての関数はもちろんサスペンドされているので、
// それらをサスペンドさせるためにはサスペンドされた関数をサブルーチンで実行する必要があります。
runBlocking {
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
```

データ

```
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
  asyncRepo.refresh()
  val count = asyncRepo.count { }
  println("indexed $count items")
}
```

Captured Output:

```
indexed 10 items
```


## Asynchronous search

非同期検索APIは非常に似ています。
返されるAsyncSearchResults以外は似ています。
結果は`Kotlin Co-Routines`ライブラリの`Flow`APIを利用しています。

```
import kotlinx.coroutines.flow.count

runBlocking {
  val results = asyncRepo.search(scrolling = true) {
    configure {
      query = matchAll()
    }
  }

  // hits is a Flow<Thing>
  println("Hits: ${results.mappedHits.count()}")
}
```

Captured Output:

```
Hits: 10
```

