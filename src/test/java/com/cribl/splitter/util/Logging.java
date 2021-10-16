package com.cribl.splitter.util;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.testng.Reporter;

public class Logging {
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

	public static void logToTestNgReporter() {
		System.setOut(new PrintStream(System.out) {
			public void println(String s) {
				Reporter.log(s);
				super.println(s);
			}
		});
	}

	public static void log(String s) {
		logToTestNgReporter();
		System.out.println(dateFormat.format(new Date()) + "::INFO::" + s);
	}

	public static void error(String s) throws CriblException {
		logToTestNgReporter();
		System.out.println(dateFormat.format(new Date()) + "::ERROR::" + s);
		throw new CriblException(s);
	}

	public static void warrning(String s) {
		logToTestNgReporter();
		System.out.println(dateFormat.format(new Date()) + "::WARNING::" + s);
	}
}
