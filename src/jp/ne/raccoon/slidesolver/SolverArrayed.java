package jp.ne.raccoon.slidesolver;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Callable;

import jp.ne.raccoon.slidesolver.SolverArrayed.FieldCondition.ScoringType;
import jp.ne.raccoon.slidesolver.SolverArrayed.FieldOperation.FieldComparator;

public class SolverArrayed {
	static {
		try {
			System.loadLibrary("slidesolver");
		}
		catch (UnsatisfiedLinkError e) {
		}
	}
	private static int HISTORY_SIZE = 400000;
	private static boolean FIELD_OPERATION_POOL_ENABLED = true;
	
	public static class SolverConfiguration {
		public final Comparator<FieldOperation> comparator;
		public final int moveLimit;
		public final int candidateSize;
		public final ScoringType scoringType;
		public final int decreaseFactor;
		/**
		 * @param comparator Fieldの優劣を決定する比較関数
		 * @param moveLimit 双方向ともに移動回数の上限
		 * @param candidateSize 1回の移動あたり生き残る候補Fieldの数
		 * @param scoringType Fieldのスコアリングタイプ
		 * @param decreaseFactor 移動毎に生き残る候補Field数を減らすパーセンテージ(0-100)
		 */
		public SolverConfiguration(Comparator<FieldOperation> comparator, int moveLimit, int candidateSize, ScoringType scoringType, int decreaseFactor) {
			this.comparator = comparator;
			this.moveLimit = moveLimit;
			this.candidateSize = candidateSize;
			this.scoringType = scoringType;
			this.decreaseFactor = decreaseFactor;
		}
	}
	private static SolverConfiguration[] configurations = {
		new SolverConfiguration(FieldComparator.comparatorDefault, 140, 1700, ScoringType.DEFAULT, 40),
		new SolverConfiguration(FieldComparator.comparatorBack27, 140, 1400, ScoringType.DEFAULT, 0),
		new SolverConfiguration(FieldComparator.comparatorDefault, 180, 1600, ScoringType.POW, 0),
		
		// テスト追加
//		new SolverConfiguration(FieldComparator.comparatorDefault, 100, 5000, ScoringType.DEFAULT, 10),
//		new SolverConfiguration(FieldComparator.comparatorDefault, 240, 2400, ScoringType.DEFAULT, 0),
//		new SolverConfiguration(FieldComparator.comparatorDefault, 240, 2400, ScoringType.TOP, 0),
//		new SolverConfiguration(FieldComparator.comparatorDefault, 240, 2400, ScoringType.LEFT, 0),
	};
	private static ThreadLocal<MemoryPool<FieldOperation>> fieldOperationMemoryPool0 = new ThreadLocal<MemoryPool<FieldOperation>>() {
		@Override
		protected MemoryPool<FieldOperation> initialValue() {
			return new MemoryPool<FieldOperation>(FieldOperation.class);
		}
	};
	private static ThreadLocal<MemoryPool<FieldOperation>> fieldOperationMemoryPool1 = new ThreadLocal<MemoryPool<FieldOperation>>() {
		@Override
		protected MemoryPool<FieldOperation> initialValue() {
			return new MemoryPool<FieldOperation>(FieldOperation.class);
		}
	};
	
	public static class SolveWorker implements Callable<SolveWorker> {
		private int width;
		private int height;
		private String fieldString;
		private boolean isNative;
		private FieldOperation field;
		private SolverConfiguration configuration;
		public SolveWorker(int width, int height, String fieldString, boolean isNative) {
			this.width = width;
			this.height = height;
			this.fieldString = fieldString;
			this.isNative = isNative;
		}
		@Override
		public SolveWorker call() {
			if (configuration != null) {
				this.field = solve(width, height, fieldString, configuration);
			}
			else {
				this.field = solve(width, height, fieldString, isNative);
			}
			return this;
		}
		public void setConfiguration(SolverConfiguration configuration) {
			this.configuration = configuration;
		}
		public int getWidth() {
			return width;
		}
		public int getHeight() {
			return height;
		}
		public String getFieldString() {
			return fieldString;
		}
		public FieldOperation getField() {
			return field;
		}
	}

