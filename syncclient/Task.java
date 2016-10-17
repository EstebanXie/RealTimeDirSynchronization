
import java.util.LinkedList;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.Instruction;
import filesync.SynchronisedFile;

/**
 * This class is a capsulation of FileSync class, since FileSync 
 * can not handle creation and deletion operation. 
 * @author yuec
 *
 */

public class Task implements Runnable {
	
	/* FileSync Object that handles modification operation */
	private SynchronisedFile file;
	
	/* Event Code indicates file creation, deletion and modification */
	int command;

	/**
	 * constructor
	 * @param sFile
	 */
	public Task(SynchronisedFile sFile) {
		file = sFile;

		// 0 for modify
		command = 0;
	}
	
	public void setCmd(int cmd) {
		command = cmd;
	}
	
	public int getCmd() {
		return command;
	}
	
	public SynchronisedFile getFileHandler() {
		return file;
	}
	
	@Override
	/**
	 * send a series of instruction of the same operation
	 * Executor.out is the output stream bind on socket, it
	 * needs to be locked to serialise instructions
	 * 
	 * The loop wakes up if a checkFileState method is called on
	 * the same FileSync object.
	 */
	public void run() {
		Instruction inst;
		LinkedList<Instruction> instructions = new LinkedList<Instruction>();
		while(true) {
			if ((inst=file.NextInstruction()) != null) {
					String msg = inst.ToJSON();
					instructions.add(inst);
					try {
						JSONObject obj = (JSONObject)new JSONParser().parse(msg);
						if (obj.get("Type").equals("EndUpdate")) {
							synchronized(Executor.out) {
								for (Instruction i : instructions) {
									Executor.sendInstruction(command, i, i.ToJSON());
								}
							}
							// clear messages pool and prepare for next instructions
							instructions.clear();
						}
					} catch (ParseException e) {
						System.err.println(e.getMessage());
						System.err.println("message: " + msg + " not transmitted");
					}
				} else {
					// exit the thread if deletion operation happens
					break;
				}
		}
	}

}
