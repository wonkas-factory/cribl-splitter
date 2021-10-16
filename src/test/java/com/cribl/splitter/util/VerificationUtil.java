package com.cribl.splitter.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * These are utility methods to verify the input logs to target logs. It has
 * been designed to handle one to many targets
 *
 */
public class VerificationUtil {
	// Note this only works/tested for Unix type systems. Windows new line is '\r\n'
	private final static int NEW_LINE_INDEX = (int) '\n';

	/**
	 * This efficiently verifies the exact content of the logs by streaming the
	 * files and storing each character count in a hash map where the key is the int
	 * of the char and value is the count. These counters are then decremented based
	 * on the target logs. The stream content are a exact match when all counters
	 * are 0 in the map at the end. Note max memory usage would be max size of int
	 * returned by the read method (65,535) by size of a long (64 bits) equaling
	 * roughly 525 Kb. This could theoretically process a max log with 65535 *
	 * 9223372036854775807 characters.
	 * 
	 * This implicitly verifies the new line counts as well which has key value of
	 * 10 stored in NEW_LINE_INDEX
	 * 
	 * @param inputLocation   the file location of the input used for the test
	 * @param targetLocations the location of one or more events.logs from target
	 * @return totalLines the total log lines of the input file
	 * @throws IOException
	 * @throws CriblException
	 */
	public static long verifyLogContent(String inputLocation, ArrayList<String> targetLocations)
			throws IOException, CriblException {
		// Check inputs
		if (inputLocation == null || inputLocation.length() == 0) {
			Logging.error("There needs to be a specified inputLocation");
		}
		if (targetLocations == null || targetLocations.size() == 0) {
			Logging.error("There needs to be a specified targetLocations");
		}

		// Hash map to track counts of the log contents
		Map<Integer, Long> counts = new HashMap<Integer, Long>();

		// Build the input map for comparison
		BufferedReader input = SystemUtil.getBufferedReader(inputLocation);
		int inputKey;
		while ((inputKey = input.read()) > 0) {
			counts.put(inputKey, counts.getOrDefault(inputKey, new Long(0)) + 1);
		}

		// Display the line count as info
		// Note the final check would check the target new lines are same
		Long totalLines = counts.getOrDefault(NEW_LINE_INDEX, 0L);
		Logging.log("Total new line count of the input file: " + totalLines);

		// Decrement counts based on target logs.
		// Can fail early when key doesn't exist or value less than 0
		for (String targetLocation : targetLocations) {
			BufferedReader target = SystemUtil.getBufferedReader(targetLocation);
			int targetKey;
			while ((targetKey = target.read()) > 0) {
				if (counts.containsKey(targetKey)) {
					counts.put(targetKey, counts.get(targetKey) - 1);
					if (counts.get(targetKey) < 0) {
						Logging.error("Output log has extra characters not in input: '" + ((char) targetKey) + "'");
					}
				} else {
					Logging.error("Output log has extra characters not in input: '" + ((char) targetKey) + "'");
				}
			}
		}

		// Final check of original counts map should be 0
		for (int key : counts.keySet()) {
			if (counts.get(key) > 0) {
				Logging.error("Input log has extra characters not in output log: '" + ((char) key) + "'");
			}
		}

		return totalLines;
	}

	/**
	 * Try to estimate the number of corrupt log lines for a set of target log
	 * locations given a well known regex pattern. The possible log lines in
	 * question are written to console and the total number is returned to pass/fail
	 * a test
	 * 
	 * @param targetLocations the file location of the input used for the test
	 * @param logEntryPattern the regex to compare log entries to
	 * @return totalCorruptCount
	 * @throws IOException
	 * @throws CriblException
	 */
	public static long getCorruptLogCount(ArrayList<String> targetLocations, String logEntryPattern)
			throws IOException, CriblException {
		if (logEntryPattern == null || logEntryPattern.length() == 0) {
			Logging.error("There needs to be a specified regex pattern to search for");
		}
		if (targetLocations == null || targetLocations.size() == 0) {
			Logging.error("There needs to be a specified targetLocations for calculations");
		}

		Matcher matcher;
		Pattern pattern = Pattern.compile(logEntryPattern);

		StringBuilder output = new StringBuilder();
		long totalCorruptCount = 0;
		for (String targetLocation : targetLocations) {
			BufferedReader target = SystemUtil.getBufferedReader(targetLocation);

			int character;
			long lineCount = 0;
			StringBuilder lineBuilder = new StringBuilder().append("\n");
			while ((character = target.read()) > 0) {
				if (character == NEW_LINE_INDEX) {
					lineCount += 1;
					String line = lineBuilder.toString();
					matcher = pattern.matcher(line);
					if (!matcher.find()) {
						totalCorruptCount++;
						output.append("Corrupt log line not matching the expected regex at line: " + lineCount
								+ " in file: " + targetLocation + "\n    '" + line + "'\n");
					}

					lineBuilder = new StringBuilder();
				} else {
					lineBuilder.append((char) character);
				}
			}
		}

		Logging.log(output.toString());
		Logging.log("Total corrupt count: " + totalCorruptCount);

		return totalCorruptCount;
	}

	/**
	 * Verifies that the file size of inputs vs all targets match. Attempt to
	 * calculate avgDistancePercentage by totaling the absolute target size from an
	 * evenly split input size over the total number of target. This return can be
	 * used as a threshold to pass/fail tests
	 * 
	 * @param inputLocation   the file location of the input used for the test
	 * @param targetLocations the location of one or more events.logs from target
	 * @return avgDistancePercentage
	 * @throws CriblException
	 */
	public static int verifyLogSizes(String inputLocation, ArrayList<String> targetLocations) throws CriblException {
		// Check on input
		long inputSize = new File(inputLocation).length();
		if (inputSize == 0) {
			Logging.error("The file size doesn't exist or is empty at: " + inputLocation);
		}
		Logging.log("Verifying logs file sizes. The total size of targets should be in bytes: " + inputSize);
		if (targetLocations == null || targetLocations.size() == 0) {
			Logging.error("There needs to be a specified targetLocations for calculations");
		}

		int totalTargets = targetLocations.size();
		long expectedAverageSize = inputSize / totalTargets;
		if (expectedAverageSize == 0) {
			// Small files can cause divide by 0 error. Estimates are already way off so set
			// to 1 to be safe
			expectedAverageSize = 1;
		}
		long distance = 0;
		long totalTargetsSize = 0;
		for (String targetLocation : targetLocations) {
			long size = new File(targetLocation).length();
			distance += Math.abs(size - expectedAverageSize);
			totalTargetsSize += size;
		}

		// Verify for file sizes
		if (inputSize != totalTargetsSize) {
			Logging.error("Expected total size of events.log to be: " + inputSize + " but found a total size of: "
					+ totalTargetsSize);
		}

		// Average the total distances from expected average size over total targets to
		// get a balance metric
		int avgDistancePercentage = (int) (((distance * 100) / expectedAverageSize) / totalTargets);
		Logging.log("The distance is: " + distance);
		Logging.log("The expectedAverageSize is: " + expectedAverageSize);
		Logging.log("The totalTargets is: " + totalTargets);
		Logging.log("The average % delta between files is: " + avgDistancePercentage);

		return avgDistancePercentage;
	}
}