	public static FieldOperation solve(int width, int height, String fieldString, boolean isNative) {
		FieldOperation field = null;
		if (isNative) {
			FieldCondition condition = new FieldCondition(width, height, fieldString, ScoringType.DEFAULT);
			field = new FieldOperation(condition);
			String operations = solveNative(width, height, fieldString);
			if (operations.length() > 0) {
				field.move(operations);
			}
		}
		else {
			FieldOperation solved;
			FieldHistory fieldHistoryAsc = new FieldHistory(HISTORY_SIZE);
			FieldHistory fieldHistoryDsc = new FieldHistory(HISTORY_SIZE);
			for (int i = 0; i < configurations.length; ++i) {
				// スレッドセーフとするために古典的な取得方法
				SolverConfiguration configuration = configurations[i];
				FieldCondition condition = new FieldCondition(width, height, fieldString, configuration.scoringType);
				field = new FieldOperation(condition);
				if ((solved = solve(configuration, field, fieldHistoryAsc, fieldHistoryDsc)) != null && solved.isSolved()) {
					return solved;
				}
			}
		}
		return field;
	}
	public static FieldOperation solve(int width, int height, String fieldString, SolverConfiguration configuration) {
		FieldCondition condition = new FieldCondition(width, height, fieldString, ScoringType.DEFAULT);
		FieldOperation field = new FieldOperation(condition);
		FieldOperation solved;
		FieldHistory fieldHistoryAsc = new FieldHistory(HISTORY_SIZE);
		FieldHistory fieldHistoryDsc = new FieldHistory(HISTORY_SIZE);
		if ((solved = solve(configuration, field, fieldHistoryAsc, fieldHistoryDsc)) != null && solved.isSolved()) {
			return solved;
		}
		return field;
	}
	private static FieldOperation solve(SolverConfiguration configuration, FieldOperation initialField, FieldHistory fieldHistoryAsc, FieldHistory fieldHistoryDsc) {
		FieldOperation[] setAsc = new FieldOperation[]{initialField};
		FieldOperation[] setDsc = new FieldOperation[]{initialField.toReverse()};
		int[] foundAnswerAsc = null;
		int[] foundAnswerDsc = null;
		int[] fieldTemp = Field.newField();
		MemoryPool<FieldOperation> pool = null;
		MemoryPool<FieldOperation> pool0 = fieldOperationMemoryPool0.get();
		MemoryPool<FieldOperation> pool1 = fieldOperationMemoryPool1.get();
		for (int i = 0; i < configuration.moveLimit; ++i) {
			pool = (i & 1) == 0 ? pool0 :pool1;
			pool.trash();
			// 順方向探索、探索するプールをdecreaseFactorに従い moveLimitから徐々に減らす
			int candidate = configuration.candidateSize * (configuration.moveLimit - i * configuration.decreaseFactor / 100) / configuration.moveLimit;
			FieldSet nextSetAsc = new FieldSet(candidate, configuration.comparator);
			outer : for (FieldOperation field: setAsc) {
				for (Direction direction : Direction.values()) {
					// 直前の移動を取り消す移動は行わない (例)右移動の後に左移動など
					// 壁やフィールドにぶつからないか確認
					if (direction.reverse() != field.lastOperation && field.canMove(direction)) {
						FieldOperation newField;
						if (FIELD_OPERATION_POOL_ENABLED) {
							newField = pool.get();
							newField.initCopy(field);
						}
						else {
							newField = field.copy();
						}
						newField.move(direction, false);
						Field.init(fieldTemp, 0, newField);
						// 盤面のループ判定、過去に登場した盤面の場合はそれ以降の解析を行わない
						if (!fieldHistoryAsc.add(fieldTemp, 0)) {
							// 他方向の移動も近似盤面となるので解析を中止する
							break;
						}
						nextSetAsc.add(newField);
						if (newField.isSolved()) {
							foundAnswerAsc = fieldTemp;
							break outer;
						}
						// 逆方向探索の盤面に存在したらルート発見、論理削除された盤面も検索対象に含める
						if (fieldHistoryDsc.contains(fieldTemp, 0, false)) {
							foundAnswerAsc = fieldTemp;
							foundAnswerDsc = fieldHistoryDsc.getArray(fieldTemp, 0, false);
							break outer;
						}
					}
				}
			}
			setAsc = nextSetAsc.toArray();
			if (foundAnswerAsc != null) {
				break;
			}
			
			// 逆方向探索
			FieldSet nextSetDsc = new FieldSet(candidate, configuration.comparator);
			outer : for (FieldOperation field: setDsc) {
				for (Direction direction : Direction.values()) {
					// 直前の移動を取り消す移動は行わない (例)右移動の後に左移動など
					// 壁やフィールドにぶつからないか確認
					if (direction.reverse() != field.lastOperation && field.canMove(direction)) {
						FieldOperation newField;
						if (FIELD_OPERATION_POOL_ENABLED) {
							newField = pool.get();
							newField.initCopy(field);
						}
						else {
							newField = field.copy();
						}
						newField.move(direction, false);
						Field.init(fieldTemp, 0, newField);
						// 盤面のループ判定、過去に登場した盤面の場合はそれ以降の解析を行わない
						if (!fieldHistoryDsc.add(fieldTemp, 0)) {
							// 他方向の移動も近似盤面となるので解析を中止する
							break;
						}
						nextSetDsc.add(newField);
						if (newField.isSolved()) {
							foundAnswerDsc = fieldTemp;
							break outer;
						}
						// 順方向探索の盤面に存在したらルート発見、論理削除された盤面も検索対象に含める
						if (fieldHistoryAsc.contains(fieldTemp, 0, false)) {
							foundAnswerDsc = fieldTemp;
							foundAnswerAsc = fieldHistoryAsc.getArray(fieldTemp, 0, false);
							break outer;
						}
					}
				}
			}
			setDsc = nextSetDsc.toArray();
			if (foundAnswerDsc != null) {
				break;
			}
		}
		// FieldHistoryのclear()は論理削除で次のsolve()呼び出しでも共有される。
		// 盤面のループ判定では削除された盤面を参照しないが、逆方向探索とのマッチングでは削除された盤面からも検索する
		fieldHistoryAsc.clear();
		fieldHistoryDsc.clear();
		
		if (foundAnswerAsc != null || foundAnswerDsc != null) {
			FieldOperation answerOperation = initialField.copy();
			if (foundAnswerAsc != null) {
				answerOperation.move(Field.getOperations(foundAnswerAsc, 0, false));
			}
			if (foundAnswerDsc != null) {
				answerOperation.move(Field.getOperations(foundAnswerDsc, 0, true));
			}
			return answerOperation;
		}
		return null;
	}
	
