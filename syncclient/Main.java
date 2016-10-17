import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import filesync.SynchronisedFile;

/**
 * This is the entry class
 * 1. parse command line args
 * 2. invoke connection method
 * 3. pack FileSync obejct into Task object
 * 4. run task as thread
 * 5. establish initial creation sync to server
 * 6. register directory watch service
 * @author cyue
 *
 */

public class Main {

	@Option(name = "-f", usage="absolute folder path")
	private static String directory = "";
	
	@Option(name = "-h", usage="server side IPv4 address")
	private static String address = "localhost";
	
	@Option(name = "-p", usage="server side IPv4 port")
	private static String port = "4444";
	
	public static void main(String[] args) throws IOException{
		new Main().parse(args);
		File dir = new File(directory);
		
		// works only if dir is a directory
		if (!dir.isDirectory()) {
			throw new IOException(directory + " is not a directory");
		}
		

		Executor.connect(address, Integer.parseInt(port));
		// debug
		System.out.println("Server connected - " + address + ":" + port);
		
		// load files to task object pool
		Hashtable<String, Task> tasks = new Hashtable<String, Task>();
		Hashtable<String, Thread> threads = new Hashtable<String, Thread>();
		
		for (String filename : dir.list()) {
			// load files to task object pool
			Task task = new Task(
					new SynchronisedFile(directory + "/" + filename));
			tasks.put(filename, task);
			
			// load files to thread object pool
			Thread thread = new Thread(task);
			threads.put(filename, thread);
			thread.setDaemon(true);
			thread.start();
		}
		
		
		Executor.initWithServer(tasks);
		
		// watch the directory and pull event to tasks if there are 
		// modification, deletion or addition
		Monitor monitor = new Monitor(directory);

		try {
			monitor.registerNotification(tasks, threads);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method complete two task :
	 * 1. parse input args
	 * 2. instruct users to input legal args if they do not.
	 * @param args
	 * @throws IOException
	 */
	private void parse(String[] args) throws IOException{
		CmdLineParser parser = new CmdLineParser(this);
		parser.setUsageWidth(80);
		
		try{
			parser.parseArgument(args);
		} catch( CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar syncclient.jar [options...]");
			parser.printUsage(System.err);
			System.err.println();
			System.exit(1);
		}
	}

}
