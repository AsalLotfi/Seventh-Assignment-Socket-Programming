package Client;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Client {
    private static Socket socket;
    private static InputStream in;
    private static OutputStream out;
    private static BufferedReader reader;
    private static PrintWriter writer;
    private static String username;
    private static String password;


    public static void main(String[] args) throws Exception {

        try (Socket socket = new Socket("localhost", 12345)) {
            // Set up client's streams
            setupStreams(socket);

            Scanner scanner = new Scanner(System.in);

            // --- LOGIN PHASE ---
            System.out.println("===== Welcome to CS Music Room =====");


            boolean loggedIn = false;
            while (!loggedIn) {
                System.out.print("Username: ");
                username = scanner.nextLine();
                System.out.print("Password: ");
                password = scanner.nextLine();

                sendLoginRequest(username, password);

                String loginResponse = reader.readLine();
                if (loginResponse.startsWith("LOGIN-SUCCESS")) {
                    loggedIn = true;
                    System.out.println("Logging succesful");
                    System.out.println("Logged in " + username);
                } else {
                    System.out.println("Login failed. Please try again");
                }
            }

            // --- ACTION MENU LOOP ---
            while (true) {
                printMenu();
                System.out.print("Enter choice: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1" -> enterChat(scanner);
                    case "2" -> uploadFile(scanner);
                    case "3" -> requestDownload(scanner);
                    case "0" -> {
                        System.out.println("Exiting...");
                        writer.println("EXIT");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    private static void setupStreams(Socket socket) throws IOException {
        in = socket.getInputStream();
        out = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(in));
        writer = new PrintWriter(out, true);
    }

    private static void printMenu() {
        System.out.println("\n--- Main Menu ---");
        System.out.println("1. Enter chat box");
        System.out.println("2. Upload a file");
        System.out.println("3. Download a file");
        System.out.println("0. Exit");
    }

    private static void sendLoginRequest(String username, String password) {
        writer.println("LOGIN|" + username + "|" + password);
    }

    private static void enterChat(Scanner scanner) throws IOException {
        System.out.print("You have entered the chat ");

        // Start a thread to listen for incoming messages from the server
        ClientReceiver receiver = new ClientReceiver(in, reader);
        Thread receiverThread = new Thread(receiver);
        receiverThread.start();
        receiver.setInChat(true);

        String message_string = "";
        while (!message_string.equalsIgnoreCase("/exit")){
            message_string = scanner.nextLine();

            if (!message_string.equalsIgnoreCase("/exit") && !(message_string == "")){

                sendChatMessage(message_string); // Sends the message to the server
            }
        }

        System.out.println("You have left the chat.");
        receiver.setInChat(false);
    }

    private static void sendChatMessage(String message_to_send) throws IOException {
        String formattedMessage = "CHAT|" + message_to_send;
        writer.println(formattedMessage);
    }

    private static void uploadFile(Scanner scanner) throws IOException {

        // List all available files in client directory
        String userDir = System.getProperty("user.dir");  // Current working directory
        String path = userDir + "/src/main/resources/Client." + username.trim();
        File clientDir = new File(path);

        if (!clientDir.exists() || !clientDir.isDirectory()) {
            System.out.println("Client directory not found.");
            return;
        }

        File[] files = clientDir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files to upload.");
            return;
        }

        // Show available files
        System.out.println("Select a file to upload:");
        for (int i = 0; i < files.length; i++) {
            System.out.println((i + 1) + ". " + files[i].getName());
        }

        System.out.print("Enter file number: ");

        // Get a valid choice from the user
        int choice = -1;
        while (true) {
            String input = scanner.nextLine();

            try {
                choice = Integer.parseInt(input) - 1;
                if (choice >= 0 && choice < files.length) {
                    break; // valid input, exit the loop
                } else {
                    System.out.println("Invalid choice. Enter a number between 1 and " + files.length);
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }

        try {
            // 1. Send metadata first
            writer.println("UPLOAD_REQUEST|" + files[choice].getName() + "|" + files[choice].length());

            // 2. Wait for server to say UPLOAD_READY
            String response = reader.readLine();

            if (response.startsWith("UPLOAD_DENIED")) {
                System.out.println("Server denied the upload: " + response.split("\\|", 2)[1]);
                return;
            } else if (response.equals("UPLOAD_READY")) {

                // 3. Try to open the file
                FileInputStream fileInputStream = new FileInputStream(files[choice]);

                // 4. If that succeeded, notify server to begin receiving
                writer.println("UPLOAD_START");

                // 5. Send raw bytes
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    bufferedOutputStream.write(buffer, 0, bytesRead);
                }

                bufferedOutputStream.flush();
                fileInputStream.close();
                System.out.println("Upload complete.");
            }

        } catch (IOException e) {
            System.out.println("Failed to upload file: " + e.getMessage());

            try {
                writer.println("UPLOAD_FAILED|" + files[choice].getName());
            } catch (Exception ex) {
                System.out.println("Could not notify server about upload failure.");
            }
        }
    }

    private static void requestDownload(Scanner scanner) throws IOException {
        // Step 1 : Request a list of available files
        writer.println("LIST");

        String fileListResponse = reader.readLine();
        if (fileListResponse == null || !fileListResponse.startsWith("FILE_LIST|")) {
            System.out.println("Failed to get file list from server.");
            return;
        }

        // Step 2 : Parse and display file list
        String[] parts = fileListResponse.split("\\|", 2);
        String[] files = parts[1].split(",");

        if (files.length == 0 || files[0].isEmpty()) {
            System.out.println("No files available on the server.");
            return;
        }

        System.out.println("Available files:");
        for (int i = 0; i < files.length; i++) {
            System.out.println((i + 1) + ". " + files[i]);
        }

        // Step 3 : Prompt user to choose a file
        System.out.println("Enter the number of the file to download: ");
        int choice;

        // Handle invalid input
        while (true) {
            choice = scanner.nextInt();
            scanner.nextLine(); // Clear newline

            if (choice >= 1 && choice <= files.length) {
                break;
            }
            System.out.println("Invalid choice. Please try again.");
        }

        String selectedFile = files[choice - 1];
        writer.println("DOWNLOAD|" + selectedFile);

        // Step 4 : Receive file info
        String fileInfo = reader.readLine();
        if (fileInfo == null || !fileInfo.startsWith("FILE_INFO|")) {
            System.out.println("Failed to receive file info from server.");
            return;
        }

        String[] fileInfoParts = fileInfo.split("\\|");
        String fileName = fileInfoParts[1];
        int fileLength = Integer.parseInt(fileInfoParts[2]);

        // Step 5 : Receive file bytes
        byte[] buffer = new byte[4096];
        int totalRead = 0;
        byte[] fileData = new byte[fileLength];

        while (totalRead < fileLength) {
            int bytesRead = in.read(buffer, 0, Math.min(buffer.length, fileLength - totalRead));
            if (bytesRead == -1) break;

            System.arraycopy(buffer, 0, fileData, totalRead, bytesRead);
            totalRead += bytesRead;
        }

        if (totalRead != fileLength) {
            System.out.println("Warning: File received is incomplete (" + totalRead + "/" + fileLength + " bytes).");
            System.out.println("Please try again.");
            return;
        }

        // Step 6 : Save file to user's folder
        // Get the base project directory (absolute path)
        String baseDir = System.getProperty("user.dir");  // e.g., C:/Users/Notebook/Ap-course/Seventh-Assignment-Socket-Programming

        // Build the full path: base/resources/Client.username
        Path userDir = Paths.get(baseDir, "src", "main", "resources", "Client." + username.trim());

        Path savePath = userDir.resolve(fileName);
        Files.write(savePath, fileData);

        System.out.println("File downloaded and saved to: " + savePath);
    }
}