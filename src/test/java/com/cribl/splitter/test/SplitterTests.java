package com.cribl.splitter.test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.cribl.splitter.util.CriblException;
import com.cribl.splitter.util.Logging;
import com.cribl.splitter.util.SystemUtil;
import com.cribl.splitter.util.VerificationUtil;

public class SplitterTests {
	// Default log and container names
	private static final String AGENT_INPUT = "node/agent/inputs/input.log";
	private static final String DOCKER_LOGS = "/usr/src/app/events.log";
	private static final String TARGET1 = "cribl-splitter_target_1_1";
	private static final String TARGET2 = "cribl-splitter_target_2_1";
	private static ArrayList<String> currentTargetLogs;
	private static String currentInputLog;

	// Acceptable corruption perectage rounded up
	// https://en.wikipedia.org/wiki/Packet_loss#Acceptable_packet_loss
	private final static int PACKET_LOSS_PERCENTAGE = 3;

	// Threshold for how far off the expected average size is off from equal parts
	// of original input file
	private final static int FILE_SIZE_BALANCE_PERCENTAGE_THRESHOLD = 10;

	@BeforeClass
	public void beforeClass() throws IOException, CriblException {
		SystemUtil.runCommand("docker-compose down", null);
	}

	/**
	 * Run the application under test that does the necessary file setup, running
	 * the docker compose, grabbing logs and shutting down the test environment. A
	 * debug flag can be used to help with test case development after grabbing all
	 * the necessary files after first run.
	 * 
	 * @param name  the test case name. The test name, input files and logs are all
	 *              matched by this for easier investigation of issues
	 * @param debug when set to true skips the docker portion and just analyze the
	 *              files already in the locations
	 * @throws IOException
	 * @throws CriblException
	 */
	private void runApplication(String name, Boolean debug) throws IOException, CriblException {
		// Set all the current input and output logs files and directories
		Files.createDirectories(Paths.get("logs/" + name));
		currentTargetLogs = new ArrayList<String>();
		currentTargetLogs.add("logs/" + name + "/events1.log");
		currentTargetLogs.add("logs/" + name + "/events2.log");
		currentInputLog = "inputs/" + name + ".log";

		// Run application, copy file and tear down
		if (!debug) {
			SystemUtil.copyFile(new File(currentInputLog), new File(AGENT_INPUT));
			SystemUtil.runCommand("docker-compose up --build", "agent_1 exited");
			SystemUtil.runCommand("docker cp " + TARGET1 + ":" + DOCKER_LOGS + " ./" + currentTargetLogs.get(0), null);
			SystemUtil.runCommand("docker cp " + TARGET2 + ":" + DOCKER_LOGS + " ./" + currentTargetLogs.get(1), null);
			SystemUtil.runCommand("docker-compose down", null);
		}
	}

	/**
	 * This does the basic verification of the log contents, log file sizes and the
	 * estimated corrupted logs based on a regex and fails the test if they don't
	 * meet the thresholds
	 * 
	 * @param inputLocation   the input file location
	 * @param targetLocations the target log file locations
	 * @param regex           the pattern to confirm a valid log line
	 * @throws IOException
	 * @throws CriblException
	 */
	private static void basicVerification(String inputLocation, ArrayList<String> targetLocations, String regex)
			throws IOException, CriblException {
		long lineCount = VerificationUtil.verifyLogContent(inputLocation, targetLocations);
		int avgDistancePercentage = VerificationUtil.verifyLogSizes(inputLocation, targetLocations);
		if (avgDistancePercentage > FILE_SIZE_BALANCE_PERCENTAGE_THRESHOLD) {
			Logging.error("The events.log file sizes from the target are inbalanced");
		}
		long corruptCount = VerificationUtil.getCorruptLogCount(targetLocations, regex);
		if (((int) (corruptCount * 100) / lineCount) > PACKET_LOSS_PERCENTAGE) {
			Logging.error("The number of corrupt packets % exceeds the threshold of: " + PACKET_LOSS_PERCENTAGE);
		}
	}

