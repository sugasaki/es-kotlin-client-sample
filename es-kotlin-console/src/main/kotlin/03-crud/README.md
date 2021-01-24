# 3. CRUD 操作

これまでインデックスができたので、CRUD操作を使うことができるようになりました。

### 1. ドキュメント追加


```
val id = "first"
println("Object does not exist: ${repo.get(id)}")

// so lets store something
repo.index(id, Thing("A thing", 42))

println("Now we get back our object: ${repo.get(id)}")
```

結果確認：キャプチャされた出力
```
Object does not exist: null
Now we get back our object: Thing(name=A thing, amount=42)
```

### 2. ドキュメント上書き

上書きされることをオプトインしない限り、オブジェクトに2回インデックスを付けることはできません。

オプトイン = `, create = false`


```
val id = "first"
try {
  repo.index(id, Thing("A thing", 40))
} catch (e: ElasticsearchStatusException) {
  println("we already had one of those and es returned ${e.status().status}")
}

// this how you do upserts
repo.index(id, Thing("Another thing", 666), create = false)
println("It was changed: ${repo.get(id)}")
```

結果確認：キャプチャされた出力
```
we already had one of those and es returned 409
It was changed: Thing(name=Another thing, amount=666)
```

### 3. ドキュメント削除

もちろん、オブジェクトを削除することも可能です。

```
repo.delete("1")
println(repo.get("1"))
```

結果確認：キャプチャされた出力
```
null
```

