## 1. Three Ways to Send a Login Message
 
### Code

```java 
  class LoginRequest implements Serializable {
    String username;
    String password;

    LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
public class Client {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 5050);

        LoginRequest loginRequest = new LoginRequest("user1", "pass123");
        // === Method 1: Plain String ===
        PrintWriter stringOut = new PrintWriter(socket.getOutputStream(), true);
        stringOut.println("LOGIN|" + loginRequest.username + "|" + loginRequest.password);

        // === Method 2: Serialized Object ===
        ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
        objectOut.writeObject(loginRequest);

        // === Method 3: JSON ===
        Gson gson = new Gson();
        String json = gson.toJson(loginRequest);
        PrintWriter jsonOut = new PrintWriter(socket.getOutputStream(), true);
        jsonOut.println(json);

        socket.close();
    }
}
```

### Questions:


### Method 1: Plain String Format

### 1. What are the pros and cons of using a plain string like `"LOGIN|user|pass"`?
#### Pros of using a plain string:

1- Simplicity
- Very easy to implement and understand.
- No need for additional libraries or serialization mechanisms.

2- Lightweight & fast
- Plain strings are compact and have low overhead.
- Faster than object serialization or JSON in most cases.

3- Easy debugging
- You can easily log or print what’s being sent/received.
- Useful during testing or development stages.

4- Cross-language compatibility
- If you stick to simple string formats, even clients in other languages (like Python or JavaScript) can parse and use them.

#### Cons of using a plain string:

1- Manual parsing is error-prone
- You have to split the string and extract data manually (split("|")). 
- Mistakes in delimiters or missing fields can cause bugs.

2- No type safety
- Everything is just a string. You can’t directly work with objects or structured data.

3- Security risk
- Sensitive data like passwords are sent as-is without encryption.
- You have to manually ensure secure transport.

4- Hard to scale
- If your data structure becomes more complex, strings get messy and unreadable.

5- Hard to maintain
- If the protocol changes, both client and server need to be updated very carefully.

### 2. How would you parse it, and what happens if the delimiter appears in the data?
we would typically parse it like this in java:
```java 
String[] parts = receivedLine.split("\\|");
String command = parts[0]; // "LOGIN"
String username = parts[1]; // "user1"
String password = parts[2]; // "pass123"
```
So we're using the `|` as a delimiter to split the string into fields.
#### what happens if the delimiter appears in the data?
- Incorrect parsing.
- server-side crashes or logic errors.
- Security bugs

### 3. Is this approach suitable for more complex or nested data?
No, Not really. Plain strings are fine for very simple data (like "LOGIN|username|pass"),
but once your data becomes complex, nested, or variable,
you should switch to JSON, serialized objects, or some structured format.

### Method 2: Serialized Java Object

### 1. What’s the advantage of sending a full Java object?
- Structured and type-safe
- Cleaner code
- Supports complex/nested objects
-  Faster to implement in Java-only systems

### 2. Could this work with a non-Java client like Python?
No, Java’s serialization is only compatible with Java. Other languages like Python can’t read the binary format 
used by ObjectOutputStream, so this method won't work in cross-language communication.

### Method 3: JSON

### 1. Why is JSON often preferred for communication between different systems?

Because JSON is language-independent and can be easily used by many different programming languages. It 
lets systems written in different languages (like Java, Python, JavaScript, etc.) share data in a common format 
that they all understand. So if a Java server sends JSON, a Python client can read it easily — and vice versa.
That’s what makes it ideal for cross-system (and cross-language) communication.

### 2. Would this format work with servers or clients written in other languages?
Yes, absolutely.

JSON is a language-independent format, so it can be used easily with clients or servers written in any modern 
programming language — including Python, JavaScript, C++, Go, Rust, and many more. Every language has 
libraries for parsing and generating JSON.

That means a Java client can send JSON to a Python server, and the Python server can read and understand it 
without any special handling. This makes JSON one of the most popular formats for cross-platform and web-based communication.