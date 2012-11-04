package si.evil.server;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.print.attribute.standard.Finishings;


/**
 * WebSocket utility methods for WebSocket v13<br>
 * implemented based on http://tools.ietf.org/html/rfc6455
 * @author Gregor
 *
 */
public class WebSocket {	
	static Logger log = Logger.getLogger("WServer");
	static byte[] mask = new byte[4];
	
	/**
	 * Returned by parseFrame on FRAME_FIN event
	 */
	static final Buffer FRAME_FIN = new Buffer();
	
	/**
	 * Returned by parseFrame on Error
	 */
	static final Buffer FRAME_ERROR = new Buffer();
	
	/**
	 * Returned by parseFrame on unimplemented
	 */
	static final Buffer FRAME_UNSUPPORTED = new Buffer();
	
	public static String generateAcceptString(String key){
    	//WebSock specification
    	String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    	key = key.replaceAll("\\s","");
    	key = key.concat(magic);
    	
    	MessageDigest md = null;
        try {
        	md = MessageDigest.getInstance("SHA-1");
            md.reset();
        } catch (NoSuchAlgorithmException ex) {
            log.log(Level.SEVERE, "SHA-1 Algorithm not found", ex);
        } 
        byte[] result;
		try {
			result = md.digest(key.getBytes("ASCII"));
		} catch (UnsupportedEncodingException e) {
			log.log(Level.SEVERE, "Failed to handshake: ASCII encoding not avaliable");
			return "";
		}        
        return Base64.encodeBytes(result);
    }
	
	/**
	 * Based on  <a href="http://tools.ietf.org/html/rfc6455#section-5.2">rfc6455</a>
	 * 
	 * Simple implementation.<br>
	 * 
	 * Supports text data only and fails if MASK is set to false
	 * Does not support Ping nor Pong or detection of FIN frames<br>
	 * Does not support streaming<br>
	 * 
	 * Supports frame sizes to at max 2^(24) (8MiB)<br>
	 * 
	 * TODO: Test data framing of more than 65KiB
	 * @param data
	 * @return null on failure or parsed (unmasked) frame
	 */
	public static Buffer parseFrame(Buffer data){
		Buffer result;
		//get opcode (4 lsb of the first Byte)
		int opcode = data.bytes[0]&0x0f;
		
		//get mask bit (1 msb of second Byte)
		boolean isMasked = ((data.bytes[1]>>7)&0x01)==0x01;
		
		//Fin packet
		if(opcode==0x08){
			return FRAME_FIN;
		}
		
		//fail if the packet is not masked or if opcode is not 0x1 (text)
		if(isMasked&&opcode==0x01){
			
			
			//Offset to mask in Bytes
			int offset = 2;
			
			//Get size, first 7lsb of the second Byte
			int size = data.bytes[1]&0x7f;
			//if payload size can not fit in 7 bytes
			if(size==0x7E){//126 as unisgned 7 bits
				//size is contained in bytes 2 and 3 (16bit unsigned int)
				size = ((data.bytes[2]<<8)& 0xFF|data.bytes[3]& 0xFF);
				offset = 4;
			}else if(size==0x7f){//127 as unsigned 7 bits
				//size is contained in bytes 2 to 9 (64bit unsigned int)
				//but ignore larger packets because java and unsigned and shit.
				//this only checks the 24 lsb bits
				size = 	(data.bytes[7]<<16)& 0xFF|(data.bytes[8]<<8)& 0xFF|(data.bytes[9]& 0xFF);
				offset = 10;
			}
			
			//Copy mask
			mask[0] = data.bytes[offset+0];
			mask[1] = data.bytes[offset+1];
			mask[2] = data.bytes[offset+2];
			mask[3] = data.bytes[offset+3];
			//set offset to offset to payload
			offset+=4;
			
			//Unmask bits
			byte[] bytes = new byte[size];
			for(int i=0; i<size;i++){
				bytes[i] = (byte) (data.bytes[i+offset]^mask[i%4]);
			}
			
			//There you have it!
			
			try {
				result = new Buffer(bytes);
			} catch (UnsupportedEncodingException e) {
				log.log(Level.SEVERE,e.getMessage());
				return FRAME_ERROR;
			}
		}else{
			return FRAME_UNSUPPORTED;
		}
		//Debug
		//System.out.println(Base64.encodeBytes(data.bytes));
		return result;
	}

	/**
	 * Based on <a href="http://tools.ietf.org/html/rfc6455#section-5.2">rfc6455</a>
	 * 
	 * Simple implementation.<br>
	 * 
	 * Does not mask.<br>
	 * 
	 * Only supports text frames (opcode=0x01)<br>
	 * 
	 * Supports data up to 2^24 (8MiB) per frame<br>
	 * 
	 * TODO: Test data framing of more than 65KiB
	 * 
	 * @param data
	 * @return
	 */
	public static Buffer createFrame(Buffer data){
		//determine size
		int size = data.bytes.length;
		//Smallest possible frame is 2Bytes
		int frame_size = 2;
		
		//offset to payload
		int offset = 2;
		
		Buffer result;
		byte [] bytes;
		if(size<126){
			frame_size+=size;
			bytes = new byte[frame_size];
			//Masked is automatically set to 0
			bytes[1] = (byte) size;
			offset = 2;
		}else if(size<65536){
			frame_size+=2+size;
			bytes = new byte[frame_size];
			//Masked is automatically set to 0
			bytes[1] = (byte) 126;
			bytes[2] = (byte) ((size>>8) & 0xFF);
			bytes[3] = (byte) ((size>>0) & 0xFF);
			offset = 4;
		}else{//NOT TESTED
			frame_size+=8+size;
			bytes = new byte[frame_size];
			//Masked is automatically set to 0
			bytes[1] = (byte) 127;
			//Only 24 lsb are uses (8MiB)
			bytes[2] = 0;
			bytes[3] = 0;
			bytes[4] = 0;
			bytes[5] = 0;
			bytes[6] = 0;
			bytes[7] = (byte) ((size>>16) & 0xFF);
			bytes[8] = (byte) ((size>>8) & 0xFF);
			bytes[9] = (byte) ((size>>0) & 0xFF);
			
			offset = 10;
		}
		
		//opcode = 1; + fin flag
		bytes[0] = (byte) 0x81;
		
		//Copy data
		for(int i=0; i<data.bytes.length; i++){
			bytes[offset+i] = data.bytes[i];
		}
		
		try {
			result = new Buffer(bytes);
		} catch (UnsupportedEncodingException e) {
			result =  FRAME_ERROR;
		}
		
		return result;
	}
	
	/**
	 * Not to be confused with FRAME_FIN witch is only a constant.<br>
	 * This generates an actual frame of size 2B<br>
	 * 
	 * Based on <a href="http://tools.ietf.org/html/rfc6455#section-5.2">rfc6455</a>
	 * 
	 * @return generated fin frame
	 */
	public static Buffer createFinFrame(){
		Buffer result;
		byte[] bytes = new byte[2];
		bytes[0] = (byte) 0x88;//FIN flag + OPCODE close packet
		bytes[1] = 0;//size of zero, mask is set to false
		
		try {
			result = new Buffer(bytes);
		} catch (UnsupportedEncodingException e) {
			result =  FRAME_ERROR;
		}
		
		return result;
	}
}
