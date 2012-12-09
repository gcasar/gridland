package si.evil.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jws.Oneway;

/**
 * Abstraction for various connection methods.
 * Can handle WebSocket and TCP connections
 * <br>
 * Extend this class and override on* methods but call their supers!
 * 
 * TODO: Comet via HTTP, perhaps ActionScript 3. Maybe include SGP?
 * @author Gregor
 *
 */
public class Connection {
	protected static Logger log = Logger.getLogger("WServer");
	
	protected static ByteBuffer buffer = ByteBuffer.allocate(9000);
	
	private static int num_connections = 0;
	
	public enum Type{
		/**
		 * Connection is established via simple TCP stream
		 */
		TCP,
		/**
		 * Connection is established via TCP but is using
		 * WebSocket 13 handshake
		 */
		WebSocket,
		/**
		 * TCP but uses ActionScript 3 handshake
		 */
		AS3Socket,
		/**
		 * Connection uses HTTP GET Ajax long-polling
		 */
		Comet,
		/**
		 * UDP based simple game protocol
		 */
		SGP
	}
	
	/**
	 * A connection can be created but not
	 * connected. 
	 * <br>Connected must be set to true
	 * only if onAccept has returned true.
	 */
	protected boolean connected = false;
	
	/**
	 * WServer that this connection belongs to
	 */
	protected Server manager = null;
	
	/**
	 * Unique connection identifier
	 */
	protected int id = 0;
	
	/**
	 * Optional (only initialized when this.type==Type.WebSocket).<br>
	 * Contains key:value pairs for WS handshake headers. Only lower case
	 */
	protected HashMap<String, String> wsHeaders = null;
	
	/**
	 * Optional (only initialized when this.type==Type.WebSocket).<br>
	 * Path requested on WebSocket handshake.
	 */
	protected String wsPath = null;
	
	/**
	 * Type of underlying connection.
	 */
	protected Type type = Type.TCP;
	
	protected SocketChannel channel = null;
	
	public Connection(SocketChannel c, Server server){
		connected = false;
		this.channel = c;
		//set and increment
		this.id = Connection.num_connections++;
		this.manager = server;
	}
	
	/**
	 * Closes the TCP connection. <br>
	 * Also calls wsClose if type == Type.WS;
	 */
	public void close(){
		try {
			channel.socket().close();
		} catch (IOException e) {
			log.log(Level.WARNING,""+id+": Failed to close channel socket");
		}
	}
	
	
	
	/**
	 * Called when actual payload is received.
	 * It is contained in a string for convenience
	 * @param msg
	 */
	public void onRecv(Buffer data){
		int i=0; 
		i++;
	}
	
	/**
	 * Parses the packet based on this.type
	 * @param data
	 */
	void onRawRecv(Buffer data){
		if(type==Type.WebSocket){
			Buffer tmp = WebSocket.parseFrame(data);
			if(tmp == WebSocket.FRAME_FIN){
				onDsc();
			}else{
				onRecv(tmp);
			}
		}else{
			onRecv(data);
		}
	}
	
	/**
	 * Closes connection
	 */
	void onDsc(){
		log.log(Level.INFO, ""+id+": Connection closed");
		this.connected = false;
		this.close();
	}
	
	/**
	 * Checks for connection type and frames accordingly (if needed)
	 * @param msg
	 */
	public boolean sendMessage(Buffer msg){
		if(this.type==Type.WebSocket){
			msg = WebSocket.createFrame(msg);
			if(msg==WebSocket.FRAME_ERROR){
				return false;
			}
		}
		return rawSendMessage(msg);
	}
	
	/**
	 * Does not check for connection types but only sends the given data
	 * @param msg
	 * @return
	 */
	public boolean rawSendMessage(Buffer msg){
		buffer.flip();
		buffer.limit(msg.bytes.length);
		buffer.put(msg.bytes);
		buffer.flip();
		try {
			this.channel.write(buffer);
			return true;
		} catch (IOException e) {
			log.log(Level.WARNING,""+id+": Failed to send message");
			return false;
		}
	}
	
