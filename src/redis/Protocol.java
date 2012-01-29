package redis;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static redis.Constants.*;
import static redis.Utils.check;
import static redis.Utils.numeric;

public class Protocol {

  enum RequestResponse {
    REQ,
    RES,
    EITHER
  }
  enum STATE {
     INITIAL
    ,NUMARGS
    ,LENGTH
    ,COLLECT
    ,LF
    ,LF_DOLLAR
    ,DOLLAR
    ,LF_BULK
    ,BULK
    ,LF_BULK_END
  }
  static class ProtocolException extends RuntimeException{
    ProtocolException(String mes) {super(mes);}
  }

  abstract static class CB {
    abstract void cb(Reply reply); 
  }

  STATE state = STATE.INITIAL;
  RequestResponse rr = RequestResponse.EITHER;
  Reply reply;
  int numArgs;
  int length;
  CB cb;

  public Protocol(CB cb) {
    this.cb = cb;
  }
  public Protocol (RequestResponse r, CB cb) {
    this(cb);
    this.rr = r;
  }

  public void handleBytes (byte [] arr) {
    handleBytes(ByteBuffer.wrap(arr));    
  }
  public void handleBytes (ByteBuffer buf) {
    while (0 != buf.remaining()) {

    
      byte b = buf.get();
      switch (this.state) {
        case INITIAL:
          if (RequestResponse.REQ == this.rr) {
            check(b, ASTERISK);
          } else if (RequestResponse.RES == this.rr) {
            check(b, RES);
          } else {
            check(b, RES);
            if (ASTERISK != b) {
              this.rr = RequestResponse.RES;
            }
          }
          
          this.reply = Reply.makeReply(b);
          if (this.reply.isMultibulk()) {
            this.state = STATE.NUMARGS;
            this.numArgs = 0;
          } else if (this.reply.isBulk()) {
            this.state = STATE.LENGTH;
            this.numArgs = 1;
            this.length = 0;
          } else {
            this.state = STATE.COLLECT;
          }
          break;

        case COLLECT:
          if (CR != b) {
            this.reply.set(b);
          } else {
            this.state = STATE.LF;
          }
          break;

        case LF:
          check(b, LF);
          cb.cb(this.reply);
          this.state = STATE.INITIAL;
          break;

        case NUMARGS:
          if (numeric(b)) {
            this.numArgs *= 10;
            this.numArgs += b - 0x30;
          } else {
            check(b, CR);
            this.state = STATE.LF_DOLLAR;
          }
          break;

        case LF_DOLLAR:
          check(b, LF);
          this.state = STATE.DOLLAR;
          break;

        case DOLLAR:
          assert (0 != this.numArgs);
          check(b, DOLLAR);
          this.state = STATE.LENGTH;
          this.length = 0;
          break;

        case LENGTH:
          if (numeric(b)) {
            this.length *= 10;
            this.length += b - 0x30;
          } else {
            check(b, CR);
            this.state = STATE.LF_BULK;
          }
          break;

        case LF_BULK:
          check(b, LF);
          this.state = STATE.BULK;
          break;

        case BULK:
          assert(-1 < this.length);

          if (0 == this.length) {
            check(b, CR);
            this.reply.next();
            this.state = STATE.LF_BULK_END;
          } else {
            this.reply.set(b);
          }
          --this.length;
          break;

        case LF_BULK_END:
          check(b, LF);
          if (0 == --this.numArgs) {
            cb.cb(this.reply);
            this.state = STATE.INITIAL;
          } else {
            this.state = STATE.DOLLAR;
          }
          break;
        
        default:
          throw new ProtocolException("unknown state:"+this.state);
          
      }
    }
  }
  public static void main (String [] args) {
    byte [] bs = "*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n".getBytes();
    byte [] i  = ":1000\r\n".getBytes();
    byte [] ok  = "+OK\r\n".getBytes();
    byte [] nok  = "-NOK\r\n".getBytes();
    byte [] blk = "$4\r\nTEST\r\n".getBytes();
    CB cb = new CB () {
      public void cb (Reply r) {
        p(r);
      }
    };
    Protocol p = new Protocol(cb);
    p.handleBytes(bs);
    p.handleBytes(i);
    p.handleBytes(ok);
    p.handleBytes(nok);
    p.handleBytes(blk);
  }

  static void p (Object o) {
    System.out.println(o);
  }
  
}
