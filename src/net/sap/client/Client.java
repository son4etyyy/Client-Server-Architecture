package net.sap.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

public class Client implements Runnable {
	public static final int SERVER_PORT = 2222;
	private SocketChannel socketChanel;	
	protected static HashMap<String, Boolean> filesForSending = new HashMap<String, Boolean>();

	public Client() {
		init();
	}
	
	private void init(){
		try {
			socketChanel = SocketChannel.open();
			socketChanel.configureBlocking(false);
			socketChanel.connect(new InetSocketAddress("localhost", SERVER_PORT));
			while (!socketChanel.finishConnect());
			System.out.println("Connected");
		} catch (IOException e) {
			if(socketChanel.isOpen()){
				try {
					socketChanel.close();
				} catch (IOException e1) {
					System.err.println("Could not close the socket " + socketChanel);
					e1.printStackTrace();
				}
			}
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	@Override
	public void run() {
		Thread sendThread = new Thread(new SendThread(socketChanel));
		Thread receiveThread = new Thread(new ReceiveThread(socketChanel));
		System.out.println("Enter command: register [username] [password]; login [username] [password]; send [message]; sendFile [path] [name]; downloadFile [name]; or logout.");
		sendThread.start();
		receiveThread.start();
	}
	
	public static void main(String[] args) throws Exception {
		Client client = new Client();
		client.run();
	}

}
