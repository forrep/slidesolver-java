package jp.ne.raccoon.slidesolver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jp.ne.raccoon.slidesolver.SolverArrayed.Direction;
import jp.ne.raccoon.slidesolver.SolverArrayed.FieldOperation;
import jp.ne.raccoon.slidesolver.SolverArrayed.MoveLimit;
import jp.ne.raccoon.slidesolver.SolverArrayed.SolveWorker;

public class JavaSolverArrayed {
	public static void main(String[] args) throws Exception {
		Reader reader = new InputStreamReader(JavaSolverArrayed.class.getResourceAsStream("problems.txt"));
		BufferedReader bufferedReader = new BufferedReader(reader);
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("solved.txt")));
		// 移動数制限
		String[] limitsString = bufferedReader.readLine().split(" ");
		MoveLimit moveLimit = new MoveLimit(Integer.parseInt(limitsString[0]), Integer.parseInt(limitsString[1]), Integer.parseInt(limitsString[2]), Integer.parseInt(limitsString[3]));
		// 行数
		int count = Integer.parseInt(bufferedReader.readLine());
		
		ExecutorService executor = Executors.newFixedThreadPool(2);
		@SuppressWarnings("unchecked")
		Future<SolveWorker>[] futures = (Future<SolveWorker>[]) new Future[count];
		int lineNumber = 0;
		int solved = 0;
		long start = System.currentTimeMillis();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			String[] params = line.split(",");
			int width = Integer.parseInt(params[0]);
			int height = Integer.parseInt(params[1]);
			String fieldString = params[2];

			futures[lineNumber] = executor.submit(new SolveWorker(width, height, fieldString, false));
			++lineNumber;
		}
		lineNumber = 0;
		List<Integer> unsolved = new ArrayList<>();
		for (Future<SolveWorker> future : futures) {
			++lineNumber;
			if (future == null) { continue; }
			SolveWorker worker = future.get();
			
			System.out.println("#" + lineNumber);
			System.out.println("Problem    : " + worker.getWidth() + "," + worker.getHeight() + "," + worker.getFieldString());
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
				unsolved.add(lineNumber);
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
