# 検索


```
// 今回は少し変わったモデルクラスを使用します
data class Thing(val name: String)
```

探すためにいくつかの文書をインデックス化してみましょう...

```
val esClient = create(host = "localhost", port = 9200)
val repo = esClient.indexRepository<Thing>("things", refreshAllowed = true)

// 検索がすぐに動作するように、ES にすべてをディスクにコミットさせます。
repo.bulk(refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE) {
  index("1", Thing("The quick brown fox"))
  index("2", Thing("The quick brown emu"))
  index("3", Thing("The quick brown gnu"))
  index("4", Thing("Another thing"))
  5.rangeTo(100).forEach {
    index("$it", Thing("Another thing: $it"))
  }
}
repo.refresh()
```



## 検索して結果を使って作業する

検索は以下のようにsearchにQueryを渡しておこないます。

```
import com.jillesvangurp.eskotlinwrapper.dsl.match
import org.elasticsearch.action.search.configure

val results = repo.search {
    configure {
        query = match(field = "name", query = searchText)
    }
}
```

結果は以下で取得します。


### totalHits - 検索ヒット件数

```
val count = results.totalHits
println("Found $count")
```

結果

```
Found 3 hits
```


### mappedHits - デシリアライズ

```
// 検索レスポンスからデシリアライズできます
results.mappedHits.forEach {
    println(it)
}
```

結果

```
print in forEach: Thing(name=The quick brown fox)
print in forEach: Thing(name=The quick brown emu)
print in forEach: Thing(name=The quick brown gnu)
```


### SearchHit - シーケンスオブジェクトを取得

Elasticsearchのシーケンスオブジェクトを取得することもできます。

```
val firstSearchHit = results.searchHits.first()
println("print in searchHits - firstSearchHit: $firstSearchHit") // Elasticsearchのオブジェクト
println("print in searchHits: ${firstSearchHit.id} ${firstSearchHit.seqNo} ${firstSearchHit.primaryTerm} ${firstSearchHit.sourceAsString}")
```

結果

```
print in searchHits - firstSearchHit: {
  "_index" : "things",
  "_type" : "_doc",
  "_id" : "1",
  "_score" : 2.9683104,
  "_source" : {
    "name" : "The quick brown fox"
  }
}
print in searchHits: 1 -2 0 {"name":"The quick brown fox"}
```


applyを使うことも可能です。
例えば、検索結果を使っての一括更新をしたい場合に便利です。

```
firstSearchHit.apply { println("print in an apply: $id $seqNo $primaryTerm $sourceAsString") }
```

結果

```
print in an apply: 1 -2 0 {"name":"The quick brown fox"}
```



### hitsを使って`Pair`として取得

hitsを使って以下の２つを`Pair`として取得できます。

- searchHit: Elasticsearchのシーケンスオブジェクト
- deserialized: デシリアライズされたオブジェクト

```
val (searchHit, deserialized) = results.hits.first()
println("print in hits - id: ${searchHit.id}") // document id
println("print in hits - searchHit: $searchHit") // Elasticsearchのオブジェクト
println("print in hits - deserialized: $deserialized") // デシリアライズされたオブジェクト
```

結果

```
print in hits - id: 1
print in hits - searchHit: {
  "_index" : "things",
  "_type" : "_doc",
  "_id" : "1",
  "_score" : 2.9683104,
  "_source" : {
    "name" : "The quick brown fox"
  }
}
print in hits - deserialized: Thing(name=The quick brown fox)
```


### コードサンプル

```
import com.jillesvangurp.eskotlinwrapper.dsl.match
import org.elasticsearch.action.search.configure

// connects to elastic cloud
val esClient = create(host = "localhost", port = 9200)
val repo = esClient.indexRepository<Thing>("things", refreshAllowed = true)

repo.deleteIndex()

// 100件登録
repo.bulk(refreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE) {
    index("1", Thing("The quick brown fox"))
    index("2", Thing("The quick brown emu"))
    index("3", Thing("The quick brown gnu"))
    index("4", Thing("Another thing"))
    5.rangeTo(100).forEach {
        index("$it", Thing("Another thing: $it"))
    }
}

// 検索がすぐに動作するように、ES にすべてをディスクにコミットさせます。
repo.refresh()

// `brown`で検索します
val searchText = "brown"

// searchにQueryを渡して、SearchRequestを取得
val results = repo.search {
    configure {
        // ここではわかりやすくするために引数に名前を追加しました
        query = match(field = "name", query = searchText)
    }
}

// totalHits - 検索ヒット件数
// 検索ヒット件数
println("\n---- count --------\n") // Elasticsearchのオブジェクト
val count = results.totalHits
println("Found $count")

// mappedHits - デシリアライズ
// mappedHits のレスポンスはThingにデシリアライズされています。
println("\n---- mappedHits --------\n") // Elasticsearchのオブジェクト
results.mappedHits.forEach {
    // 検索レスポンスからデシリアライズできます
    println("print in forEach: $it")
}

// searchHits
// Elasticsearchのシーケンスオブジェクトを取得する
println("\n---- searchHits --------\n") // Elasticsearchのオブジェクト
val firstSearchHit = results.searchHits.first()
println("print in searchHits - firstSearchHit: $firstSearchHit") // Elasticsearchのオブジェクト
println("print in searchHits: ${firstSearchHit.id} ${firstSearchHit.seqNo} ${firstSearchHit.primaryTerm} ${firstSearchHit.sourceAsString}")

// applyを使うことも可能です。    firstSearchHit.apply { println("print in an apply: $id $seqNo $primaryTerm $sourceAsString") }例えば、検索結果を使っての一括更新をしたい場合に便利です。
firstSearchHit.apply { println("print in an apply: $id $seqNo $primaryTerm $sourceAsString") }

// hits - `Pair`として取得
// Elasticsearchのシーケンスオブジェクトと、デシリアライズされたペアオブジェクトを返します。
println("\n---- hits --------\n") // Elasticsearchのオブジェクト
val (searchHit, deserialized) = results.hits.first()
println("print in hits - id: ${searchHit.id}") // document id
println("print in hits - searchHit: $searchHit") // Elasticsearchのシーケンスオブジェクト
println("print in hits - deserialized: $deserialized") // デシリアライズされたオブジェクト
```

