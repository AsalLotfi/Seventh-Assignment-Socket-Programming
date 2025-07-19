package Client;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ClientReceiver implements Runnable {
    private InputStream in;
    BufferedReader reader;
    private volatile boolean inChat = false;

    public ClientReceiver(InputStream in, BufferedReader reader) {
        this.in = in;
        this.reader = reader;
    }

    public void setInChat(boolean inChat) {
        this.inChat = inChat;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String message = reader.readLine();
                if (message == null) {
                    // Server has closed the connection
                    System.out.println("Disconnected from server.");
                    break;
                }

                // Dispatch messages based on type
                if (message.startsWith("CHAT|")) {
                    if (inChat) {
                        System.out.println(message.substring(5)); // Remove "CHAT|"
                    }
                } else {
                    // Always show non-chat messages
                    System.out.println(message);
                }
            }
        } catch (Exception e) {
            System.out.println("Error reading from server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}