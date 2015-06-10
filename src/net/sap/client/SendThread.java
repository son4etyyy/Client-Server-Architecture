package net.sap.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Scanner;

public class SendThread implements Runnable {
	private String message;
	private SocketChannel socketChannel;

	public SendThread(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	private boolean checkCommand(String command, String splitedMessage[]) {
		if (command.equals("register") || command.equals("login") || command.equals("sendFile")) {
			if (splitedMessage.length <= 2) {
				return false;
			}
		} else if(command.equals("downloadFile")) {
			if (splitedMessage.length <= 1) {
				return false;
			}
		} else if (!command.equals("send") && !command.equals("logout")) {
			return false;
		}
		return true;
	}
	
	private void sendFile(String fileName, String path) {
		String pathAndName = path + "/" + fileName;
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pathAndName), "utf-8"));
		put
		String line;
        while((line=reader.readLine())!=null)
        {
            put.write(line);
            put.flush();
        }
        reader.close();
	}

	@Override
	public void run() {
		Scanner terminalInput = new Scanner(System.in);
		while (terminalInput.hasNextLine()) {
			//check if there are files for sending
			for(Map.Entry<String, Boolean> current : Client.filesForSending.entrySet()) {
				if(current.getValue() != null && current.getValue()) {
					sendFile(current.getKey());
				}
			}
			message = terminalInput.nextLine();
			String splitedMessage[] = message.split("\\s+");
			String command = splitedMessage[0];
			if (checkCommand(command, splitedMessage)) {
				CharBuffer buffer = null;
				if(command.equals("sendFile")) {
					String path = splitedMessage[1];
					File file = new File(path);
					if(!file.exists()) {
						System.out.println("This file doesn't exist!");
					} else {
						String fileName = splitedMessage[2];
						long fileSize = file.length();
						buffer = CharBuffer.wrap("TrySendFile" + " " + fileSize + " " + fileName);
						Client.filesForSending.put(fileName, false);
					}
				} else {
					buffer = CharBuffer.wrap(message);
				}
				while (buffer.hasRemaining()) {
					try {
						socketChannel.write(Charset.defaultCharset().encode(buffer));
					} catch (IOException e) {
						System.err.println("Could not send the message to the Sever!");
						e.printStackTrace();
					}
				}
				if (command.equals("logout")) {
					terminalInput.close();
					System.out.println("logout...");
					System.exit(0);
				}
			} else {
				System.out.println("Invalid command!");
			}
		}
		terminalInput.close();
	}
}
