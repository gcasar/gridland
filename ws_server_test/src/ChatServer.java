import si.evil.server.Server;

/**
 * Test for WServer, echoes recived messages to all connections
 * @author Gregor
 *
 */
public class ChatServer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//Supply our own factory
		Server server = new Server(new ConnectionFactory());
		if(server.init())
			server.serve();
		else
			System.out.println("There was an error while initialising.");
	}

}
