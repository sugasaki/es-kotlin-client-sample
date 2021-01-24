# 4.　楽観的ロックによる更新

Elasticsearchの便利な機能の1つは、楽観的ロックを実行できることです。（同時更新の抑制）
その方法は、ドキュメントの`seqNo`と`primaryTerm`を追跡(チェック)することです。

`seqNo`と`primaryTerm`の両方をインデックス登録時に指定すると、
`seqNo`と`primaryTerm`が既存データと一致するかどうかがチェックされ、ドキュメントが一致する場合にのみドキュメントが上書きされます。


### 0. Sample1

```
// データ追加
val id = "2"

// 初期データ投入、初期データはseqNo = 0,primaryTerm = 1となる。
thingRepository.index(id, Thing("Another thing"))
thingService.consolePrintThing(id)

// update... ①
// seqNo = 0 and primaryTerm = 1のデータを更新する
thingRepository.index(
    id,
    Thing("Another Thing"),
    seqNo = 0,
    primaryTerm = 1,
    create = false
)
// 更新結果をデバッグプリント。更新の結果、seqNo = 1,primaryTerm = 1となる。
thingService.consolePrintThing(id)

// update... ②
// seqNo = 1 and primaryTerm = 1のデータを更新する
thingRepository.index(
    id,
    Thing("Another Thing"),
    seqNo = 1,
    primaryTerm = 1,
    create = false
)
// 更新結果をデバッグプリント。更新の結果、seqNo = 2,primaryTerm = 1となる。
thingService.consolePrintThing(id)

// update... ③
// seqNo = 1 and primaryTerm = 1のデータを更新する
// seqNo = 2となっているので、更新が失敗する
try {
    thingRepository.index(
        id,
        Thing("Another Thing2", amount = 666),
        seqNo = 1,
        primaryTerm = 1,
        create = false
    )
} catch (e: ElasticsearchStatusException) {
    println("Version conflict! Es returned ${e.status().status}")
}
```


### 1. Sample1

ID=2でデータを格納
次に、データを上書きします。
再度上書きします。
seqNoがキーになっていることに注意


```
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

// ... but if we use these values again it fails
try {
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
```


結果確認：キャプチャされた出力

```
obj with name 'Another thing' has id: 2, primaryTerm: 1, and seqNo: 0
Version conflict! Es returned 409
```

### 2. Updateメソッド

`Update`メソッドで、楽観的ロックを少しだけシンプルにしてくれます。

※筆者注) 以下はロックではなくて、単純な後勝ちルールのUpdateに見える。シンプルとはそういうことかな？

```
repo.index("3", Thing("Yet another thing"))

// thingインスタンスを作成して更新
repo.update(id) { Thing("Another Thing", amount = it.amount + 1) }
println("It was updated: ${repo.get("3")}")

// object copyでもOK
repo.update("3") { currentThing ->
    currentThing.copy(name = "we can do this again and again", amount = currentThing.amount + 1)
}
println("It was updated again ${repo.get("3")}")
```

結果確認：キャプチャされた出力

```
It was updated: Thing(name=an updated thing, amount=43)
It was updated again Thing(name=we can do this again and again, amount=44)
```


`Update`は、以前に`manual`更新と同じことをシンプルに行います。

```
# manual更新
repo.index("3", Thing("Another Thing"),
  seqNo = rawGetResponse.seqNo,
  primaryTerm = rawGetResponse.primaryTerm,
  create = false
)

# update
repo.update(id) { Thing("Another Thing") }
```

`Update`の内部動作は
メタデータとともにオブジェクトの現在のバージョンを取得します。
次に、現在のバージョンを更新ラムダ関数に渡し、そこで必要な処理を実行できます。
この場合、Kotlinのcopyを使用してコピーを作成し、フィールドの1つを変更してから、それを新しい値として返します。

#### 同時更新の競合
このupdateメソッドは、バージョンの競合をトラップします。設定した回数分リトライします。
**同じオブジェクトへの同時書き込みがある場合、競合が発生する可能性があります。**
再試行により最新バージョンが取得され、再度更新ラムダを適用し保存しようとします。

##### リトライ無しの動作

再試行なしで何が起こるかをシミュレートするために、これでいくつかのスレッドをスローし、0回の再試行を構成できます。

```
repo.index("4", Thing("First version of the thing", amount = 0))

try {
  // 複数スレッドにて並列実行
  1.rangeTo(30).toList().parallelStream().forEach { n ->
    // maxUpdateTriesパラメータはオプションで、デフォルト値は2です。
    // リトライ回数を 0 に設定して同時更新を行うと失敗します。
    repo.update("4", 0) { Thing("nr_$n") }
  }
} catch (e: Exception) {
  println("It failed because we disabled retries and we got a conflict")
}
```

結果確認：キャプチャされた出力

```
It failed because we disabled retries and we got a conflict
```

##### リトライ10回の動作

10回の再試行で同じことを行うと、問題が修正されます。

```
repo.index("5", Thing("First version of the thing", amount = 0))

1.rangeTo(30).toList().parallelStream().forEach { n ->
  // but if we let it retry a few times, it will be eventually consistent
  repo.update("5", 10) { Thing("nr_$n", amount = it.amount + 1) }
}
println("All updates succeeded! amount = ${repo.get("5")?.amount}.")
```

結果確認：キャプチャされた出力

```
All updates succeeded! amount = 30.
```

##### リトライ10回の動作（失敗）

※筆者注) 10回は確実な数ではなくて、おそらく10回くらいリトライしたら成功するだろうとの見込みと思われる。
試しにスレッド10000で実行

```
repo.index("5", Thing("First version of the thing", amount = 0))
    
// スレッド10000 maxUpdateTries = 10 にて更新
// この更新処理はリトライ回数を10でも失敗する（可能性が高い）
try {
    // 複数スレッドにて並列実行
    1.rangeTo(10000).toList().parallelStream().forEach { n ->
        // この更新処理はリトライ回数を10でも失敗する（可能性が高い）
        repo.update(id, maxUpdateTries = 10) { Thing("nr_$n", amount = it.amount + 1) }
    }
    println("All updates succeeded! amount = ${repo.get(id)?.amount}.")
} catch (e: Exception) {
    println("It failed because we disabled retries and we got a conflict")
    println("amount = ${repo.get(id)?.amount}.")
}
```

結果確認：キャプチャされた出力

```
It failed because we disabled retries and we got a conflict
amount = 5301.
```

5301回目のアップデートで失敗してました。


#### es-kotlinのリトライ内部処理

ライブラリ内では更新エラーが発生した際に、リトライ回数最大値に達するまで再起処理を行っている。

```
} catch (e: ElasticsearchStatusException) {
    if (e.status().status == 409) {
        if (tries < maxUpdateTries) {
            // we got a version conflict, retry after sleeping a bit (without this failures are more likely
            Thread.sleep(RandomUtils.nextLong(50, 500))
            return update(tries + 1, id, transformFunction, maxUpdateTries, requestOptions)
        } else {
            throw IllegalStateException("update of $id failed after $tries attempts")
        }
    } else {
        // something else is wrong
        throw e
    }
}
```

