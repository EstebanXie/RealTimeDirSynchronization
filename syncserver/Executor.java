import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.BlockUnavailableException;
import filesync.Instruction;
import filesync.InstructionFactory;
import filesync.SynchronisedFile;

/**
 * This class is server side Executor, all function includes:
 * 1. accept connection request from client 
 * 2. parse instruction and dispatch instruction to 
 *    the corresponding procedure.
 * @author cyue
 *
 */

public class Executor {

	/* listening port */
	private static int port = 4444;
	
	/* socket that receive specific instruction */
	private static Socket sock;
	
	/* const indicates the start of a series of instructions */
	private final static String CMD_START = "StartUpdate";
	
	/* event code indicates a file creation operation on the client */
	private final static long CREATE = 1;
	
	/* event code indicates a file deletion operation on the client */
	private final static long DELETE = 2;
	
	/* parse client defined instruction to raw instruction */
	private static final JSONParser parser = new JSONParser();
	
	/**
	 * accept conncetion from client side
	 * @param p - listening port 
	 * @return
	 */
	public static Socket getConnection(int p) {
		try(ServerSocket server = new ServerSocket(p)){
			sock = server.accept();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sock;
	}
	
	/**
	 * if no listen port input, use the default one, 4444
	 * @return
	 */
	public static Socket getConnection() {
		if (sock != null) {
			return sock;
		} else return getConnection(port);
	}

	@SuppressWarnings("unchecked")
	/**
	 * parse instruction and dispatch instruction to 
	 * create, delete or update procedure.
	 * @param conn
	 * @param directory
	 * @param syncFiles
	 * @throws IOException
	 */
	public static void watch(Socket conn,
			String directory, Hashtable<String, SynchronisedFile> syncFiles) throws IOException {
		DataOutputStream out = new DataOutputStream(conn.getOutputStream());
		DataInputStream in = new DataInputStream(conn.getInputStream());
		
		InstructionFactory instFact=new InstructionFactory();
		String filename = "";
		
		while(true) {
			String msg = in.readUTF();
			
			// debug
			// System.out.println("server: " + msg);
			
			// allocate the message to right sync file instance
			JSONObject obj = null;
			try {
				obj = (JSONObject)parser.parse(msg);
				
			} catch (ParseException e) {
				e.printStackTrace();
			}
			
			if (obj.containsKey("FileName")) {
				filename = String.valueOf(obj.get("FileName"));
			}
			// creation operation
			if (obj.get("Type").equals(CMD_START) && (long)obj.get("Cmd") == CREATE) {
				// create an empty file as handler
				File newFile = new File(directory + "/" + filename);
				if (newFile.exists()) {
					newFile.delete();
				} 
				newFile.createNewFile();
				
				// create synchronized-file instance
				SynchronisedFile file = new SynchronisedFile(directory + "/" + filename);
				syncFiles.put(filename, file);				
			}
				
			// deletion operation won't go through FileSync process
			if ((long)obj.get("Cmd") == DELETE) {
				// delete syncFile handler from hashtable			
				syncFiles.remove(filename);
					
				// delete file from the folder
				File deleteFile = new File(directory + "/" + filename);
				deleteFile.delete();
				
				JSONObject resp = new JSONObject();
				resp.put("Type", "Acknowlegement");
				out.writeUTF(resp.toJSONString());
				out.flush();
				continue;
			}
			
			// update operation & creation operation to finish
			// remove extra part from the raw message
			obj.remove("Cmd");
			Instruction inst = instFact.FromJSON(obj.toJSONString());
			try {
				syncFiles.get(filename).ProcessInstruction(inst);
			} catch (BlockUnavailableException e) {
				obj.clear();
				obj.put("Type", "NewBlockRequest");
				out.writeUTF(obj.toJSONString());
				out.flush();
				continue;
			}
			
			JSONObject resp = new JSONObject();
			resp.put("Type", "Acknowlegement");
			out.writeUTF(resp.toJSONString());
			out.flush();
		}
	}
	
	
}
