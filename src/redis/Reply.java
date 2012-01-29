package redis;


import java.util.List;
import java.util.LinkedList;

import primitive.collection.ByteList;

import static redis.Utils.numeric;
import static redis.Constants.*;



public class Reply {
  enum Type {
    MULTI,
    BULK,
    INT,
    STATUS,
    ERROR
  }

  static Reply makeReply(byte indicator) {
    switch (indicator) {
      case ASTERISK:
        return new MultiBulkReply();
      case DOLLAR:
        return new BulkReply();
      case COLON:
        return new IntegerReply();
      case PLUS:
        return new StatusReply();
      case MINUS:
        return new ErrorReply();
      default:
        throw new Protocol.ProtocolException("unexpected: "+(char)indicator);
    }
  }

  Type type;

  /* indicate next arg in req or multibulk*/
  void next(){}

  /* collect the next byte, internal to sm */
  void set(byte b){}

  boolean isBulk() {
    return false;
  }
  boolean isMultibulk() {
    return false;
  }

  static class StringReply extends Reply {
    StringBuffer buf = new StringBuffer();
    public void set(byte b) {
      buf.append((char)b);
    }
    public String getMessage() {
      return buf.toString();
    }

    public String toString() {
      return this.getClass()+" : "+this.getMessage();
    }
  }
  /** e.g. +OK */
  static class StatusReply extends StringReply {
    StatusReply() {
      this.type = Type.STATUS;
    }
  }

  /** e.g. -Some error */
  static class ErrorReply extends StringReply {
    ErrorReply () {
      this.type = Type.ERROR;
    }
  }

  /** e.g. :1000 */
  static class IntegerReply extends Reply {
    int value;
    IntegerReply() {
      this.type = Type.INT;
    }
    public void set(byte b){
      if (!numeric(b)) {
        throw new Protocol.ProtocolException("not numeric");
      }
      value *= 10;
      value += b - 0x30;
    }
    public int getValue() {
      return value;
    }

    public String toString () {
      return this.getClass()+" : "+this.getValue();
    }
  }
  static class BulkReply extends Reply {
    ByteList bytes;
    BulkReply() {
      this.type = Type.BULK;
    }
    public void set(byte b) {
      this.bytes = bytes == null ? new ByteList(32) : this.bytes;
      this.bytes.add(b);
    }
    public boolean isBulk() {
      return true;
    }
    public byte[] getValue() {
      return this.bytes.toArray();
    }
    public String toString() {
      return new String(this.getValue());
    }
  }
  static class MultiBulkReply extends BulkReply {
    
    List<byte[]> byteList;
    MultiBulkReply () {
      this.type = Type.MULTI;
    }
    public void next(){
      this.byteList = null == this.byteList ? new LinkedList<byte[]>() : this.byteList;
      this.byteList.add(super.getValue());
      this.bytes.clear();
    }
    public List<byte[]> getEntries() {
      return this.byteList;
    }
    public String toString() {
      if (null == this.byteList) {
        return "";
      }
      StringBuilder buf = new StringBuilder();
      for (byte[] bs : this.byteList){
        if (0 != buf.length()) {
          buf.append(':');
        }
        buf.append(new String(bs));
      }
      return buf.toString();
    }
    public boolean isMultibulk() {
      return true;
    }
  }
}
