import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Hashtable;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import filesync.SynchronisedFile;

/**
 * This Class monitors the folder and response appropriately 
 * according to the notified event ( creation, modification or deletion)
 * @author yuec
 *
 */

public class Monitor {
	
	String directory;
	
	WatchService watcher;

	/**
	 * constructor 
	 * @param path
	 */
	public Monitor(String path) {
		try{
			directory = path;
			
			// register watch service of the directory
			watcher = FileSystems.getDefault().newWatchService();
			Path dir = Paths.get(directory);
			dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			
		} catch(IOException ex) {
			System.err.println(ex.getMessage());
			System.exit(-1);
		} 
	}
	
	/**
	 * register watch service and call task to response to specific event.
	 * EVENT_MODIFY - invoke CheckFileState method and wake up NextInstruction 
	 * EVENT_DELETE - invoke deletionHandler and delete related task and thread
	 * EVENT_CREATE - create new FileSync Object, Task Object and new running Thread
	 * @param tasks
	 * @param threads
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void registerNotification(Hashtable<String, Task> tasks, Hashtable<String, Thread> threads) 
			throws IOException, InterruptedException {
		while (true) {
			WatchKey key = watcher.take();
			
			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();
				
				@SuppressWarnings("unchecked")
				WatchEvent<Path> ev = (WatchEvent<Path>) event;
				Path filename = ev.context();
				
				if (kind == OVERFLOW) {
					boolean valid = key.reset();
					if (!valid) break;
					continue;
				}
				
				if (kind == ENTRY_MODIFY) {
					// avoid mismatch temporary swap file in Linux System
					if (filename.toString().matches("\\..*.swp.*")) {
						boolean valid = key.reset();
						if (!valid) break;
						continue;
					}

					tasks.get(filename.toString()).getFileHandler().CheckFileState();
				} else if (kind == ENTRY_CREATE) {
					// avoid mismatch temporary swap file in Linux System
					if (filename.toString().matches("\\..*.swp.*")) {
						boolean valid = key.reset();
						if (!valid) break;
						continue;
					}
				
					tasks.put(filename.toString(), new Task(
							new SynchronisedFile(directory + "/" + filename.toString())));
					
					// command 1 signify create command
					tasks.get(filename.toString()).setCmd(1);
					
					// start the new task to monitor the new file
					Thread newThread = new Thread(tasks.get(filename.toString()));
					newThread.setDaemon(true);
					threads.put(filename.toString(), newThread);
					newThread.start();
					
					// invoke CheckFileState to notify instructions in Task
					tasks.get(filename.toString()).getFileHandler().CheckFileState();
				} else if (kind == ENTRY_DELETE) {
					// avoid mismatch temporary swap file in Linux System
					if (filename.toString().matches("\\..*.swp.*")) {
						boolean valid = key.reset();
						if (!valid) break;
						continue;
					}
					
					// kill the shadow thread of the deleted file
					Thread dyingThread = threads.remove(filename.toString());
					dyingThread.interrupt();
					
					// delete from tasks
					Task deletedTask = tasks.remove(filename.toString());
					deletedTask.setCmd(2);
					
					// send delete message to server
					Executor.deletionHandler(deletedTask, filename.toString());	
				}
				
				boolean valid = key.reset();
				if (!valid) break;
			}
		}
	} 
}
