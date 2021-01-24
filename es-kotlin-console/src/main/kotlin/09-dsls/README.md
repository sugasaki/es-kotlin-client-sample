# Extending and Customizing the Kotlin DSLs

マッピング、設定、クエリのために提供されている Kotlin DSL は素晴らしいものですが、Elasticsearch が提供するものを 100% カバーしているわけではありません。また、Elasticsearch は新しいリリースのたびにクライアントライブラリに新しいものを追加し続けているので、それに追いつくのは非常に大変です。そこで、我々は一般的に使われているもの、少なくとも我々自身が使っているものをサポートすることに集中するという選択をしました。

幸いなことに、これを回避するのは非常に簡単で、DSL を拡張して新しいもののサポートを追加したり、DSL が構築した任意の JSON 構造を構築するための基本的な機能を使用したりすることができます。

これはどのように動作するのでしょうか？
Elasticsearch は JSON を受け入れる REST api を提供しています。したがって、我々の DSL の目標は、Elasticsearch が期待するものにマッチする JSON にシリアライズできるオブジェクト構造をプログラム的に構築することです。この JSON は Elasticsearch LowLevelClient を介してネットワーク経由で送信されます。

シリアライズのために、私たちは Elasticsearch クライアントが JSON コンテンツを扱うために使用する内蔵フレームワークである XContent を利用しています。

これを行うために、いくつかの有用なKotlin言語の機能を利用しています。その一つがインターフェイスデリゲーションで、特別な基底クラスを実装するために使用します。MapBackedProperties です。これは、インターフェイスデリゲーションを使ってシンプルな Map<String, Any> を実装しています。

DSL のほとんどの場所でブロックが提供されていますが、レシーバはこの型のオブジェクト、またはその型を親とする派生クラスです。これにより、フードの下にあるプロパティをマップ・デリゲートに挿入するだけで定義することができます。

このクラスを単純に拡張することで、このマップにデリゲートするクラスのプロパティを定義することが可能になります。さらに、カスタム クラスのユーザーは、委任されたプロパティとして追加するのを忘れていたものに対して、単に直接マップに値を置くことができます。

## 例: the TermQuery Implementation

例として、ライブラリ内のTermQueryの実装を以下に示します。

```
class TermQueryConfig : MapBackedProperties() {
  var value by property<String>()
  var boost by property<Double>()
}

@SearchDSLMarker
class TermQuery(
  field: String,
  value: String,
  termQueryConfig: TermQueryConfig = TermQueryConfig(),
  block: (TermQueryConfig.() -> Unit)? = null
) : ESQuery("term") {

  init {
    putNoSnakeCase(field, termQueryConfig)
    termQueryConfig.value = value
    block?.invoke(termQueryConfig)
  }
}

fun SearchDSL.term(
  field: String,
  value: String,
  block: (TermQueryConfig.() -> Unit)? = null
) =
  TermQuery(field,value, block = block)
```

TermQueryはESQueryという基底クラスを拡張したもので、1つのフィールド(クエリ名)を別のMapBackedProperties(クエリの詳細)にマップしたMapBackedPropertiesになっています。

ここから先は非常に簡単です。
fieldは、TermConfigurationを持つ別のMapBackedPropertiesオブジェクトのキーとして使用され、この場合は値やブーストなどが含まれます。

最後に、SearchDSL.term拡張関数を追加しました。
これにより、IDEでオートコンプリートを使ってサポートされているクエリを簡単に見つけることができます。

もちろん、独自の拡張関数を追加することもできます。

```
val termQuery = TermQuery("myField", "someValue") {
  boost = 10.0
}

println(termQuery.toString())
```

Captured Output:

```
{
  "term" : {
  "myField" : {
    "value" : "someValue",
    "boost" : 10.0
  }
  }
}
```


ご覧のように、TermQueryはJSONを表示する便利なtoString実装を継承しています。
これは、DSL を使用してプログラムでクエリを作成する場合のデバッグやログ記録に便利です。

また、TermConfiguration でデリゲートされたプロパティを使用していることにも注目してください。
これにより、DSL を使用しているときに、簡単な代入を使用してこれらのプロパティに値を設定することができます。

しかし、ここに何かを追加するのを忘れていて、用語クエリ設定に foo という名前の（既存のものではない）プロパティを設定する必要があるとします。

