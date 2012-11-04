import java.nio.channels.SocketChannel;

import si.evil.server.Connection;
import si.evil.server.WServer;


/**
 * We have to create our own factory so the server uses our connections
 * @author Gregor
 *
 */
public class ConnectionFactory implements WServer.ConnectionFactory {

	@Override
	public Connection createNew(SocketChannel c, WServer s) {
		return new ChatConnection(c, s);
	}

}
