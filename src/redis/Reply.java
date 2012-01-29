package redis;

import static redis.Utils.numeric;
import static redis.Constants.*;

public class Reply {
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
  /* indicate next arg in req or multibulk*/
  void next(){}
  void set(byte b){

  }
  public boolean isBulk() {
    return false;
  }
  public boolean isMultibulk() {
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
  }
  static class StatusReply extends StringReply {}
  static class ErrorReply extends StringReply {}
  static class IntegerReply extends Reply {
    int value;
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
  }
  static class BulkReply extends Reply {
    public boolean isBulk() {
      return true;
    }
  }
  static class MultiBulkReply extends Reply {
    public boolean isMultibulk() {
      return true;
    }
  }
}
