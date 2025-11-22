# LazyTable

Jetpack Composeで、巨大な表を複数もつ巨大なページを表示するサンプルです。

## 概要

- LazyColumnの内部に複数の巨大な表がある。最大2000行×100列でテストした。
- 表の内部で可視範囲に応じてセルをcompose/配置してメモリ消費を節約する
- 表の見出し行/見出し列のsticky表示。
- 表は横スクロール可能で、親のLazyColumnのモーションイベント管理と完全に独立しているので斜めスクロールができる。
- 表の各列の幅と各行の幅はあらかじめ計算したものをCompose時に参照する。
- あらかじめ計算で数万セルのサイズ計測をComposeで一度に行うのは不可能だった。
    - ViewフレームワークでComposeとのテキスト計測のズレを減らす工夫を盛り込んだ。

## ライセンス

このサンプルアプリはMITライセンスの下で公開されます。

## 環境要件

- **minSdk**: 30 (Android 11)
- **targetSdk**: 36
- **Kotlin**: 2.0.0
- **Compose BOM**: 2024.04.01
- **Compose Compiler**: 2.0.0

## ビルド方法

```bash
./gradlew assembleDebug
```

----
## 技術的な深掘り

Composeだけで完結した実装ができれば良かったのですが、実際には困難がいくつかありました。
こういった困難と直面して、トリッキーだが現実的に動作する実装を積んでみたのがこのサンプルです。

| 目標            | 困難                                                         | 対策                                                                                                                                                |
|---------------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| 大量のセルの事前サイズ計測 | Composeで行うのは非常に困難。<br>ベタに書くとメモリ消費が非現実的                     | 事前計測部分だけViewフレームワークを使う。<br>テキスト計測結果をComposeと揃えるため`androidx.compose.ui.text.Paragraph`を使う<br>~~けったいな~~カスタムビューを用意した。<br>`AnnotatedString`には対応できていない |
| 表部分の斜めスクロール   | `LazyColumn`の制約により`pointerInput`や`nestedScroll`を使っても実装できない | `Activity.dispatchTouchEvent`を併用                                                                                                                  |

----
## ファイルごとの主な役割

### MainActivity
サンプルの巨大な画面のActivityと表示内容のCompose。
- Activity.dispatchTouchEvent()をオーバライドして横方向のドラッグ量を、タッチ時に触れていた表のHorizontalScrollableに渡す。
- ViewBindingを使うCellMeasurerを持ち、onCreateで開始するコルーチンで各列の幅、各行の高さを計測する。
- テキスト計測のズレがでないよう、ComposeCompatTextView.composeInfo に現在のテーマの情報を渡す。

### CellMeasurer
- セルのサイズ計測を行う機能。
- ViewBindingを使うか、固定値を使うかのバリエーション。後者はPreview用。

### ComposeCompatTextView
- Viewフレームワークで発生するテキスト計測のズレを軽減するためのカスタムビュー。
- 内部でComposeの`Paragraph`/`ParagraphIntrinsics`を使うので、AnnotatedString以外ならまあズレない。
- XML属性を用意しているが、サンプルなので網羅性は低い。
- XMLレイアウトエディタのプレビュー用にfallbackComposeInfoを内部で用意している。

### HorizontalScrollable
- LazyColumn中の横スクロール可能な領域1つと、そのスクロール状態を保持するデータクラス。
- 慣性スクロール対応。

### HorizontalScrollArea
- 横スクロール表示を行うコンテナComposable。
- Modifier.horizontalScroll()と異なり、モーションイベントを一切奪わない。

### LazyTable
- 巨大な表のComposable。
- コレ自体にスクロール機能は一切ないが、表内の可視範囲をStateとして渡すことで可視範囲のセルだけをComposeする。
- 可視範囲の左端と上端に見出し列/行を張り付けるsticky表示に対応する。

### LazyTableSizes
- LazyTableにわたす表の幅/高さの情報。

### TableCell
- 表のセルのComposable関数。

### TableData
- このサンプルの表のデータモデル。

### app/src/main/res/layout/measure_cell.xml
- セルのレイアウトXML。サイズ計測のために使われる。内容はTableCellを単純に置き換えたもの。
