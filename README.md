# Discription
We assume a source folder and an empty target folder. RTDS firstly copy all content in the folder including the folder itself to target at the initialization phase( start of running the program). Then it synchronize every modification from source to target. It can monitor remotely via using the PRC paradiam.

# Paradiam
The whole system running in C/S arch and synchronize in RPC mode. When the server runs, it initialize the content from source to target. The client and server can be on different machine via network.

- Server
 * 1. parse corresponding args
 * 2. establish FileSync Hash tables, key is filename, value is FileSync object
 * 3. establish connection 
 * 4. watch and wait for instruction from client and sync the operation on 
 
- Client
 * 1. parse command line args
 * 2. invoke connection method
 * 3. pack FileSync obejct into Task object
 * 4. run task as thread
 * 5. establish initial creation sync to server
 * 6. register directory watch service
 
# Running
- Running server: java -jar syncserver.jar -f [folder on server side] -p [port]

- Running client: java -jar syncclient.jar -f [folder on client side] -h [server ip address] -p [port on server]
