package net.sap.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class Server implements Runnable {
	public static final int PORT = 2222;
	private static final long MAX_SIZE_FILE = 1000;
	private static final long MAX_SIZE_All_FILES = 10000;
	private ByteBuffer echoBuffer;
	private Map<String, String> nameToPass;// username to password
	private Set<String> logedInUsers;
	private Map<SelectionKey, String> logedInKeyToName;// selectionkey to username		
	private Selector selector;
	private ServerSocketChannel serverSocketChannel;
	private BufferedReader reader;
	private BufferedWriter writer;

	public Server() {
		echoBuffer = ByteBuffer.allocate(1024);
		nameToPass = new ConcurrentHashMap<String, String>();
		logedInUsers = new ConcurrentSkipListSet<String>();
		logedInKeyToName = new ConcurrentHashMap<SelectionKey, String>();		
		init();
	}
	
	private void init(){
		try {
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.bind(new InetSocketAddress(PORT));
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			reader = null;
			//File file = new File("registerdUsersAndPasswsords.txt");
			reader = new BufferedReader(new InputStreamReader(new FileInputStream("registerdUsersAndPasswords.txt"), "utf-8"));
			//writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("registerdUsersAndPasswords.txt"), "utf-8"));
			String line;
			while((line = reader.readLine()) != null) {
				String splitArray[] = line.split("\\s+");
				nameToPass.put(splitArray[0], splitArray[1]);
			}		
			if(reader != null){
				reader.close();
			}
			System.out.println("Initialized");
		} catch (IOException e) {
			System.err.println("Could not initialize the Sever!");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private void closeConnection(SelectionKey key, SocketChannel sc) {
		try {
			sc.close();
		} catch (IOException e) {
			System.err.println("I/O error occured while closing the socket.");
			e.printStackTrace();
		}
		key.cancel();
	}
	
	private String receive(SelectionKey key, SocketChannel sc){
		String message = "";
		while (true) {
			echoBuffer.clear();
			int r = 0;
			try {
				r = sc.read(echoBuffer);
			} catch (IOException e) {
				System.err.println("A client has loged out " + sc);
				if(logedInKeyToName.containsKey(key)){
					logedInUsers.remove(logedInKeyToName.get(key));
				}
				logedInKeyToName.remove(key);
				closeConnection(key, sc);
				e.printStackTrace();
			}
			if (r <= 0) {
				break;
			}
			echoBuffer.flip();
			message += Charset.defaultCharset().decode(echoBuffer);
		}		
		return message;		
	}

	private void sendResponse(SocketChannel sc, String response) {
		CharBuffer buffer = CharBuffer.wrap(response);
		while (buffer.hasRemaining()) {
			try {
				sc.write(Charset.defaultCharset().encode(buffer));
			} catch (IOException e) {
				System.err.println("Could not send the message to " + sc);
				e.printStackTrace();
			}
		}
	}
	
	private void commandRegister(SelectionKey key, SocketChannel sc, String userName, String password){
		String response;
		if (nameToPass.putIfAbsent(userName, password) != null) {
			// send response
			response = "This username is already used!";
			sendResponse(sc, response);
		} else {
			logedInUsers.add(userName);
			logedInKeyToName.put(key, userName);
			response = "You loged in.";
			sendResponse(sc, response);
			writer = null;
			try {
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("registerdUsersAndPasswords.txt", true), "utf-8")); //name, appendMode
				writer.append(userName + " " + password);
				writer.newLine();
			} catch (IOException e) {
			  System.err.println("Problem with writyng to registerdUsersAndPasswords.txt.");
			  e.printStackTrace();
			} finally {
			   try {writer.close();} catch (Exception ex) {
				   System.err.println("Couldn't close registerdUsersAndPasswords.txt.");
				   ex.printStackTrace();
			   }
			}
		}
	}
	
	private void commandLogin(SelectionKey key, SocketChannel sc, String userName, String password){
		String response;
		if(logedInUsers.contains(userName)){
			response = "This user is already loged in!";
			sendResponse(sc, response);
		} else if(nameToPass.containsKey(userName)){
			if(!nameToPass.get(userName).equals(password)){
				response = "Wrong password.";
				sendResponse(sc, response);
			} else {
				logedInUsers.add(userName);
				logedInKeyToName.put(key, userName);
				response = "You loged in.";
				sendResponse(sc, response);
			}
		} else {
			response = "You are not registered.";
			sendResponse(sc, response);
		}
	}
	
	private void commandSend(SelectionKey key, SocketChannel sc, String text){
		// send to all others except key
		boolean sendSuccessfully = true;
		//
		for (Entry<SelectionKey, String> entry : logedInKeyToName.entrySet()) {
			SelectionKey currKey = entry.getKey();
			if (currKey.equals(key)) {
				continue;
			}
			SocketChannel currSocketChanel = (SocketChannel) currKey.channel();
			sendResponse(currSocketChanel, text);//sendResponse(currSocketChanel, text,sendSuccessfully)
		}
		String response;
		if (sendSuccessfully) {
			response = "The message was send succesfully!";
		} else {
			response = "The message was not send succesfully!";
		}
		sendResponse(sc, response);
	}
	
	private void commandLogout(SelectionKey key, SocketChannel sc){
		logedInUsers.remove(logedInKeyToName.get(key));
		logedInKeyToName.remove(key);
		closeConnection(key, sc);
	}

	@Override
	public void run() {
		while (true) {
			int selected = 0;
			try {
				selected = selector.select();
			} catch (IOException e) {
				System.err.println("Couldn't select a selector.");
				e.printStackTrace();
			}
			if (selected == 0) {
				continue;
			}
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> it = selectedKeys.iterator();

			while (it.hasNext()) {
				SelectionKey key = it.next();
				if (key.isAcceptable()) {
					// Accept the new connection
					SocketChannel sc;
					try {
						sc = serverSocketChannel.accept();
						if(sc.equals(null)){
							continue;
						}
						sc.configureBlocking(false);
					} catch (IOException e) {
						System.err.println("Error with serverSocketChannel occured.");
						e.printStackTrace();
						continue;
					}
					// Add the new connection to the selector
					try {
						sc.register(selector, SelectionKey.OP_READ);
					} catch (ClosedChannelException e) {
						System.err.println("The channel has been closed.");
						e.printStackTrace();
						continue;
					}
					System.out.println("Got connection from " + sc);
				} else if (key.isReadable()) {
					// Read the data
					SocketChannel sc = (SocketChannel) key.channel();
					if(!sc.isConnected()) {
						key.cancel();
						it.remove();
						continue;					
					}
					String message = receive(key, sc);
					System.out.println("Message " + message + ", from " + sc);
					String splitedMessage[] = message.split("\\s+");
					String command = splitedMessage[0];
					if (command.equals("register")) {
						String userName = splitedMessage[1];
						String password = splitedMessage[2];
						commandRegister(key, sc, userName, password);
					} else if (command.equals("login")) {
						String userName = splitedMessage[1];
						String password = splitedMessage[2];
						commandLogin(key, sc, userName, password);
					} else if (command.equals("send")) {
						if (!logedInKeyToName.containsKey(key)) {
							sendResponse(sc,"You are not loged in!");
						} else {
							String text = "";
							for (int i = 1; i < splitedMessage.length; i++) {
								text += splitedMessage[i];
								text += " ";
							}
							commandSend(key, sc, text);
						}
					} else if(command.equals("TrySendFile")) {
						if (!logedInKeyToName.containsKey(key)) {
							sendResponse(sc,"You are not loged in!");
						} else {
							long fileSize = Integer.parseInt(splitedMessage[1]);
							String fileName = splitedMessage[2];
							if(fileSize <= MAX_SIZE_FILE) {
								sendResponse(sc, "sendFile" + " " + fileName);
							} else {
								sendResponse(sc, fileName + " is too big to be sent!");
							}
						}
					} else if(command.equals("sendFile")) {
						if (!logedInKeyToName.containsKey(key)) {
							sendResponse(sc,"You are not loged in!");
						} else {
							long fileSize = Integer.parseInt(splitedMessage[1]);
							String fileName = splitedMessage[2];
							if(fileSize <= MAX_SIZE_FILE) {
								sendResponse(sc, "sendFile" + " " + fileName);
							} else {
								sendResponse(sc, fileName + " is too big to be sent!");
							}
						}
					} else if (command.equals("logout")) {
						if (!logedInKeyToName.containsKey(key)) {
							sendResponse(sc,"You are not loged in!");
						} else {
							commandLogout(key, sc);
							continue;
						}
					} else {
						sendResponse(sc, "Invalid command!");
					}
				}
				it.remove();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Server s = new Server();
		s.run();
	}
}