	/**
	 * <b>Test case:</b> largeOneMillionEventsTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * processing a large number of events. <br>
	 * 
	 * <b>Goal:<b> Verify that even though the events.log may appear out of order
	 * everything gets transmitted. The total file size of the events sums up to the
	 * original input.log and that they are relatively balanced in size. Lastly
	 * assuming a valid entry/packet is "This is event number #" attempt to figure
	 * out the number of corrupt lines meets a certain threshold for a streaming
	 * application
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void largeOneMillionEventsTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		basicVerification(currentInputLog, currentTargetLogs, "This is event number (\\d+)");
	}

	/**
	 * <b>Test case:</b> emptyLogFileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when an input file is present but empty. <br>
	 * 
	 * <b>Goal:<b> Verify the application runs fine as expected an exits gracefully
	 * and that because there were no entries that no events.logs are produced.
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void emptyLogFileTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertFalse(new File(currentTargetLogs.get(0)).exists());
		Assert.assertFalse(new File(currentTargetLogs.get(1)).exists());
	}

	/**
	 * <b>Test case:</b> noInputLogFileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file does not exist. <br>
	 * 
	 * <b>Goal:<b> Verify the application throws an error unable to read/find the
	 * file.
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void noInputLogFileTest(Method method) throws IOException, CriblException {
		SystemUtil.deleteFile(AGENT_INPUT);
		String output = SystemUtil.runCommand("docker-compose up --build", "agent_1 exited");
		SystemUtil.runCommand("docker-compose down", null);

		Assert.assertTrue(output.contains("Error: ENOENT: no such file or directory, open 'agent/inputs/input.log'"));
	}

	/**
	 * <b>Test case:</b> oneLineLogFileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file has one line and file is less than the 64Kb buffer of
	 * agent <br>
	 * 
	 * <b>Goal:<b> Verify the application always sends the line to the first target
	 * and the second target has no logs because it's less than the default agent
	 * buffer
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void oneLineLogFileTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertFalse(new File(currentTargetLogs.get(1)).exists());
		currentTargetLogs.remove(1);
		basicVerification(currentInputLog, currentTargetLogs, "This is event number (\\d+)");
	}

	/**
	 * <b>Test case:</b> sameLineLogFileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file has all the same lines. <br>
	 * 
	 * <b>Goal:<b> Verify the application is not affected by log lines where there
	 * is no apparent order to being with. All the same checks still pass
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void sameLineLogFileTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		basicVerification(currentInputLog, currentTargetLogs, "(The quick brown fox jumps over the lazy dog)");
	}

	/**
	 * <b>Test case:</b> specialCharactersTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file special characters <br>
	 * 
	 * <b>Goal:<b>Verify the application is not affected by log lines where there is
	 * are special characters
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void specialCharactersTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		basicVerification(currentInputLog, currentTargetLogs, ".{50}");
	}

	/**
	 * <b>Test case:</b> sampleJpegFileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file is a binary application <br>
	 * 
	 * <b>Goal:<b> Verify the application file size. The buffered reader cannot parse
	 * the binary proper.
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void sampleJpegFileTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	/**
	 * <b>Test case:</b> utf8FileTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when the input file is encoded with UTF-8 and on one line ie no \n
	 * character<br>
	 * 
	 * <b>Goal:<b> Verify the application file size with all data should go to
	 * target1 only. There is no regex to test this content
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void utf8FileTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
		Assert.assertFalse(new File(currentTargetLogs.get(1)).exists());
		currentTargetLogs.remove(1);
		VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
	}

	/**
	 * <b>Test case:</b> Series of new line tests <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * when there are 1,2,3 and many \n characters only<br>
	 * 
	 * <b>Goal:<b> Verify the application file size and content is correct. The
	 * second target file does not exist for the one case but for multiple \n
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void newLineOneTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertFalse(new File(currentTargetLogs.get(1)).exists());
		currentTargetLogs.remove(1);
		long lineCount = VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
		// Since a \n is one byte would be equal to count
		Assert.assertEquals(new File(currentTargetLogs.get(0)).length(), lineCount);
		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	@Test
	public void newLineTwoTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertTrue(new File(currentTargetLogs.get(1)).exists());
		long lineCount = VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
		// Since a \n is one byte
		Assert.assertEquals(new File(currentTargetLogs.get(0)).length() + new File(currentTargetLogs.get(1)).length(),
				lineCount);
		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	@Test
	public void newLineThreeTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertTrue(new File(currentTargetLogs.get(1)).exists());
		long lineCount = VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
		// Since a \n is one byte
		Assert.assertEquals(new File(currentTargetLogs.get(0)).length() + new File(currentTargetLogs.get(1)).length(),
				lineCount);
		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	@Test
	public void newLineManyTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		Assert.assertTrue(new File(currentTargetLogs.get(1)).exists());
		long lineCount = VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
		// Since a \n is one byte
		Assert.assertEquals(new File(currentTargetLogs.get(0)).length() + new File(currentTargetLogs.get(1)).length(),
				lineCount);
		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	/**
	 * <b>Test case:</b> apacheLogsTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * with real apache access logs<br>
	 * 
	 * <b>Goal:<b> Verify the application file size and content is correct. The regex
	 * is able to parse the log items as well
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void apacheLogsTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		basicVerification(currentInputLog, currentTargetLogs,
				"^([\\d.]+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+-]\\d{4})\\] \"(.+?)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"(.+?)\"");
	}

	/**
	 * <b>Test case:</b> languageEncodingTest <br>
	 * 
	 * <b>Purpose:<b> The purpose of this test is to see how the application behaves
	 * with UTF test from different languages<br>
	 * 
	 * <b>Goal:<b>Verify the application file size and content is correct. With a
	 * small file size this algorithm of still splitting into two files.
	 * 
	 * @param method
	 * @throws IOException
	 * @throws CriblException
	 */
	@Test
	public void languageEncodingTest(Method method) throws IOException, CriblException {
		runApplication(method.getName(), false);

		VerificationUtil.verifyLogContent(currentInputLog, currentTargetLogs);
		// Note the files are not balanced due to small file size
		VerificationUtil.verifyLogSizes(currentInputLog, currentTargetLogs);
	}

	@AfterMethod(alwaysRun = true)
	public void afterMethod() throws IOException, CriblException {
		// Run in case anything test fails
		SystemUtil.runCommand("docker-compose down", null);
	}

}