	public static native String solveNative(int width, int height, String fieldString);

	/**
	 * 盤面の横幅・縦幅・壁など設定を保持
	 */
	public static class FieldCondition {
		public static enum ScoringType {
			DEFAULT, POW, LEFT, TOP
		}
		public static final int INT_SIZE = 32;
		public static final int FIELD_SIZE_MAX = 36;
		
		// 盤面の横幅
		public final int width;
		// 盤面の縦幅
		public final int height;
		// 盤面のセルの数
		public final int size;
		// セルあたりの必要bit数
		public final int bitPerCell;
		// 1セル分を取り出すためのMask bitPerCell分だけbitが立つ、壁を表すbit配列を兼用
		public final int cellMask;
		// int変数1つに入るセルの数
		public final int cellsPerInt;
		// 壁の位置、true:壁、false:通常セル
		public final boolean[] wall = new boolean[FIELD_SIZE_MAX];
		// セルの値から開始局面のpositionを取得するための配列。逆方向探索で開始局面とのマンハッタン距離を取得するのに利用
		// key=セルの値、value=position
		public final int[] startField;
		// セルの値からゴール局面のpositionを取得するための配列。順方向探索でゴール局面とのマンハッタン距離を取得するのに利用
		// key=セルの値、value=position
		public final int[] finalField;
		// FieldOperationに割り振るinstanceIdのシーケンス
		public int instanceId = 0;
		// 各ポジションから上下左右、各方向に移動可能かどうかを保持
		public final int[] canMoveMap = new int[FIELD_SIZE_MAX];
		// Field/FieldOperation.fieldのスロット配置、セル毎にどのslotに保存されているか保持
		public final int[] fieldSlotMap = new int[FIELD_SIZE_MAX];
		// Field/FieldOperation.fieldのスロット内配置
		public final int[] fieldInSlotPositionMap = new int[FIELD_SIZE_MAX];
		// positionからx座標、y座標に変換する 4x4の局面で position=7の場合、positionXMap[7]=3、positionYMap[7]=1
		public final int[] positionXMap = new int[FIELD_SIZE_MAX];
		public final int[] positionYMap = new int[FIELD_SIZE_MAX];
		// マンハッタン距離のスコアリング方式
		public final ScoringType scoringType;
	
		public FieldCondition(int width, int height, String fieldString, ScoringType scoringType) {
			this.scoringType = scoringType;
			this.width = width;
			this.height = height;
			size = width * height;
			// 1セルあたりに必要なbit数を計算、size種類（0～size-1）の数値/記号と壁で+1 が必要, size-1+1 が必要とするbit数を算出
			bitPerCell = Integer.numberOfTrailingZeros(Integer.highestOneBit(size)) + 1;
			// 1セル分を取り出すためのMask bitPerCell分だけbitが立つ
			cellMask = (1 << bitPerCell) - 1;
			cellsPerInt = (int) (INT_SIZE / bitPerCell);
			startField = new int[size];
			finalField = new int[size];
			for (int i = 0; i < fieldString.length(); ++i) {
				char cellString = fieldString.charAt(i);
				if (cellString == '=') {
					// 壁の場合
					wall[i] = true;
					startField[i + 1] = i;
				}
				else if ('0' <= cellString && cellString <= '9') {
					// 0-9 の場合
					startField[cellString - '0'] = i;
				}
				else if ('A' <= cellString && cellString <= 'Z') {
					// A-Z の場合、A=10,B=11,,,Z=35
					startField[cellString - 'A' + 10] = i;
				}
				else {
					throw new RuntimeException();
				}
			}
			finalField[0] = size - 1;
			for (int i = 1; i < finalField.length; ++i) {
				finalField[i] = i - 1;
			}

			for (int i = 0; i < this.size; ++i) {
				this.fieldSlotMap[i] = i / this.cellsPerInt;
				this.fieldInSlotPositionMap[i] = (i % this.cellsPerInt) * this.bitPerCell;
			}

			// 各ポジションから移動可能な位置を表すcanMoveMapを構築
			for (int i = 0; i < this.size; ++i) {
				this.canMoveMap[i] = 0;
				for (Direction direction : Direction.values()) {
					int targetPosition = 0;
					switch (direction) {
					case LEFT:
						if (i % this.width <= 0) {
							continue;
						}
						targetPosition = i - 1;
						break;
					case RIGHT:
						if (i % this.width >= this.width - 1) {
							continue;
						}
						targetPosition = i + 1;
						break;
					case UP:
						if (i / this.width <= 0) {
							continue;
						}
						targetPosition = i - this.width;
						break;
					case DOWN:
						if (i / this.width >= this.height - 1) {
							continue;
						}
						targetPosition = i + this.width;
						break;
					}

					// 移動先が壁でないことを確認
					if (!this.wall[targetPosition]) {
						this.canMoveMap[i] |= 1 << direction.code;
					}
				}
			}
			// positionからx,yに変換を行うpositionXMap/positionYMapの構築
			for (int i = 0; i < this.size; ++i) {
				this.positionXMap[i] = i % this.width;
				this.positionYMap[i] = i / this.width;
			}
		}
	}
	
