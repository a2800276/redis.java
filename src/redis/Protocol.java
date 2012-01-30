package redis;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static redis.Constants.*;
import static redis.Utils.check;
import static redis.Utils.numeric;

public class Protocol {

  public enum RequestReply {
    REQ,
    REPLY,
    EITHER
  }
  enum STATE {
     INITIAL
    ,NUMARGS
    ,LENGTH
    ,LEN_NULL
    ,LEN_NULL_CR
    ,COLLECT
    ,LF
    ,LF_DOLLAR
    ,DOLLAR
    ,LF_BULK
    ,BULK
    ,LF_BULK_END
  }

  public static class ProtocolException extends RuntimeException{
    ProtocolException(String mes) {super(mes);}
  }

  public abstract static class CB {
    public abstract void cb(Reply reply); 
    public void multibulk (Reply.MultiBulkReply mbr) { cb(mbr); }
    public void bulk (Reply.BulkReply br) { cb(br); }
    public void integer (Reply.IntegerReply ir) { cb(ir); }
    public void status (Reply.StatusReply sr) {cb(sr); }
    public void error (Reply.ErrorReply er) {cb(er); }
  }

  STATE state = STATE.INITIAL;
  RequestReply rr = RequestReply.EITHER;
  Reply reply;
  int numArgs;
  int length;
  CB cb;

  public Protocol(CB cb) {
    this.cb = cb;
  }
  public Protocol (RequestReply r, CB cb) {
    this(cb);
    this.rr = r;
  }

  public void handleBytes (byte [] arr) {
    handleBytes(ByteBuffer.wrap(arr));    
  }
  public void handleBytes (byte [] arr, int offset, int len) {
    handleBytes(ByteBuffer.wrap(arr, offset, len));    
  }
  public void handleBytes (ByteBuffer buf) {
    while (0 != buf.remaining()) {
      byte b = buf.get();
      switch (this.state) {
        case INITIAL:
          if (RequestReply.REQ == this.rr) {
            check(b, ASTERISK);
          } else if (RequestReply.REPLY == this.rr) {
            check(b, REPLY);
          } else {
            check(b, REPLY);
            if (ASTERISK != b) {
              this.rr = RequestReply.REPLY;
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
          cb();
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
          } else if ( MINUS == b && 0 == this.length) { 
            this.state = STATE.LEN_NULL;
            this.length = -1;
          } else {
            check(b, CR);
            this.state = STATE.LF_BULK;
          }
          break;

        case LEN_NULL:
          check(b, (byte)'1');
          ((Reply.BulkReply)reply).isNull = true;
          this.reply.next();
          this.state = STATE.LEN_NULL_CR;
          break;

        case LEN_NULL_CR:
          check(b, CR);
          this.state = STATE.LF_BULK_END;
          break;

        case LF_BULK:
          check(b, LF);
          this.state = STATE.BULK;
          break;

        case BULK:
          assert(-2 < this.length);
           
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
            cb();
            this.state = STATE.INITIAL;
          } else {
            this.state = STATE.DOLLAR;
          }
          break;
        
        default:
          throw new ProtocolException("unknown state:"+this.state);
          
      }
    } // while
  }

  void cb () {
    switch (this.reply.type) {
      case MULTI:
        this.cb.multibulk((Reply.MultiBulkReply)this.reply);
        break;
      case BULK:
        this.cb.bulk((Reply.BulkReply)this.reply);
        break;
      case INT:
        this.cb.integer((Reply.IntegerReply)this.reply);
        break;
      case STATUS:
        this.cb.status((Reply.StatusReply)this.reply);
        break;
      case ERROR:
        this.cb.error((Reply.ErrorReply)this.reply);
        break;
      default:
        throw new ProtocolException("unknown reply type");
    }
  }
  public static void main (String [] args) {

    byte [] bs = "*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n".getBytes();
    byte [] i  = ":1000\r\n".getBytes();
    byte [] ok  = "+OK\r\n".getBytes();
    byte [] nok  = "-NOK\r\n".getBytes();
    byte [] blk = "$4\r\nTEST\r\n".getBytes();
    byte [] nul = "$-1\r\n".getBytes();
    byte [] mbn = "*3\r\n$-1\r\n$3\r\nONE\r\n$3\r\nTWO\r\n".getBytes();

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
    p.handleBytes(nul);
    p.handleBytes(mbn);

  }

  static void p (Object o) {
    System.out.println(o);
  }
  
}
