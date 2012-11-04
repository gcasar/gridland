package si.evil.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.Inflater;


/**
 * Web server
 * @author Gregor 
 */
public class WServer {
	/**
	 * Implement to create custom connections
	 * @author Gregor
	 *
	 */
	public static interface ConnectionFactory{
		Connection createNew(SocketChannel c, WServer s);
	}
	
	/**
	 * Default factory
	 * @author Gregor
	 *
	 */
	public static class DefaultFactory implements ConnectionFactory{

		@Override
		public Connection createNew(SocketChannel c, WServer s) {
			return new Connection(c,s);
		}
		
	}
	
	public static final int PORT = 10101;
	
	/**
	 * In Bytes
	 */
	public static final int BUFFER_SIZE = 1500;
	
	/**
	 * Default interval manager
	 * will be given to serve Connection ticks 
	 * and time will be set to GCD of all connections
	 */
	IntervalManager interval_manager = null;
	
	/**
	 * Creates new connection classes
	 */
	ConnectionFactory factory = null;
	
	/**
	 * Selector for SocketChannels (sockets)
	 */
	Selector selector = null;
	
	/**
	 * Main channel for accepting connections
	 */
	ServerSocketChannel serverChannel;
	
	/**
	 * Buffer for sending and receiving
	 */
	ByteBuffer buffer;
	
	/**
	 * All active connections
	 */
	HashMap<SocketChannel, Connection> connections = new HashMap<SocketChannel,Connection>();
	
	static Logger log = Logger.getLogger("WServer");
	
	public WServer(){
		buffer = ByteBuffer.allocate(BUFFER_SIZE);
	}
	
	public WServer(ConnectionFactory factory){
		buffer = ByteBuffer.allocate(BUFFER_SIZE);
		this.factory = factory;
	}
	
	public boolean init(){
		try {
	    	selector = Selector.open();
	    	
		    serverChannel = ServerSocketChannel.open();

		    serverChannel .configureBlocking(false);
		    serverChannel.socket().bind(new InetSocketAddress("0.0.0.0",PORT));
		    //Only register OP_ACCEPT so selector.select() actually blocks
		    serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		    return true;
		} catch (IOException e) {
			return false;
		}	
	}
	
	/**
	 * Gives the server a chance to limit the number of connections, IPs ...
	 * 
	 * Override to handle.<br>
	 * 
	 * This implementation always returns true.
	 * @param channel
	 * @return
	 */
	public boolean handleTCPAccept(SocketChannel channel){
		return true;
	}
	
	public void handleRecv(Buffer data, Connection c){
		if(c.connected){
			c.onRawRecv(data);
		}else{
			c.onFirstRecv(data);
		}
	}
	
	public void handleDsc(Connection c){
		log.log(Level.INFO,"Connection closed");
		c.onDsc();
	}
	
	public void brodcastMessage(Buffer msg){
		for(Connection c: connections.values()){
			c.sendMessage(msg);
		}
	}
	
	public void setFactory(ConnectionFactory f){
		this.factory = f;
	}
		
	public void serve(){
		System.out.println("Waiting for events.");	
		try{
			while (true) {
		        selector.select();
		        for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext();) { 
					SelectionKey key = i.next(); 
					i.remove(); 
					
					//Skip invalid
					if(!key.isValid())continue;
					
					if (key.isConnectable()) { 
						((SocketChannel)key.channel()).finishConnect();
						log.log(Level.FINER,"Connection finished");
					} 
					
					if (key.isAcceptable()) { 
						// accept connection 
						SocketChannel client = serverChannel.accept(); 
						if(handleTCPAccept(client)!=true){
							client.close();
							log.log(Level.FINER,"Connection refused");
						}else{
							client.configureBlocking(false); 
							client.socket().setTcpNoDelay(true); 
							client.register(selector, SelectionKey.OP_READ);
							
							//Add the client to our HashMap
							connections.put(client, factory.createNew(client,this));
							log.log(Level.FINER,"Connection opened");
						}
					} 
					
					if (key.isReadable()) { 
						SocketChannel client = ((SocketChannel)key.channel());
						Connection connection = connections.get(client);
						
						//Reset buffer position and limit
						buffer.position(0);
						buffer.limit(BUFFER_SIZE);
						int numBytes = client.read(buffer);
						if(numBytes!=-1){
							buffer.flip();
							Buffer dataBuffer = new Buffer(buffer);
							log.log(Level.FINER,"onRecv()");
							
							if(connection==null){
								//Should not happen
								log.log(Level.WARNING,"Unconnected client event");
							}else{
								handleRecv(dataBuffer, connection);
							}
						}else{
							//Connection closed
							if(connection==null){
								//Should not happen
								log.log(Level.WARNING,"Unconnected client event");
							}else{
								connections.remove(client);
								client.close();
								handleDsc(connection);
							}
						}
					} 
				}
		    }
		}catch (Throwable e) { 
			throw new RuntimeException("Server failure: "+e.getMessage());
		} finally {
			try {
				selector.close();
				serverChannel.socket().close();
				serverChannel.close();
			} catch (Exception e) {
				// Server failed on cleanup
			}
		}
	}


}