	/**
	 * 盤面の履歴保存用にFieldOperationから必要なデータのみを抽出
	 */
	public static class Field {
		// 盤面
		public static final int FIELD_OFFSET = 0;
		public static final int FIELD_SIZE = 8;
		// equalsの呼び出し回数が多いのでhashCodeを事前に計算して保持しておく。盤面(field)のみに依存。
		public static final int HASH_CODE_OFFSET = 8;
		// 移動の回数
		public static final int OPERATION_COUNT_OFFSET = 9;
		// 移動の記録(アクティブ)、operationsと会わせることで完全な移動の履歴になる
		public static final int CURRENT_OPERATION_OFFSET = 10;
		// 移動の記録(非アクティブ)、immutable。子孫およびFieldOperationと同一オブジェクトを共有する
		public static final int OPERATIONS_OFFSET = 11;
		public static final int OPERATIONS_SIZE = 12;
		
		public static final int FIELD_ARRAY_SIZE = FIELD_SIZE + OPERATIONS_SIZE + 3;
		
		public static int[] newField() {
			return new int[FIELD_ARRAY_SIZE];
		}
		public static void init(int[] array, int start, FieldOperation fieldOperation) {
			for (int i = 0; i < FIELD_ARRAY_SIZE; ++i) {
				array[start + i] = 0;
			}
			for (int i = 0; i < fieldOperation.field.length; ++i) {
				array[start + FIELD_OFFSET + i] = fieldOperation.field[i];
			}
			for (int i = 0; i < fieldOperation.operations.length; ++i) {
				// byte -> int の変換
				array[start + OPERATIONS_OFFSET + i / 4] |= ( (((int) fieldOperation.operations[i]) & 0xff ) << (i % 4) * 8);
			}
			array[start + CURRENT_OPERATION_OFFSET] = fieldOperation.currentOperation;
			array[start + OPERATION_COUNT_OFFSET] = fieldOperation.operationCount;
			int hashCode = 0;
			for (int fieldEntry : fieldOperation.field) {
				hashCode ^= fieldEntry;
				hashCode *= 13;
			}
			array[start + HASH_CODE_OFFSET] = hashCode;
		}
		public static int hashCode(int[] array, int start) {
			return array[start + HASH_CODE_OFFSET];
		}
		public static boolean equals(int[] array, int start, int[] anotherArray, int anotherStart) {
			if (array[start + HASH_CODE_OFFSET] != anotherArray[anotherStart + HASH_CODE_OFFSET]) {
				return false;
			}
			for (int i = 0; i < FIELD_SIZE; ++i) {
				if (array[start + FIELD_OFFSET + i] != anotherArray[anotherStart + FIELD_OFFSET + i]) {
					return false;
				}
			}
			return true;
		}
		public static String getOperations(int[] array, int start, boolean reverseMode) {
			short operationCount = (short) array[start + OPERATION_COUNT_OFFSET];
			byte[] operations = new byte[operationCount / FieldOperation.operationsPerVariable];
			for (int i = 0; i < operations.length; ++i) {
				// int -> byte変換
				operations[i] = (byte) ((array[start + OPERATIONS_OFFSET + i / 4] >>> (i % 4) * 8) & 0xff);
			}
			return FieldOperation.getOperations(operations, (byte) array[start + CURRENT_OPERATION_OFFSET], operationCount, reverseMode);
		}
	}
	
	/**
	 * 盤面の保持と移動を行う
	 */
	public static class FieldOperation {
		// 1回の移動の記録に必要なbit数
		private static final int bitsPerOperation = 2;
		// 1個の変数フィールドに入るOperation数、byte型=8bitなので、4個入る
		private static final int operationsPerVariable = 4;
		// 1回の移動を切り出すためのマスク(2bit)
		private static final int operationMask = 3;
		
		private int instanceId;
		private FieldCondition condition;
		// 0の位置を表す、位置 = X軸 + Y軸 * width
		private int position;
		// 盤面
		private int[] field;
		
