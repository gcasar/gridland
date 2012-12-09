import java.nio.BufferOverflowException;
import java.nio.channels.SocketChannel;

import si.evil.server.Buffer;
import si.evil.server.Connection;
import si.evil.server.Server;


public class ChatConnection extends Connection {

	public ChatConnection(SocketChannel c, Server manager) {
		super(c,manager);
	}
	
	@Override
	public void onRecv(Buffer data){
		this.manager.brodcastMessage(data);
	}

}
