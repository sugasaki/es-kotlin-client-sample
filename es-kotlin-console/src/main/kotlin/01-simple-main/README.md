# Getting Started

## 依存関係の追加

始めるには、jarファイルに依存関係を追加する必要があります。

```
implementation("com.github.jillesvangurp:es-kotlin-client:VERSION")
```

ES Kotlinクライアントを使用するには、Java High Level Restclientのインスタンスを作成するだけです。これにはいくつかの方法があります。

## Clientの作成

### Javaの方法 （非推奨）

以前にJava Highlevelクライアントを使用したことがある場合は、特別にする必要はありません。
いつものようにJava Highlevel Restクライアントを作成するだけです。例えば、以下のようにします。

```
val restClientBuilder = RestClient.builder(
  HttpHost("localhost", 9200, "http")
)
val restHighLevelClient = RestHighLevelClient(restClientBuilder)
```


### 拡張機能を使う方法 （推奨）

Javaの方法は少し重いです。

そこで、多くのパラメータとまともなデフォルト値を持つ create() 拡張関数の形で代替案を提供します。

Clientの作成は、org.elasticsearch.clientに定義されたcreate()拡張関数でおこないます。

create()は多くのパラメータを持っていますが、一番シンプルな作成は以下で可能です
localhost:9200で動作しているElasticsearchサーバーに接続します。

```
// connects to localhost:9200
val esClient = create()
```

#### パラメータ

他にもuser, passwordの指定などのパラメータがあります。
例えば、[Elastic Cloud](https://www.elastic.co/jp/cloud/)に接続する場合には以下のようになります。

```
// connects to Elastic Cloud
val restHighLevelClient = create(
    host = "XXXXXXXXXX.eu-central-1.aws.cloud.es.io", // Elastic CloudのEndpoint
    port = 9243,
    https = true,
    user = "admin",
    password = "secret" // パスワードを入力
)
```


## 簡単な例

```
data class Foo(val message: String)

val fooRepo = esClient.indexRepository<Foo>("my-index", refreshAllowed = true)
fooRepo.index(obj=Foo("Hello World!"))

// ensure the document is committed
fooRepo.refresh()

val results = fooRepo.search {
  configure {
    query = matchAll()
  }
}
println(results.mappedHits.first().message)
```

結果

```
Hello World!
```

この単純な例では、インデックスリポジトリを使用してFooデータクラスからシリアライズされたjsonドキュメントをElasticsearchに追加しています。
そして、ドキュメントが検索可能な状態になるようにインデックスを更新します。
最後に、単純な match all クエリを行い、最初の結果を表示します。

これらの機能については、次の章でさらに掘り下げていきます。


## cluster sniffingの設定

アプリケーションが Elasticsearch クラスタに直接アクセスしており、ロードバランサを使用していない場合、クライアント側のロードバランサを使用することができます。この目的のために、create関数にはuseSniffingパラメータがあります。明らかに、Elastic Cloud でホスティングしている場合は、クラスタがロードバランサの後ろにあり、クライアントはクラスタ内のノードに直接話しかけることができないため、これは機能しません。

スニッフィングを使用すると、クライアントは初期ノードからクラスタを検出し、単純なラウンドロービングのロードバランシングやノードの消失からの回復を行うことができます。どちらも本番環境で持っておくと便利な機能です。

```
val restHighLevelClient = create(
  host = "localhost",
  port = 9200,
  useSniffer = true,
  // if requests fail, the sniffer will try to discover non failed nodes
  sniffAfterFailureDelayMillis = 2000,
  // regularly discover nodes in the cluster
  sniffIntervalMillis = 30000
)
```


# コンソールサンプル


簡単なコンソールアプリケーションを作成します。

Elasticsearchは構築済み（もしくはDockerで）ローカル（localhost:9200）で起動しているものとします。

![スクリーンショット 2021-01-13 10.40.02.png](inkdrop://file:gdNQHPKDL)


### 依存関係の追加

jarファイルへの依存関係をgradleプロジェクトに追加します。

build.gradle.ktsファイルに以下の項目を追加します

```
val esKotlinVersion = "1.0.0"
val slf4jVersion = "1.7.30"

repositories {
    jcenter()
}

dependencies {
    implementation("com.github.jillesvangurp:es-kotlin-client:$esKotlinVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.14.0") // es seems to insist on log4j2
    implementation("org.slf4j:log4j-over-slf4j:$slf4jVersion")
}

```


### main

main.ktファイルを以下のようにします。

my-indexインデックスを作成し、
message="Hello Elastic World!"　というドキュメントを追加します。


```
import com.jillesvangurp.eskotlinwrapper.dsl.matchAll
import org.elasticsearch.action.search.dsl
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository

data class Foo(val message: String)

fun main(args: Array<String>) {
    // connects to elastic cloud
    val esClient = create(host = "localhost", port = 9200,)

    val fooRepo = esClient.indexRepository<Foo>("my-index", refreshAllowed = true)
    fooRepo.index(obj = Foo("Hello Elastic World!"))

    // ensure the document is committed
    fooRepo.refresh()
    val results = fooRepo.search {
        dsl {
            query = matchAll()
        }
    }
    println(results.mappedHits.first().message)
}
```

### 実行結果

以下のように表示されれば成功です

```
Hello Elastic World!
```


## refresh apiについて

elasticsearch上でリフレッシュAPIを呼び出す。テスト以外では使ってはいけません。
例えば、検索クエリをテストするときに、インデックスを作成した後に検索を呼び出す前にリフレッシュしたいことがよくあります。
リポジトリ作成時にrefreshAllowedをtrueに設定して明示的にオプトインしなかった場合、UnsupportedOperationExceptionをスローします。

refreshAllowedをtrueに設定は以下

```
esClient.indexRepository<Foo>("my-index", refreshAllowed = true)
```