		// 移動の回数
		private short operationCount;
		// 移動の記録(アクティブ)、operationsと会わせることで完全な移動の履歴になる
		private byte currentOperation;
		// 移動の記録(非アクティブ)、immutable。子孫およびFieldクラスと同一オブジェクトを共有する
		private byte[] operations;
		// 前回の移動方向、上に移動した後に下など、前回の動作を取り消す動きを高速にチェックするために保持
		private Direction lastOperation = null;
		
		// 計算済みのマンハッタン距離
		private int manhattanDistance;
		// false:順方向探索、true:ゴール盤面から開始盤面へ逆方向探索
		private boolean reverseMode;
		
		public FieldOperation() {
		}
		public FieldOperation(FieldCondition condition) {
			init(condition);
		}
		public void init(FieldCondition condition) {
			this.instanceId = condition.instanceId++;
			this.condition = condition;
			field = new int[condition.size / condition.cellsPerInt + (condition.size % condition.cellsPerInt > 0 ? 1 : 0)];
			for (int i = 0; i < condition.size; ++i) {
				if ((condition.wall[condition.startField[i]])) {
					// 壁の場合
					setCell(condition.startField[i], condition.cellMask);
				}
				else if (i == 0) {
					// 0 の場合、位置の記録とpositionの初期化
					setCell(condition.startField[i], 0);
					position = condition.startField[i];
				}
				else {
					setCell(condition.startField[i], i);
				}
			}
			operationCount = 0;
			currentOperation = 0;
			operations = new byte[0];
			reverseMode = false;
			updateManhattanDistance();
		}
		
		public FieldOperation initCopy(FieldOperation from) {
			this.instanceId = from.condition.instanceId++;
			this.condition = from.condition;
			this.field = from.field.clone();
			this.position = from.position;
			this.operationCount = from.operationCount;
			this.currentOperation = from.currentOperation;
			// operationsはimmutableなので同一オブジェクトを共有する
			this.operations = from.operations;
			this.lastOperation = from.lastOperation;
			this.manhattanDistance = from.manhattanDistance;
			this.reverseMode = from.reverseMode;
			return this;
		}
		private int getCell(int i) {
			return (field[condition.fieldSlotMap[i]] >>> condition.fieldInSlotPositionMap[i]) & condition.cellMask;
		}
		private void setCell(int i, int cell) {
			int intPos = condition.fieldInSlotPositionMap[i];;
			int slot = condition.fieldSlotMap[i];
			// 指定場所のbitを置き換え
			field[slot] = (field[slot] ^ (field[slot] & (condition.cellMask << intPos))) | (cell << intPos);
		}
		
		private boolean canMove(Direction direction) {
			return (condition.canMoveMap[position] & 1 << direction.code) != 0 ? true : false;
		}
		
		public void move(String operations) {
			for (int i = 0; i < operations.length(); ++i) {
				move(Direction.valueOf(operations.charAt(i)), true);
			}
		}
		public void move(Direction direction, boolean check) {
			if (check && canMove(direction) == false) {
				throw new RuntimeException();
			}
			int targetPosition;
			switch (direction) {
			case LEFT:
				targetPosition = position - 1;
				break;
			case RIGHT:
				targetPosition = position + 1;
				break;
			case UP:
				targetPosition = position - condition.width;
				break;
			case DOWN:
				targetPosition = position + condition.width;
				break;
			default:
				throw new RuntimeException();
			}
			
			// 入れ替え処理
			int targetCell = getCell(targetPosition);
			manhattanDistance -= getManhattanDistance(targetCell, targetPosition);
			manhattanDistance += getManhattanDistance(targetCell, position);
			setCell(position, targetCell);
			setCell(targetPosition, 0);
			position = targetPosition;
			lastOperation = direction;
			
			// 移動の記録
			currentOperation |= direction.code << bitsPerOperation * (operationCount % operationsPerVariable);
			++operationCount;
			// operationsPerVariable毎にcurrentOperationをoperations配列に入れて初期化
			if (operationCount % operationsPerVariable == 0) {
				operations = Arrays.copyOf(operations, operationCount / operationsPerVariable);
				operations[operations.length - 1] = currentOperation;
				currentOperation = 0;
			}
		}
		
		public FieldOperation copy() {
			return new FieldOperation().initCopy(this);
		}
		
		public FieldOperation toReverse() {
			if (reverseMode) {
				throw new RuntimeException();
			}
			FieldOperation copied = copy();

			for (int i = 0; i < condition.size; ++i) {
				if ((condition.wall[i])) {
					// 壁の場合
					copied.setCell(i, condition.cellMask);
				}
				else if (i == condition.size - 1) {
					// 0 の場合、positionの初期化
					copied.setCell(i, 0);
					copied.position = i;
				}
				else {
					copied.setCell(i, i + 1);
				}
			}
			copied.operationCount = 0;
			copied.currentOperation = 0;
			copied.operations = new byte[0];
			copied.reverseMode = true;
			copied.updateManhattanDistance();
			
			return copied;
		}
		
