package com.cribl.splitter.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SystemUtil {
	/**
	 * Run a terminal command like 'docker-compose up --build'
	 * 
	 * @param command what would be typed in a terminal
	 * @param exit    specific text to look for to exit if process stays attached
	 * @return the captures input stream of the process
	 * @throws IOException
	 * @throws CriblException
	 */
	public static String runCommand(String command, String exit) throws IOException, CriblException {
		if (command == null || command.isEmpty()) {
			Logging.error("command cannot be null or empty");
		}
		Logging.log("Running command: " + command);
		Process p = Runtime.getRuntime().exec(command);

		StringBuilder sb = new StringBuilder();
		String line;
		BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		while ((line = input.readLine()) != null) {
			sb.append(line);
			System.out.println(line);
			if (exit != null && line.contains(exit)) {
				break;
			}
		}

		input.close();
		p.destroyForcibly();
		return sb.toString();
	}

	/**
	 * Copy a file from one location to another. Note the destination name can be
	 * different to rename the file as well
	 * 
	 * @param sourceFile      the location of the file to be copied relative to the
	 *                        directory the application is running on
	 * @param destinationFile the location of the new copied file
	 * @throws IOException
	 */
	public static void copyFile(File sourceFile, File destinationFile) throws IOException {
		Logging.log("Copying file from: " + sourceFile + "  to: " + destinationFile);
		FileInputStream fileInputStream = new FileInputStream(sourceFile);
		FileOutputStream fileOutputStream = new FileOutputStream(destinationFile);

		int bufferSize;
		byte[] buffer = new byte[512];
		while ((bufferSize = fileInputStream.read(buffer)) > 0) {
			fileOutputStream.write(buffer, 0, bufferSize);
		}

		fileInputStream.close();
		fileOutputStream.close();
	}

	/**
	 * Delete a file specified at the location
	 * 
	 * @param file the location of the file
	 * @return
	 */
	public static Boolean deleteFile(String file) {
		Logging.log("Deleting file: " + file);
		return new File(file).delete();
	}

	/**
	 * Get a buffered reader for a file
	 * 
	 * @param location where the file is located
	 * @return BufferedReader
	 * @throws FileNotFoundException
	 */
	public static BufferedReader getBufferedReader(String location) throws FileNotFoundException {
		Logging.log("Creating a buffered reader for file: " + location);
		File file = new File(location);
		FileReader fileReader = new FileReader(file);
		return new BufferedReader(fileReader);
	}
}
