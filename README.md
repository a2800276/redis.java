# redis protocol handling statemachine

This is a simple statemachine to handle the redis protocol. It may one
day grow up to be a part of something larger. Until then, here it is.

## Usage

### dissecting incoming bytes

FWIW at the present time, Requests and Replies are the same thing.
On the wire `Requests` are identical to `MultiBulkReply`s so at this
point in time, they're handled by the same class.

To use the library you need to create an instance of `Protocol.CB` that
implements a `void cb(Reply reply)` callback method which the state
machine calls every time a complete message has been received:

```java
  Protocol.CB cb = new Protocol.CB() {
    public void cb (Reply r) {
      System.out.println(r);
    }
  }
```

Next instatiate `Protocol`, passing in the callback and --optionally--
whether you're handling Requests or Replies:

```java
  Protocol p = new Protocol(RequestReply.REQ, cb);
```

Finally, whenever you receive bytes from wherever you get them from,
call the `handleBytes()` method of protocol:

```java
  byte [] bs; (...)
  while (getMoreBytes(bs)) {
    p.handleBytes(bs);
  }
```

alternatively, in case you're dealing with nio, just pass in a
ByteBuffer:

```java
  ByteBuffer buf = (... at your discretion ...)
  p.handleBytes(buf);
```

### 'Your callback method suck!`

The callback described above is more of a hello world than anything
else. It describes the minimal implementation you need to do. In order
to do anything useful you'll need to write callbacks for each of the 5
reply types:

```java
  Protocol.CB cb = new Protocol.CB() {
    public void multibulk (Reply.MultiBulkReply r) {
      System.out.println(r.getEntries());
    }
    public void bulk (Reply.BulkReply r) {
      System.out.println(r.getValue());
    }
    public void integer (Reply.IntegerReply r) {
      System.out.println(r.getValue());
    }
    public void status (Reply.StatusReply r) {
      System.out.println(r.getMessage());
    }
    public void error (Reply.ErrorReply r) {
      System.out.println(r.getMessage());
    }
  }
```




## getting started

`git submodule init`
`git submodule update`

## LICENSE

You should probably not be using this in the current state.
In case you do:

Copyright (C) 2012 Tim Becker

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