キャプチャされた出力。

```

---- count --------

Found 100 hits

---- mappedHits --------

print in forEach: Thing(name=The quick brown fox)
print in forEach: Thing(name=The quick brown emu)
print in forEach: Thing(name=The quick brown gnu)
print in forEach: Thing(name=Another thing)
print in forEach: Thing(name=Another thing: 5)
print in forEach: Thing(name=Another thing: 6)
print in forEach: Thing(name=Another thing: 7)
print in forEach: Thing(name=Another thing: 8)
print in forEach: Thing(name=Another thing: 9)
print in forEach: Thing(name=Another thing: 10)

---- searchHits --------

print in searchHits - firstSearchHit: {
  "_index" : "things",
  "_type" : "_doc",
  "_id" : "1",
  "_score" : 1.0,
  "_source" : {
    "name" : "The quick brown fox"
  }
}
print in searchHits: 1 -2 0 {"name":"The quick brown fox"}
print in an apply: 1 -2 0 {"name":"The quick brown fox"}

---- hits --------

print in hits - id: 1
print in hits - searchHit: {
  "_index" : "things",
  "_type" : "_doc",
  "_id" : "1",
  "_score" : 1.0,
  "_source" : {
    "name" : "The quick brown fox"
  }
}
print in hits - deserialized: Thing(name=The quick brown fox)

Process finished with exit code 130 (interrupted by signal 2: SIGINT)

```


elasticsearchのクエリには、上で使用したKotlin DSL、複数行の文字列の形をした生のjson、Javaクライアントに付属のJavaビルダーなど、いくつかの代替方法があります。ドキュメントは Kotlin Query DSL を参照してください。



## Count

ドキュメントの総数を取得するだけのクエリも可能です。

```
println("The total number of documents is ${repo.count()}")

val searchText = "quick"
val count = repo.count {
    configure {
        query = match("name", searchText)
    }
}
println("We found $count results matching $text")
```


キャプチャされた出力。


```
The total number of documents is 100
We found 3 results matching quick
```


## json文字列を使用する

直接json形式のクエリを実行したい場合もあるでしょう。
Kotlinには`multi line strings`があるので、これは簡単に実行できます。

ESはJSONにコメントを入れることができます。


```
import org.elasticsearch.action.search.source

val results = repo.search {
    // Kotlin の文字列テンプレートを使うことができます。
    val text = "brown"
    source(
        """
{
  "size": 10,
  "query": {
    "match": {
      // ESはJSONにコメントを入れることができるって知ってましたか？
      // 見てください、私たちの変数を注入することができます
      // しかし、もちろんスクリプトインジェクションには注意してください!
      "name": "$text"
    }
  }
}          
        """.trimIndent()
    )
}
println("Found ${results.totalHits}")
```

キャプチャされた出力。

```
Found 3 hits
```


## スクロール検索

スクロールは、大量の結果を処理したい場合に便利です。
スクロールを使用するための古典的なユースケースは、ドキュメントを一括更新することです。

Elasticsearchには、インデックスから大量のドキュメントを取得するためのスクロール検索という概念があります。
これはスクロールトークンを記録しておき、それを`Elasticsearch`に渡して後続のページの結果を取得することで動作します。

スクロールをより簡単で退屈にならないようにするために、リポジトリの検索メソッドにはもっとシンプルな解決策があります。

スクロールを使用するための古典的なユースケースは、ドキュメントを一括更新することです。
これは次のようにして行うことができます。

```
import com.jillesvangurp.eskotlinwrapper.dsl.matchAll
import org.elasticsearch.action.search.configure


repo.bulk {
  // 単純にスクロールをtrueに設定すると、インデックス全体をスクロールすることができます。
  // これはインデックスのサイズに関係なくスケールされます。もし
  // スクロールする場合は、スクロールの ttl を設定することもできます (デフォルトは 1m)。
  val results = repo.search(
    scrolling = true,
    scrollTtlInMinutes = 10
  ) {
    configure {
      // スクロール検索のページサイズ
      // note, resultSizeはsizeに変換されます。しかし、サイズも
      // Map上の関数、我々はこれを回避するために動作します。
      resultSize = 5
      query = matchAll()
    }
  }
  // 結果をたくさんprintしないようにしましょう
  results.hits.take(15).forEach { (hit, thing) ->
    // マッピングのソースをオフにした場合は null になる可能性があります。
    println("${hit.id}: ${thing?.name}")
  }
  // 結果の最後のページの後、スクロールがクリーンアップされます。
}
```


キャプチャされた出力。

```
1: The quick brown fox
2: The quick brown emu
3: The quick brown gnu
4: Another thing
5: Another thing: 5
6: Another thing: 6
7: Another thing: 7
8: Another thing: 8
9: Another thing: 9
10: Another thing: 10
11: Another thing: 11
12: Another thing: 12
13: Another thing: 13
14: Another thing: 14
15: Another thing: 15
```