		public String getOperations() {
			return getOperations(operations, currentOperation, operationCount, reverseMode);
		}
		public static String getOperations(byte[] operations, byte currentOperation, short operationCount, boolean reverseMode) {
			StringBuilder builder = new StringBuilder(operationCount);
			for (int oeration : operations) {
				for (int i = 0; i < operationsPerVariable; ++i) {
					if (reverseMode) {
						builder.insert(0, Direction.valueOf((oeration >>> i * bitsPerOperation) & operationMask).reverse().value);
					}
					else {
						builder.append(Direction.valueOf((oeration >>> i * bitsPerOperation) & operationMask).value);
					}
				}
			}
			for (int i = 0; i < operationCount % operationsPerVariable; ++i) {
				if (reverseMode) {
					builder.insert(0, Direction.valueOf((currentOperation >>> i * bitsPerOperation) & operationMask).reverse().value);
				}
				else {
					builder.append(Direction.valueOf((currentOperation >>> i * bitsPerOperation) & operationMask).value);
				}
			}
			return builder.toString();
		}
		
		private void updateManhattanDistance() {
			manhattanDistance = 0;
			for (int i = 0; i < condition.size; ++i) {
				manhattanDistance += getManhattanDistance(getCell(i), i);
			}
		}
		/**
		 * 該当数値が本来どこにあるべきかの距離を返す。
		 * 壁の場合は常に正しい位置にあるので距離0、移動の起点である0の場合も距離0とする。
		 * @param cell
		 * @param position
		 * @return 該当数値が本来どこにあるべきかの距離
		 */
		private int getManhattanDistance(int cell, int position) {
			if (cell == condition.cellMask || cell == 0) {
				return 0;
			}
			return getDistance(position, reverseMode == false ? condition.finalField[cell] : condition.startField[cell]);
		}
		private int getDistance(int start, int end) {
			int distance = Math.abs(condition.positionXMap[end] - condition.positionXMap[start]) + Math.abs(condition.positionYMap[end] - condition.positionYMap[start]);
			if (condition.scoringType == ScoringType.POW) {
				distance *= distance;
			}
			if (condition.scoringType == ScoringType.LEFT) {
				distance *= ((condition.width - condition.positionXMap[end] - 1) * condition.width
						+ (condition.height - condition.positionYMap[end])) * 30;
			}
			if (condition.scoringType == ScoringType.TOP) {
				distance *= (condition.size - end) * 30;
			}
			return distance;
		}
		public boolean isSolved() {
			return manhattanDistance == 0;
		}
		
		public int getOperationCount() {
			return operationCount;
		}
		public int getManhattanDistance() {
			return manhattanDistance;
		}

		// FieldSetで次の世代に残す優先順位を決定するComparatorクラス
		// 汎用的な作りで、設定により優先するFieldOperationが変化する。
		// JNIバージョンはそれぞれ個別に実装しているが速度的にさほどインパクトがなかったのでJava版は汎用版を利用
		public static class FieldComparator implements Comparator<FieldOperation> {
			public static final FieldComparator comparatorDefault = new FieldComparator(0);
			public static final FieldComparator comparatorBack27 = new FieldComparator(27);
			
			private final int reverseStartup;
			private final int reverseStartupLimit;
			
			/**
			 * @param reverseStartupLimit スコアを反転させる基準の移動回数
			 */
			public FieldComparator(int reverseStartupLimit) {
				this.reverseStartup = reverseStartupLimit > 0 ? -1 : 1;
				this.reverseStartupLimit = reverseStartupLimit;
			}
			@Override
			public int compare(FieldOperation o1, FieldOperation o2) {
				if (o1 == null && o2 == null) {
					return 0;
				}
				if (o1 == null) {
					return 1;
				}
				if (o2 == null) {
					return -1;
				}
				int reverseStartup = o1.getOperationCount() <= reverseStartupLimit ? this.reverseStartup : 1;
				if (o1.manhattanDistance != o2.manhattanDistance) {
					return (o1.manhattanDistance - o2.manhattanDistance) * reverseStartup;
				}
				if (o1.instanceId != o2.instanceId) {
					return (o1.instanceId > o2.instanceId ? -1 : 1) * reverseStartup;
				}
				return 0;
			}
		}
	}
	
	/**
	 * 最低限のオペレーションのみ実装した軽量TreeSetクラス
	 * 設定した容量＋αの内部バッファを持って、バッファが一杯になったタイミングで定期的にソートして設定した容量以降を破棄する
	 * 保持要素数が容量以上になっている場合、追加時に最後尾の要素と比較して不必要な様子を追加しない。
	 */
	public static class FieldSet {
		// 設定上でFieldSetに入る容量（実際には指定したサイズよりも多くの要素を一時的に保持する）
		private int capacity;
		// 内部バッファ分を含んだ容量
		private int internalCapacity;
		// 要素を保持する内部バッファ
		private FieldOperation[] list;
		// 現在保持している要素数、整理処理(organize())は論理削除なのでlist中の不要な要素は破棄されない
		private int size;
		// Setに追加可能な最低閾値となる要素、list[capacity-1]が該当する。初期はnullで条件がそろったら設定される
		private FieldOperation minThresould;
		// 内部バッファがソート済みになっているかどうかを表す
		private boolean organized;
		private Comparator<FieldOperation> comparator;
		