	/**
	 * Gives the connection a chance to detect and handle any higher protocols.
	 * This implementation calls {@link wsCheck}
	 * @return true if a higher-than-TCP protocol was detected, see {@link Type}
	 */
	protected boolean onFirstRecv(Buffer data){
		boolean found = false;
		if(wsCheckHandshake(data)){
			found = true;
			this.type = Type.WebSocket;
		}else{
			//Tcp only delivers the payload so we just pass it on
			this.type = Type.TCP;
			this.onRawRecv(data);
		}
		
		this.connected = true;
		
		return found;
	}
	
	/**
	 * Checks if data contains a WS 13 request and parses it.<br>
	 * Also calls onWsConnected to handle accepting or rejecting this connection.<br>
	 * @param data
	 * @return true if this connection is a websocket connection
	 */
	protected boolean wsCheckHandshake(Buffer data){
		String request = data.string;
		//System.out.println(request);
		//Try parsing WS 13 headers
		String [] lines = request.split("\\r?\\n");
		
		//Parse first HTTP line (GET path HttpVersion)
		String parts[]	= lines[0].split(" ");
		if(parts.length<3){
			//Bad header
			return false;
		}
		
		String path = parts[1];		
		
		//init to number of lines -1 (first line is GET)
		//header keys are all lover case
		HashMap<String, String> headers = new HashMap<String, String>(lines.length-1);
		//traverse all but the first line
		for(int i=lines.length-1; i>0; --i){
			parts = lines[i].split(":");
			//skip bad lines?
			if(parts.length!=2)
				continue;
			headers.put(parts[0].toLowerCase(), parts[1]);
		}
		
		this.wsPath = path;
		this.wsHeaders = headers; 
		
		onWsConnected(path,headers);
        
        return true;
	}
	
	/**
	 * Override to handle. Call super to get default response headers.
	 * 
	 * Calls wsRespond if connection is accepted and returns true or
	 * returns false and closes the connection.
	 * 
	 * This implementation only calls wsRespond(true,  new HashMap<String,String>())
	 * and passes its return value
	 * 
	 * @param path same as this.wsPath
	 * @param headers sane as this.wsHeaders
	 * @return true if connection is accepted
	 */
	boolean onWsConnected(String path, HashMap<String,String> headers){
		boolean result =  wsSendHandshakeResponse(true, new HashMap<String,String>());
		if(result){
			connected = true;
			return true;
		}else{
			connected = false;
			return false;
		}
	}
	
	/**
	 * Generates and sends the response message
	 * TODO: append custom_headers
	 * @param accept true if connection should be accepted
	 * @param headers custom response headers. Can override some default headers.
	 *         Keys must be lower case.
	 * @return true on success
	 */
	protected boolean wsSendHandshakeResponse(boolean accept, HashMap<String,String> custom_headers){
		//Generate response
		String lineSeparator = "\r\n";
		Buffer response = null;
		if(accept==true){
			String key = wsHeaders.get("sec-websocket-key");
			String origin = wsHeaders.get("origin");
			String protocol = wsHeaders.get("sec-websocket-protocol");
			
			if(key==null){
				return false;
			}
			
			key = WebSocket.generateAcceptString(key);			
			
			StringBuilder sb = new StringBuilder();
	       
	        sb.append("HTTP/1.1 101 Switching Protocols").append(lineSeparator);
	        sb.append("Upgrade: websocket").append(lineSeparator);
	        sb.append("Connection: Upgrade").append(lineSeparator);
	       
	        sb.append("Sec-WebSocket-Accept: ").append(key).append(lineSeparator);
	        if(protocol!=null)
	            sb.append("Sec-WebSocket-Protocol: ").append(protocol).append(lineSeparator);
	
	        
	        sb.append(lineSeparator);
	
	        try {
				response = new Buffer(sb.toString());
			} catch (UnsupportedEncodingException e) {
				log.log(Level.SEVERE, e.getMessage());
			}
	        
	        //System.out.println(response.string);
		}else{
			StringBuilder sb = new StringBuilder();
			sb.append("HTTP/1.1 101 404 Not Found").append(lineSeparator);
			sb.append(lineSeparator);
			try {
				response = new Buffer(sb.toString());
			} catch (UnsupportedEncodingException e) {
				log.log(Level.SEVERE, e.getMessage());
			}
		}
        
        return sendMessage(response);
	}
	
	public SocketChannel getChannel(){
		return channel;
	}
	
	public boolean isConnected(){
		return connected;
	}
	
	public int getConnectionId(){
		return id;
	}
}
