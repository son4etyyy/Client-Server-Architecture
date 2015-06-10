package net.sap.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class ReceiveThread implements Runnable {
	private String message;
	private SocketChannel socketChannel;
	private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
	private Selector selector;

	public ReceiveThread(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
		try {
			selector = Selector.open();
		} catch (IOException e) {
			System.out.println("Could not open a Selector in " + socketChannel);
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				socketChannel.register(selector, SelectionKey.OP_READ);
			} catch (ClosedChannelException e) {
				System.out.println("Could not register a selector.");
				e.printStackTrace();
			}
			int selected = 0;
			try {
				selected = selector.select();
			} catch (IOException e) {
				System.out.println("Could not select a selector.");
				e.printStackTrace();
			}
			if (selected == 0) {
				continue;
			}
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> it = selectedKeys.iterator();
			while (it.hasNext()) {
				SelectionKey key = it.next();
				if (key.isReadable()) {
					socketChannel = (SocketChannel) key.channel();
					message = "";
					while (true) {
						readBuffer.clear();
						int r = 0;
						try {
							r = socketChannel.read(readBuffer);
						} catch (IOException e) {
							System.err.println("The socket has been closed.");
							e.printStackTrace();
							System.exit(1);
						}
						if (r <= 0) {
							break;
						}
						readBuffer.flip();
						message += Charset.defaultCharset().decode(readBuffer);
					}
					String splitedMessage[] = message.split("\\s+");
					if(splitedMessage[0].equals("sendFile")) {
						if(splitedMessage.length > 1){
							Client.filesForSending.replace(splitedMessage[1], true);
						} else {
							System.out.println("No file name send!");
						}
					} else if (!message.isEmpty()) {
						System.out.println(message);
					}
				}
				it.remove();
			}
		}
	}
}
