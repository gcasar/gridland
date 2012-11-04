import si.evil.server.WServer;

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
		WServer server = new WServer(new ConnectionFactory());
		if(server.init())
			server.serve();
		else
			System.out.println("There was an error while initialising.");
	}

}
