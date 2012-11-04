package si.evil.server;

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Immutable object. <br>
 * Contains Bytes and String representation of data payload
 * it is required that both representations are present, but 
 * it may happen that only one is initialized if UnsupportedEncodingException
 * happens.
 * @author Gregor
 *
 */
public final class Buffer {
	public static Logger log = Logger.getLogger("WServer");
	/**
	 * In Bytes
	 */
	public final int MAX_DATA_SIZE = 1500;
	public final byte [] bytes;
	public final String string;
	
	/**
	 * Generates message from data
	 * encoding is ASCII
	 * @param buffer must have appropriate position and limit
	 * @throws UnsupportedEncodingException 
	 */
	public Buffer(ByteBuffer buffer) throws UnsupportedEncodingException{
		int size = buffer.limit()-buffer.position();
		bytes = new byte[size];
		buffer.get(bytes,0,size);
		String tmp;
		try {
			tmp = new String(bytes,0,size,"ASCII");
		} catch (UnsupportedEncodingException e) {
			tmp = "";
			throw e;
		}
		string = tmp;
	}
	
	/**
	 * Generates message from bytes<br>
	 * Encoding is ASCII
	 * @param bytes
	 * @throws UnsupportedEncodingException
	 */
	public Buffer(byte[] bytes) throws UnsupportedEncodingException{
		this.bytes = bytes;
		String tmp;
		try {
			tmp = new String(bytes,0,bytes.length,"ASCII");
		} catch (UnsupportedEncodingException e) {
			tmp = "";
			throw e;
		}
		string = tmp;
	}
	
	/**
	 * Generates bytebuffer from message
	 * encoding is ASCII
	 * @param message
	 * @throws UnsupportedEncodingException 
	 */
	public Buffer(String message) throws UnsupportedEncodingException{
		this.string = message;
		byte[] tmp;
		try {
			tmp = this.string.getBytes("ASCII");
		} catch (UnsupportedEncodingException e) {
			tmp = new byte[1];
			tmp[0] = 0;
			throw e;
		}
		this.bytes = tmp;
	}
	
	/**
	 * Shallow copy
	 * @param b
	 */
	public Buffer(Buffer b){
		this.bytes = b.bytes;
		this.string = b.string;
	}
	
	/**
	 * Dummy constructor
	 */
	public Buffer(){
		this.bytes = new byte[1];
		this.bytes[0] = 0;
		this.string = "";
	}
}