		public FieldSet(int capacity, Comparator<FieldOperation> comparator) {
			resize(capacity);
			this.comparator = comparator;
		}
		public void add(FieldSet field) {
			field.organize();
			for (int i = 0; i < field.size; ++i) {
				add(field.list[i]);
			}
		}
		public void add(FieldOperation field) {
			if (minThresould != null && comparator != null && comparator.compare(field, minThresould) > 0) {
				return;
			}
			if (size >= internalCapacity) {
				organize();
			}
			list[size++] = field;
			organized = false;
		}
		private void organize() {
			if (organized) {
				return;
			}
			if (comparator != null) {
				Arrays.sort(list, 0, size, comparator);
			}
			if (size >= capacity) {
				minThresould = list[capacity - 1];
				size = capacity;
			}
			organized = true;
		}
		public FieldOperation[] toArray() {
			organize();
			return Arrays.copyOf(list, size);
		}
		public FieldOperation first() {
			organize();
			return isEmpty() ? null : list[0];
		}
		public boolean isEmpty() {
			return size <= 0;
		}
		public void resize(int capacity) {
			this.capacity = capacity;
			this.internalCapacity = (int) ((float) capacity * 1.2f);
			if (this.internalCapacity <= this.capacity) { this.internalCapacity = this.capacity + 1; }
			this.size = 0;
			this.list = new FieldOperation[internalCapacity];
			this.minThresould = null;
			this.organized = true;
		}
	}
	
	/**
	 * 盤面の履歴(Fieldクラス)を保持する軽量Set
	 */
	public static class FieldHistory {
		private static final int FIELD_ENTRY_NEXT_OFFSET = Field.FIELD_ARRAY_SIZE + 0;
		private static final int FIELD_ENTRY_VERSION_OFFSET = Field.FIELD_ARRAY_SIZE + 1;
		private static final int FIELD_ENTRY_SIZE = Field.FIELD_ARRAY_SIZE + 2;
		
		private int[] table;
		public int[] list;
		private int index;
		private boolean isOverflow;
		private int version;
		public FieldHistory(int capacity) {
			table = new int[Integer.highestOneBit((int)((float) capacity * 1.5f)) << 1];
			list = new int[capacity * FIELD_ENTRY_SIZE + 1];
			index = 1;
			version = 0;
			isOverflow = false;
		}
		private int indexFor(int hashCode) {
			return (hashCode ^ (hashCode >> Integer.numberOfTrailingZeros(table.length))) & (table.length - 1);
		}
		public boolean add(int[] array, int field) {
			int entry = getEntry(array, field, false);
			if (entry > 0) {
				if (list[entry + FIELD_ENTRY_VERSION_OFFSET] == version) {
					// すでに存在したパターン
					return false;
				}
				else {
					// clear()で削除済みの領域に存在したパターン
					// tableから削除、listはゼロクリア
					remove(entry);
					for (int i = 0; i < FIELD_ENTRY_SIZE; ++i) {
						list[entry + i] = 0;
					}
				}
			}
			if (isOverflow) {
				// 対象領域が既にゼロクリアされている場合もあるが、副作用はないからOK（テーブルから見つからず削除なしでreturn）
				remove(index);
			}
			int bucket = indexFor(Field.hashCode(array, field));
			for (int i = 0; i < Field.FIELD_ARRAY_SIZE; ++i) {
				list[index + i] = array[field + i];
			}
			list[index + FIELD_ENTRY_NEXT_OFFSET] = table[bucket];
			list[index + FIELD_ENTRY_VERSION_OFFSET] = version;
			table[bucket] = index;
			
			// ARM向け最適化、剰余演算は行わない
			index += FIELD_ENTRY_SIZE;
			if (index >= list.length) {
				index = 1;
				isOverflow = true;
			}
			return true;
		}
		public int get(int[] array, int field, boolean current) {
			return getEntry(array, field, current);
		}
		public int[] getArray(int[] array, int field, boolean current) {
			int index = getEntry(array, field, current);
			int[] result = Field.newField();
			for (int i = 0; i < Field.FIELD_ARRAY_SIZE; ++i) {
				result[i] = list[index + i];
			}
			return result;
		}
		private int getEntry(int[] array, int field, boolean current) {
			for (int candidate = table[indexFor(Field.hashCode(array, field))]; candidate > 0; candidate = list[candidate + FIELD_ENTRY_NEXT_OFFSET]) {
				if (Field.equals(list, candidate, array, field)) {
					return !current || list[candidate + FIELD_ENTRY_VERSION_OFFSET] == version ? candidate : 0;
				}
			}
			return 0;
		}
		public boolean contains(int[] array, int field, boolean current) {
			return get(array, field, current) > 0;
		}
		/**
		 * すべてのデータを論理削除
		 * get() contains() の第2引数にfalseを指定すれば、clear()されたけどバッファに残っているデータを取得可能
		 */
		public void clear() {
			++version;
			if (version == 0) {
				// Integer.MAX_VALUEがオーバーフローした場合はすべてのバージョンを0にリセット
				int entry;
				int limit = isOverflow ? list.length : index;
				for (int i = 1; i < limit; i += FIELD_ENTRY_SIZE) {
					for (entry = table[indexFor(Field.hashCode(list, i))]; entry > 0; entry = list[entry + FIELD_ENTRY_NEXT_OFFSET]) {
						list[entry + FIELD_ENTRY_VERSION_OFFSET] = 0;
					}
				}
				++version;
			}
		}
		private void remove(int field) {
			int bucket = indexFor(Field.hashCode(list, field));
			int candidate = table[bucket];
			int prevCandidate = 0;
			while (candidate > 0) {
				if (candidate == field) {
					if (prevCandidate == 0) {
						table[bucket] = list[candidate + FIELD_ENTRY_NEXT_OFFSET];
					}
					else {
						list[prevCandidate + FIELD_ENTRY_NEXT_OFFSET] = list[candidate + FIELD_ENTRY_NEXT_OFFSET];
					}
					return;
				}
				prevCandidate = candidate;
				candidate = list[candidate + FIELD_ENTRY_NEXT_OFFSET];
			}
			return;
		}
	}
	
