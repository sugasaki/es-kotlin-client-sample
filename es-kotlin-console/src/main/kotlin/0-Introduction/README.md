# es-kotlin-clinent マニュアル（意訳）



[Elasticsearch Kotlin Client Manual](https://www.jillesvangurp.com/es-kotlin-manual/index.html)

Elasticsearch Kotlin Clientは、Elasticsearchが提供する高レベルのElasticsearch HTTPクライアント for Java に適応させる Kotlinで書かれたクライアントライブラリです。


# 序章（抜粋）

公式の Java クライアントは、基本的に`REST API`で公開されているすべての機能を提供しています。
Elasticsearch Kotlin Client は、この機能の利用をより Kotlin と親しみやすいものにしている。

これは多くの便利な機能やショートカットを追加する拡張機能によって実現されている。
クエリ、マッピングの定義、バルクインデックス作成のための Kotlin DSL を追加している。
最も一般的なユースケースを容易にするために、このライブラリはリポジトリの抽象化も提供しています。

さらに、Javaライブラリの非同期クライアントの`co-routine`フレンドリーなバージョンも提供しています。
ユーザーはKtorやSpring Bootのような完全にリアクティブなコードを書くことができます。
このライブラリは、現在のところ最も簡単な方法となっています。


# 序章（全文）

Elasticsearch用Kotlinクライアントのマニュアルです。
サーバサイドでの Kotlin 開発を目的としており、Elastic が提供する Java クライアントの上にフレンドリーな Kotlin API を提供しています。

Elastic の HighLevelRestClient は Java で書かれており、Elasticsearch が公開している REST API の基本的にすべてへのアクセスを提供します。このJava APIは、OSSとx-packのすべての機能へのアクセスを提供しています。しかし、直接作業するには最も簡単なものではありません。

EsのKotlinクライアントはそのような力を何も奪いませんが、多くの力と利便性を加えています。

KotlinクライアントはJavaクライアントをいくつかの方法で拡張します。拡張関数を使って機能を追加したり、生成されたコードを使って内蔵の非同期関数をKotlinのコ・ルーチンに適応させたり、クエリやマッピングなどのためのKotlin Domain Specific Languages (DSL)を追加したりします。

必要に応じて、基本となるJavaの機能はいつでも利用できるようになっています。しかし、最も一般的に使われるものについては、このクライアントはKotlinに適した方法で機能にアクセスする方法を提供しています。

プロジェクトの歴史
Elasticsearch 1.0がリリースされる前に、私はElasticsearchの最初のクライアントをJavaで書いた。これは私が2012年に設立したスタートアップの内部プロジェクトでした。私はそれをオープンソース化するつもりだったが、それは実現しなかった。Elasticsearch 2.0 がリリースされたとき、私はこれを修正しようとし、OSS 版のコードベースを作成しました。

しかし、自分たちのコードベースを2.0に更新するまでには至らなかったので、コードは完成することはありませんでした。その後、Elasticsearch 5.0 はかなりメジャーな方法で互換性を壊してしまい、私はそれをサポートすることができませんでした。その上、新しい Java の低レベルクライアントが含まれていたので、コードベースの作業を増やす気が失せてしまいました。高レベルのクライアントは 6.0 に含まれていました。

数年前、私は Kotlin を使い始め、同時に（最終的に）コードベースを Elasticsearch の最新バージョンに移行した。その頃には Elastic が RestHighLevel クライアントをリリースしていたので、それを使ってみたのですが、今までに何度か構築してきた機能を見逃していることにすぐに気付きました。例えば、Bulk APIの複雑さを管理するBulkセッションを持っていたり、スクロール検索の結果をイテレートするのに便利な方法を持っていたり、jsonドキュメントをシリアライズしてデシリアライズする方法を知っている便利なインデックスリポジトリを持っていたり、というようなことです。

この頃までに、私は別のプロジェクト（Elasticsearch 5.x用）でJava用のシンプルなhttpクライアントを再構築したこともあり、同じようなものをまた作ってしまったような気がします。

そこで2018年、Kotlinのためにもう一度この機能を構築したいと考えていたことに気づき、今回はちゃんとやって、初日からオープンソースプロジェクトにすることにしました。当時まだ在籍していたスタートアップが同時期に閉鎖してしまったので、すぐに使うプロジェクトはありませんでした。しかし、当時はElasticsearchのスペシャリストとしてフリーランスとして活動していたので、プロジェクトを立ち上げるのに十分なダウンタイムがありました。それからの数年間、私はクライアントの仕事を続け、最終的にはいくつかのプロジェクトで使うようになりました。使うたびに、もっと機能を追加したい、もっと便利にしたい、コードベースを改善する方法をもっと見つけたいと思うようになりました。

意図的に1.0のリリースを遅らせたのは、間違っていると感じたり、仕事が必要だと感じたAPIのサポートにコミットすることで、自分自身を失敗に陥らせないためです。ある時点で、1.0 に到達するためには何が必要かを考え始め、それらの問題を一つずつ修正していきました。私のリストの大きな項目は、実際に動作する例を含むドキュメントを作成することでした。

このマニュアルはその努力の結果です。
