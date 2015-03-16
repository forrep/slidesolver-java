package jp.ne.raccoon.slidesolver;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import jp.ne.raccoon.slidesolver.Solver.FieldOperation;

public class SolverCommand {
	private static final Pattern numberPattern = Pattern.compile("^[0-9]+$");
	private static final Pattern fieldPattern = Pattern.compile("^[0-9a-zA-Z]+$");
	private static final int operationSplitter = 5;
	
	public static void main(String[] args) throws Exception {
		if (args.length < 3 || !numberPattern.matcher(args[0]).matches() || !numberPattern.matcher(args[1]).matches() || !fieldPattern.matcher(args[2]).matches()) {
			System.out.println("usage: SolverCommand <width> <height> <field>");
			System.out.println("       SolverCommand <width> <height> <cell-1> <cell-2>...");
			return;
		}
		int width = Integer.parseInt(args[0]);
		int height = Integer.parseInt(args[1]);
		String fieldString = args[2].toUpperCase();
		if (args.length > 3 && args.length == width * height + 2) {
			StringBuilder newField = new StringBuilder();
			for (int i = 2; i < args.length; ++i) {
				if (!numberPattern.matcher(args[i]).matches()) {
					System.out.println("usage: SolverCommand <width> <height> <field>");
					System.out.println("       SolverCommand <width> <height> <cell-1> <cell-2>...");
					return;
				}
				int parsed = Integer.parseInt(args[i]);
				if (parsed >= 10) {
					newField.append((char) ('A' + parsed - 10));
					
				}
				else {
					newField.append((char) ('0' + parsed));
				}
			}
			fieldString = newField.toString();
		}
		if (width * height != fieldString.length()) {
			System.out.println("invalid field size");
			return;
		}
		if (!checkField(fieldString)) {
			System.out.println("invalid field");
			return;
		}
		
		System.out.println("Input     : " + width + "," + height + "," + fieldString);
		System.out.flush();
		
		FieldOperation answerField = Solver.solve(width, height, fieldString, false);
		System.out.println("Moves     : " + answerField.getOperationCount());
		
		String operations = answerField.getOperations();
		System.out.println("-- Operation --");
		for (int i = 0; i < operations.length(); ++i) {
			if (i % operationSplitter == 0) {
				System.out.print(i / operationSplitter + 1);
				System.out.print(":");
				for (int j = i / operationSplitter; j >= 0; --j) {
					System.out.print(" ");
				}
			}
			System.out.print(operations.charAt(i));
			System.out.print(" ");
			if (i % operationSplitter == operationSplitter - 1) {
				System.out.println();
				System.out.println();
			}
		}
		System.out.println();
		System.out.flush();
	}
	
	private static boolean checkField(String field) {
		Set<Integer> map = new HashSet<Integer>();
		for (int i = 0; i < field.length(); ++i) {
			int cell = field.charAt(i);
			if ('A' <= cell && cell <= 'Z') {
				cell = cell - 'A' + 10;
			}
			else if ('0' <= cell && cell <= '9') {
				cell = cell - '0';
			}
			if (cell >= field.length()) {
				return false;
			}
			if (map.contains(cell)) {
				return false;
			}
			map.add(cell);
		}
		
		return true;
	}
}