```
val termQuery = TermQuery("myField", "someValue") {
  // we support boost
  boost = 2.0
  // but foo is not something we support
  // but we can still add it to the TermQueryConfig
  // because it is backed by MapBackedProperties
  // and implements Map<String, Any>
  this["foo"] = "bar"
}

println(termQuery)
```

Captured Output:

```
{
  "term" : {
  "myField" : {
    "value" : "someValue",
    "boost" : 2.0,
    "foo" : "bar"
  }
  }
}
```


明らかに、Elasticsearchはこのクエリをバッドリクエストでリジェクトしますが、これは用語クエリにfooプロパティがないからです。


## より複雑な JSON を作成する

任意のjsonをかなり簡単に構築することができます。
jsonオブジェクトを作成したい場合は、`mapProps`を使います。

```
val aCustomObject = mapProps {
  // mixed type lists
  this["icanhasjson"] = listOf(1, 2, "4")
  this["meaning_of_life"] = 42
  this["nested_object"] = mapProps {
    this["another"] = mapProps {
      this["nested_object_prop"] = 42
    }
    this["some more stuff"] = "you get the point"
  }
}

println(aCustomObject)
```

Captured Output:

```
{
  "icanhasjson" : [
  1,
  2,
  "4"
  ],
  "meaning_of_life" : 42,
  "nested_object" : {
  "another" : {
    "nested_object_prop" : 42
  },
  "some more stuff" : "you get the point"
  }
}
```

マップ内で異なるタイプを混在させることができます。
XContent が物事をシリアライズできるようにするために、`MapBackedProperties`の`toXContent`関数の一部として`writeAny`拡張関数を使用しています。
この関数は現在、ほとんどのプリミティブ、マップ、列挙型、イテレータブルなどをサポートしています。


## スネークケースとキャメルケース

Elasticsearch のほとんどの API は DSL で使用される json キーにスネークケース (小文字とアンダースコア) を想定しています。一方、Kotlinでは変数名のようなものにはキャメルケースを使用します。

そのため、MapBackedProperties はフィールド値をスネークケースにする put 実装を使用します。フィールド名のようなものにはこれは望ましくないので、この動作を回避するために putNoSnakeCase メソッドを使用する必要があります。

## XContent 拡張モジュール

`XContent`は Elasticsearch や Elasticsearch Java クライアントが内部で JSON コンテンツを扱うために使用しているものです。

Jackson, GSon, kotlinx-serialization などの扱いに慣れている人にとっては、かなり異質なものかもしれませんが、このライブラリは XContent を簡単に扱えるようにするための拡張関数をいくつか提供しています。

大抵の問題は、何らかの種類の json 構造体をパラメータとして期待している java ライブラリ関数に XContent を提供することにあります。

これらの Java 関数の多くは、適切な XContent を生成する Java ビルダーか、あるいは任意の XContent オブジェクトを受け入れるビルダーを付属しています。

DSL関数で述べたように、もちろんこれらのビルダーを使用することができます。
しかし、ビルダーのパターンはKotlinではあまり良くないので、`Kotlin DSL`も提供しています。

しかし、時にはビルダーをバイパスして、jsonを直接Elasticsearchに提供したいこともあるだろう。
そのために、SearchRequest, CountRequest, その他のいくつかのリクエストに、文字列(またはKotlinの複数行の文字列)または生のjsonを受け取るためのいくつかのソース拡張関数を提供しています。

上記の MapBackedProperties はもちろん ToXContent インターフェースを実装しており、そのインスタンスを使って前述のソース関数に渡すことができます。


## DSL の拡張

検索DSLの基本的な用語、テキスト、複合クエリ、およびそれらの設定プロパティのほとんどをカバーしています。
現在は、必要に応じて追加しています。

しかし、我々がまだ提供していないものを必要とする場合は、DSLを拡張することは非常に簡単です。
単純に ESQuery を拡張し、上記で説明したようにデリゲートされたプロパティを使用します。

また、`SearchDSL`に拡張機能を追加することも忘れないでください。
もちろん、新しいクエリタイプのプルリクエストや既存のクエリタイプの改良も歓迎します。

また、Elasticsearchクライアントには、現在サポートしていない独自のDSLを持つクライアントAPIもあります。
これらについては、独自の DSL を作成することもできます。

もちろん、そのためのプルリクエストも大歓迎です。
