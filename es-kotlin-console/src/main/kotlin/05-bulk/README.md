# Bulk Indexing

一括処理を簡単に
Elasticsearch での作業の重要な部分は、コンテンツの追加です。CRUDのサポートはインデックス内の個々のオブジェクトを操作するのには便利ですが、大量のデータを送信するのには適していません。

そのためには、バルクインデックスを使用する必要があります。ElasticsearchのバルクAPIは、ESの中でも特に複雑なAPIの一つです。Kotlinクライアントは、バルクインデックス作成を簡単に、堅牢に、そして簡単にするために、いくつかの重要な抽象化を提供しています。



## リポジトリを使用してインデックスを一括作成する

ここでもThingクラスを使用してthingRepository


これを簡単にするために、ライブラリには`BulkIndexingSession`が付属しています。これは、バルクリクエストを構築して送信するためのすべてのボイラープレートを処理します。
もちろん、私たちの `IndexRepository` は、セッションを作成するシンプルなバルクメソッドを提供します。

```
// creates a BulkIndexingSession<Thing> and passes it to the block
repo.bulk {
  1.rangeTo(500).forEach {
    index("doc-$it", Thing("indexed $it", 666))
  }
}

println("Lets get one of them " + repo.get("doc-100"))
```

キャプチャされた出力：

```
Lets get one of them Thing(name=indexed 100, amount=666)
```


## bulkSize

`BulkIndexingSession`はインデックス操作を`BulkRequest`リクエストに集約し、Elasticsearchに送信してくれます。
`bulkSize`パラメータを設定することで、各リクエストで送信される操作の数を制御することができます。`BulkIndexingSession` は `AutoClosable`を実装しておりクローズされたときに最後のリクエストを送信します。
もちろん、これらはすべて`bulk`メソッドで処理されます。

`index更新`以外にも`update`や`getAndUpdate`などのいくつかの操作があります。

```
// bulkSize = 50
// 以下の例では4件の処理を行っています。
repo.bulk(bulkSize = 50) {
    // create=false の設定が上書きされて適切なものになります。
    // ドキュメントを一括で置換する場合に行うこと
    index("doc-1", Thing("upserted 1", 666), create = false)

    // getAndUpdate
    // CRUDアップデートと同じように、安全な一括アップデートを行うことができます。
    // しかし、これはアイテムごとに1つの取得を行うという欠点があり、スケールしない可能性があります。
    getAndUpdate("doc-2") { currentVersion ->
        // これはupdateと同じように動作しリトライも行います。
        currentVersion.copy(name = "updated 2")
    }

    // update
    // repo.update()とは違う動作なので注意
    // seqNo、primaryTermsを指定する必要があります。
    // この場合は取得をスキップすることができます。これらを効率的に取得する良い方法は scrolling検索です。
    // 以下の例ではあえて更新を失敗しています。
    update(
        id = "doc-3",
        // この2つの値(seqNo, primaryTerms)はあえて間違っている値をセットしています。
        // getAndUpdate.
        seqNo = 12,
        primaryTerms = 34,
        original = Thing("indexed $it", 666)
    ) { currentVersion ->
        currentVersion.copy(name = "safely updated 3")
    }

    // アイテムを削除することもできます。
    delete("doc-4")
}

println(repo.get("doc-1"))
println(repo.get("doc-2"))
println(repo.get("doc-3"))
// should print null
println(repo.get("doc-4"))
```

キャプチャされた出力：

```
Thing(name=upserted 1, amount=666)
Thing(name=updated 2, amount=666)
Thing(name=indexed 3, amount=666)
null
```


## コールバック

バルクインデキシングの重要な側面は、実際にレスポンスを検査することです。`BulkIndexingSession` はコールバックの仕組みを使用しており、レスポンスに何かをさせることができます。
このためのデフォルトの実装では、2つのことを行います。

* 失敗をログに記録します
* 競合する更新を再試行します

ほとんどのユーザーにとってはこれで問題ありませんが、必要であれば、何かカスタムなことをすることができます。

```
repo.bulk(
  // コールバック
  itemCallback = object : (BulkOperation<Thing>, BulkItemResponse) -> Unit {
      // Elasticsearchがバルクリクエストの各項目に対して何をしたかを確認する
      // そして、このコールバックを実装することで、カスタムなことを行うことができます。
      override fun invoke(op: BulkOperation<Thing>, response: BulkItemResponse) {
          if (response.isFailed) {
              // ここを通るはずです。
              println(
                  "${op.id}: ${op.operation.opType().name} failed, " +
                      "code: ${response.failure.status}"
              )
          } else {
              println("${op.id}: ${op.operation.opType().name} succeeded!")
          }
      }
  }
) {
  // bulk処理
  // seqNo、primaryTermsを指定しての楽観的ロック更新
  // この場合は取得をスキップすることができます。これらを効率的に取得する良い方法は scrolling検索です。
  // 以下の例ではあえて更新を失敗しています。
  update(
      id = "doc-2",
      // these values are wrong and this will now fail instead of retry
      // これらの値は間違っており、再試行する代わりに失敗するようになりました
      seqNo = 12,
      primaryTerms = 34,
      original = Thing("updated 2", 666)
  ) { currentVersion ->
      currentVersion.copy(name = "safely updated 3")
  }
}
println("" + repo.get("doc-2"))
```


キャプチャされた出力：

```
doc-2: UPDATE failed, code: CONFLICT
Thing(name=indexed 2, amount=666)
```

更新が失敗した事がCallback関数内で検知できています。


## その他のパラメータ

オーバーライドできるパラメーターは他にもいくつかあります。


```
// オーバーライドできるパラメーターサンプル
repo.bulk(
    // Elasticsearchに送信する項目数を制御する
    // 何が最適かは、ドキュメントのサイズとクラスタの設定を行います。
    bulkSize = 10,
    // デフォルトでドキュメントを再試行する頻度を制御します。
    retryConflictingUpdates = 3,
    // Elasticsearch の更新方法と更新するかどうかを制御します。
    // ES がリフレッシュするまでの間、バルクリクエストをブロックするかどうかを指定します。
    refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE,
    // item callback
    itemCallback = object : (BulkOperation<Thing>, BulkItemResponse) -> Unit {
        override fun invoke(op: BulkOperation<Thing>, response: BulkItemResponse) {
            if (response.isFailed) {
                // ここを通るはずです。
                println(
                    "${op.id}: ${op.operation.opType().name} failed, " +
                        "code: ${response.failure.status}"
                )
            } else {
                println("${op.id}: ${op.operation.opType().name} succeeded!")
            }
        }
    }
) {

    // 削除
    delete("doc-1")

    // update
    getAndUpdate("doc-2") { currentVersion ->
        // これはupdateと同じように動作しリトライも行います。
        currentVersion.copy(name = "updated 2")
    }

    // seqNo, primaryTermsを指定しての更新
    update(
        id = "doc-2",
        // these values are wrong so this will be retried
        seqNo = 12,
        primaryTerms = 34,
        original = Thing("updated 2", 666)
    ) { currentVersion ->
        currentVersion.copy(name = "safely updated 3")
    }
}
println("" + repo.get("doc-2"))

```

キャプチャされた出力：

```
doc-1: DELETE succeeded!
doc-2: UPDATE succeeded!
doc-2: UPDATE failed, code: CONFLICT
Thing(name=updated 2, amount=666)
```

