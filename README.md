スライドパズル 解法探索アルゴリズム
====

**Google Developer Day 2011**で参加資格を得るために行われた、DevQuizのスライドパズルの解法を探索するプログラムです。
Corei5 3.4GHzのPCで2スレッド稼働させた場合、全5,000問中、正答数4,945問（正答率98.9％）を3分ほどで計算完了します。

## 動かし方
Eclipseにプロジェクトとして取り込み``jp.ne.raccoon.slidesolver.JavaSolver``を実行してください。
標準出力に探索された解法が表示され、ルートディレクトリに``solved.txt``が生成されます。

## 任意のフィールドの解法を探索する
``jp.ne.raccoon.slidesolver.SolverCommand <width> <height> <cell-1> <cell-2>...``を実行してください。
width, heightのそれぞれ最大値は6です。cellは左上から右へ、次に2行目の左上から右へ、、、という順番に入力します。
セルの値は0が空白でそれ以外のセルは1から順に数値で入力してください。
