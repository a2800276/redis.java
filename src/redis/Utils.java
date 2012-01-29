package redis;

import java.util.Arrays;

public class Utils {
  static boolean numeric (byte b) {
    return (0x2F < b) && ( b < 0x3A);
  }
  
  /**
    Note: `should` must be sorted!
  */
  static void check (byte is, byte[] should) {
    if (0 > Arrays.binarySearch(should, is)) {
      throw new Protocol.ProtocolException("unexpected byte is: "+is+"("+((char)is)+")");
    } 
  }
  static void check (byte is, byte should) {
    if (is != should) {
      throw new Protocol.ProtocolException("byte is: "+is+"("+((char)is)+") should be:"+should+"("+((char)should)+")");
    }
  }
}
