import java.nio.BufferOverflowException;
import java.nio.channels.SocketChannel;

import si.evil.server.Buffer;
import si.evil.server.Connection;
import si.evil.server.WServer;


public class ChatConnection extends Connection {

	public ChatConnection(SocketChannel c, WServer manager) {
		super(c,manager);
	}
	
	@Override
	public void onRecv(Buffer data){
		this.manager.brodcastMessage(data);
	}

}
