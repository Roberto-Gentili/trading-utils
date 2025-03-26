package org.rg.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

public class ShellExecutor {

	public static Thread execute(String command, boolean printOutput) {
		Thread thread = new Thread(() -> {
			try {
				ProcessBuilder processBuilder = new ProcessBuilder();
	            processBuilder.command("cmd.exe", "/c", command);
	        	Process process = processBuilder.start();
	            StringBuilder output = new StringBuilder();
	            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	            String line;
	            while ((line = reader.readLine()) != null) {
	                output.append(line + "\n");
	            }
	            int exitVal = process.waitFor();
	            if (printOutput) {
	            	logLine(output);
	            }
	            logLine("executed command: " + command);
	            logLine("Process exit code: " + exitVal);
			} catch (Throwable exc) {
				exc.printStackTrace();
			}
		}, "Process executor of 'Thread " + Thread.currentThread().getName() + "'");
		thread.start();
		return thread;
	}

	private static void logLine(Object log) {
		System.out.println(new Date().toString() + " - " + Thread.currentThread() + " -> " + log);
	}

	private static void log(String log) {
		System.out.print(new Date() + " - " + Thread.currentThread() + " -> " + log);
	}

}
