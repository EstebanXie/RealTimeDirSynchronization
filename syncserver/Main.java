import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Hashtable;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import filesync.SynchronisedFile;

/**
 * This is the entry of server program and functions as:
 * 1. parse corresponding args
 * 2. establish FileSync Hash tables, key is filename, value is FileSync object
 * 3. establish connection 
 * 4. watch and wait for instruction from client and sync the operation on 
 *    server.
 * @author cyue
 *
 */

public class Main {
	
	@Option(name = "-f", usage="absolute folder path correspond to the client folder")
	private static String directory = "";
	
	@Option(name = "-p", usage="listening port")
	private static String port = "4444";

	public static void main(String[] args) throws IOException {
		new Main().parse(args);
		
		File dir = new File(directory);
		
		// works only if dir is a directory
		if (!dir.isDirectory()) {
			throw new IOException(directory + " is not a directory");
		}
		
		Hashtable<String, SynchronisedFile> syncFiles = new Hashtable<String, SynchronisedFile>();
		for(String filename : dir.list()) {
			SynchronisedFile file = new SynchronisedFile(directory + "/" + filename);
			syncFiles.put(filename, file);
		}
		
		Socket conn = Executor.getConnection(Integer.parseInt(port));
		
		
		Executor.watch(conn, directory, syncFiles);
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
			System.err.println("java -jar syncserver.jar [options...]");
			parser.printUsage(System.err);
			System.err.println();
			System.exit(1);
		}
	}

}