	/**
	 * 移動回数制限
	 */
	public static class MoveLimit {
		public final int[] limits = new int[Direction.values().length];
		public final int[] moves = new int[Direction.values().length];
		
		public MoveLimit(int limitLeft, int limitRight, int limitUp, int limitDown) {
			this.limits[Direction.LEFT.code] = limitLeft;
			this.limits[Direction.RIGHT.code] = limitRight;
			this.limits[Direction.UP.code] = limitUp;
			this.limits[Direction.DOWN.code] = limitDown;
		}
		
		public void add(String operations) {
			for (int i = 0; i < operations.length(); ++i) {
				moves[Direction.valueOf(operations.charAt(i)).code]++;
			}
		}
	}
	
	public static enum Direction {
		LEFT(0), RIGHT(1), UP(2), DOWN(3);
		private static Direction[] directionsValueOfInt = new Direction[] {LEFT, RIGHT, UP, DOWN};
		private static Direction[] directionsClockwise = new Direction[] {UP, DOWN, RIGHT, LEFT};
		private static Direction[] directionsCounterclockwise = new Direction[] {DOWN, UP, LEFT, RIGHT};
		
		public final int code;
		public final char value;
		
		private Direction(int code) {
			this.code = code;
			this.value = this.toString().charAt(0);
		}
		public static Direction valueOf(int code) {
			return directionsValueOfInt[code];
		}
		public static Direction valueOf(char value) {
			switch (value) {
			case 'L':
				return LEFT;
			case 'R':
				return RIGHT;
			case 'U':
				return UP;
			case 'D':
				return DOWN;
			default:
				throw new RuntimeException();
			}
		}
		public Direction reverse() {
			return valueOf(this.code ^ 1);
		}
		public Direction clockwise() {
			return directionsClockwise[this.code];
		}
		public Direction counterclockwise() {
			return directionsCounterclockwise[this.code];
		}
	}
	
	public static class MemoryPool<T> {
		private Object[] pool;
		private int index = 0;
		private int poolSize;
		private Class<T> initializer;
		
		public MemoryPool(Class<T> initializer) {
			// プールサイズの初期値、不足すれば自動拡張するので少なめの数を適当に設定
			this.poolSize = 1 << 4;
			pool = new Object[poolSize];
			try {
				for (int i = 0; i < poolSize; ++i) {
					pool[i] = initializer.newInstance();
				}
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException();
			}
			this.initializer = initializer;
		}
		public void trash() {
			index = 0;
		}
		@SuppressWarnings("unchecked")
		public T get() {
			if (index >= poolSize) {
				// プールサイズを超えてget()要求が来た場合
				// プールを構成する配列を2倍の容量で生成し直して、元の配列からオブジェクトをコピーしてから
				// 増やした領域のオブジェクトをまとめて生成する
				int newPoolSize = poolSize << 1;
				if (newPoolSize <= poolSize) {
					// intのオーバーフロー、対応しない
					throw new RuntimeException();
				}
				Object[] newPool = new Object[newPoolSize];
				System.arraycopy(pool, 0, newPool, 0, poolSize);
				poolSize = newPoolSize;
				pool = newPool;
				try {
					// 今回増加した分のオブジェクトをまとめて生成
					for (int i = index; i < poolSize; ++i) {
						pool[i] = initializer.newInstance();
					}
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException();
				}
			}
			return (T) pool[index++];
		}
	}
}
