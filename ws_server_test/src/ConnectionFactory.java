import java.nio.channels.SocketChannel;

import si.evil.server.Connection;
import si.evil.server.Server;


/**
 * We have to create our own factory so the server uses our connections
 * @author Gregor
 *
 */
public class ConnectionFactory implements Server.ConnectionFactory {

	@Override
	public Connection createNew(SocketChannel c, Server s) {
		return new ChatConnection(c, s);
	}

}
