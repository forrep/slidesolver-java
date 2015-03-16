package jp.ne.raccoon.slidesolver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jp.ne.raccoon.slidesolver.Solver.Direction;
import jp.ne.raccoon.slidesolver.Solver.FieldOperation;
import jp.ne.raccoon.slidesolver.Solver.SolveWorker;

public class JavaSpecifiedNumberSolver {
	private static Set<Integer> target = new HashSet<>();
	static {
		target.add(127); target.add(141); target.add(181); target.add(186); target.add(244); target.add(330); target.add(344); target.add(381);
		target.add(406); target.add(668); target.add(690); target.add(958); target.add(1052); target.add(1075); target.add(1179); target.add(1358);
		target.add(1381); target.add(1389); target.add(1421); target.add(1437); target.add(1695); target.add(1749); target.add(1872); target.add(1874);
		target.add(1932); target.add(1934); target.add(2215); target.add(2226); target.add(2415); target.add(2570); target.add(2582); target.add(2589);
		target.add(2765); target.add(2790); target.add(2901); target.add(3031); target.add(3089); target.add(3117); target.add(3163); target.add(3318);
		target.add(3370); target.add(3549); target.add(3551); target.add(3648); target.add(3649); target.add(3825); target.add(3943); target.add(4112);
		target.add(4373); target.add(4564); target.add(4790); target.add(4812); target.add(4813); target.add(4895); target.add(4953);
	}

	public static void main(String[] args) throws Exception {
		Reader reader = new InputStreamReader(Solver.class.getResourceAsStream("problems.txt"));
		BufferedReader bufferedReader = new BufferedReader(reader);
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("solved.txt")));
		// 移動数制限
		String[] limitsString = bufferedReader.readLine().split(" ");
		Solver.MoveLimit moveLimit = new Solver.MoveLimit(Integer.parseInt(limitsString[0]), Integer.parseInt(limitsString[1]), Integer.parseInt(limitsString[2]), Integer.parseInt(limitsString[3]));
		Integer.parseInt(bufferedReader.readLine());
		// 行数
		int count = target.size();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		@SuppressWarnings("unchecked")
		Future<SolveWorker>[] futures = (Future<SolveWorker>[]) new Future[count];
		int[] lineNumberMap = new int[count];
		int lineNumber = 0;
		int futureNumber = 0;
		int solved = 0;
		long start = System.currentTimeMillis();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			++lineNumber;
			if (!target.contains(lineNumber)) {
				continue;
			}
			String[] params = line.split(",");
			int width = Integer.parseInt(params[0]);
			int height = Integer.parseInt(params[1]);
			String fieldString = params[2];

			lineNumberMap[futureNumber] = lineNumber;
			futures[futureNumber++] = executor.submit(new SolveWorker(width, height, fieldString, false));
		}
		lineNumber = 0;
		List<Integer> unsolved = new ArrayList<>();
		for (Future<SolveWorker> future : futures) {
			++lineNumber;
			if (future == null) { continue; }
			SolveWorker worker = future.get();
			
			System.out.println("#" + lineNumberMap[lineNumber - 1]);
			System.out.println("Problem: " + worker.getWidth() + "," + worker.getHeight() + "," + worker.getFieldString());
			System.out.flush();
			
			FieldOperation answerField = worker.getField();
			System.out.println("Operation  : " + answerField.getOperations());
			System.out.println("Count      : " + answerField.getOperationCount());
			System.out.println("Distance   : " + answerField.getManhattanDistance());
			if (answerField.isSolved()) {
				++solved;
				moveLimit.add(answerField.getOperations());
				writer.write(answerField.getOperations());
			}
			else {
				unsolved.add(lineNumberMap[lineNumber - 1]);
			}
			writer.write("\n");
			
			long elapsed = System.currentTimeMillis() - start;
			long remain = Math.max(elapsed * count / lineNumber - elapsed, 0);
			System.out.println("Elapsed    : " + (elapsed / 1000) + " sec");
			System.out.println("Remain     : " + (remain / 1000) + " sec");
			System.out.println("Total      : " + ((elapsed + remain) / 1000) + " sec");
			System.out.println();
			System.out.flush();
		}
		System.out.println("Number   : " + count);
		System.out.println("Solved   : " + solved);
		System.out.println("Elapsed  : " + ((System.currentTimeMillis() - start) / 1000) + " sec");
		System.out.println("Limits(L): " + moveLimit.moves[Direction.LEFT.code]  + "/" + moveLimit.limits[Direction.LEFT.code]);
		System.out.println("Limits(R): " + moveLimit.moves[Direction.RIGHT.code] + "/" + moveLimit.limits[Direction.RIGHT.code]);
		System.out.println("Limits(U): " + moveLimit.moves[Direction.UP.code]    + "/" + moveLimit.limits[Direction.UP.code]);
		System.out.println("Limits(D): " + moveLimit.moves[Direction.DOWN.code]  + "/" + moveLimit.limits[Direction.DOWN.code]);
		
		System.out.println();
		System.out.print("Unsolved: ");
		for (int i = 0; i < unsolved.size(); ++i) {
			if (i > 0) {
				System.out.print(",");
			}
			System.out.print(unsolved.get(i));
		}
		System.out.println();
		executor.shutdownNow();
		writer.close();
		reader.close();
	}
}
