import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.NewBlockInstruction;

/**
 * This Class defines a series of static methods concerning:
 * 	1.Connection and DataStream establishment
 *  2.Create, Update, Delte operation handler
 *  3.Inital Sync from the start of the system
 *  
 * @author cyue
 *
 */

public class Executor {
	
	
	/* client socket */
	static Socket socket;
	/*  send buffer that pushes message to server */
	public static DataOutputStream out;
	
	/* receive buffer that receive message from server */
	public static DataInputStream in;
	
	/* JSON parser to reconstruct instruction */
	private static final JSONParser parser = new JSONParser();
	
	/* Creation Code indicates a create operation in the folder */
	public static final int CREATE = 1;
	
	/* Modification Code indicates a update operation in the folder */
	public static final int MODIFY = 0;
	
	/* Deletion Code indicates a delete operation in the folder */
	public static final int DELETE = 2;
	
	/**
	 * connect to server and initialise send buffer and receive buffer 
	 * if successful.
	 * @param addr - server side address
	 * @param p - server side port
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public static void connect(String addr, int p) throws UnknownHostException, IOException {
		socket = new Socket(addr, p);
		out = new DataOutputStream(socket.getOutputStream());
		in = new DataInputStream(socket.getInputStream());
	}

	@SuppressWarnings("unchecked")
	/**
	 * Reconstruct deletion code into the sending instruction 
	 * @param deletedTask - A task instance that handles instruction
	 * @param filename - the deleted filename
	 */
	public static void deletionHandler(Task deletedTask, String filename) {
		JSONObject obj = new JSONObject();
		obj.put("FileName", filename);
		obj.put("Type", "Deletion");
		synchronized(Executor.out){
			sendInstruction(deletedTask.getCmd(), null, obj.toJSONString());
		}
		
	}
	
	
	@SuppressWarnings("unchecked")
	/**
	 * operation of send instruction, invoked by task instance.
	 * @param cmd - operation code (create, delete, modification)
	 * @param inst - raw instruction generated by FileSync Object
	 * @param message - content that need to be reconstruct into raw instruction
	 * @return the response from server
	 */
	public static String sendInstruction(int cmd, Instruction inst, String message) {
		// add command to the original message to represent the operation type
		// since the FileSync can not handle create operation on the server side
		JSONObject obj = null;
		String response = "";
		try {
			obj = (JSONObject) parser.parse(message);
			obj.put("Cmd", cmd);
			
			message = obj.toJSONString();
			/* if server send back NewBlock instruction
			 * client should always upgrade CopyBlock to NewBlock
			 */
			while(true) {
				out.writeUTF(message);
				out.flush();
			    
				response = in.readUTF();
				JSONObject respondObj = (JSONObject) parser.parse(response);
				
				// pack command code with NewBlockInstruction and resend
				if (respondObj.get("Type").equals("NewBlockRequest")){
					Instruction upgraded=new NewBlockInstruction((CopyBlockInstruction)inst);
					message = upgraded.ToJSON();
					obj = (JSONObject) parser.parse(message);
					obj.put("Cmd", cmd);
					message = obj.toJSONString();	
				} else break;
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} 
		return response;
	}

	/**
	 * send create instruction concerning all files in folder to server
	 * @param tasks - operate create instruction and send to server
	 */
	public static void initWithServer(Hashtable<String, Task> tasks) {
		for (Task task : tasks.values()) {
			try {
				task.setCmd(CREATE);
				task.getFileHandler().CheckFileState();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
