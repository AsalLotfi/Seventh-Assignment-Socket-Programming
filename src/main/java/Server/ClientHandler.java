package Server;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private List<ClientHandler> allClients;
    private String username;

    // Fields to store pending upload info
    private String pendingUploadFilename;
    private int pendingUploadLength;

    public ClientHandler(Socket socket, List<ClientHandler> allClients) {
        try {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            this.allClients = allClients;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            while (true) {
                String input = reader.readLine(); // waits for client message
                if (input == null) break; // client closed connection

                // Example protocol: "CHAT|hello there", "UPLOAD|file.txt|1234", etc.
                String[] parts = input.split("\\|");

                String command = parts[0];

                switch (command) {
                    case "LOGIN":
                        handleLogin(parts[1], parts[2]);
                        break;

                    case "CHAT":
                        String message = username + ": " + parts[1];
                        broadcast(message);
                        break;

                    case "UPLOAD_REQUEST":
                        pendingUploadFilename = parts[1];
                        pendingUploadLength = Integer.parseInt(parts[2]);

                        // Check if file already exists on server
                        String serverDirectory = System.getProperty("user.dir"); // Current working directory
                        String path = serverDirectory + "/src/main/resources/Server.Files/" + pendingUploadFilename;
                        File serverFile = new File(path);
                        if (serverFile.exists()) {
                            sendMessage("UPLOAD_DENIED|File already exists.");
                            pendingUploadFilename = null;
                            pendingUploadLength = 0;
                        } else {
                            sendMessage("UPLOAD_READY");
                        }
                        break;

                    case "UPLOAD_START":
                        receiveFile(pendingUploadFilename, pendingUploadLength);
                        pendingUploadFilename = null;
                        pendingUploadLength = 0;
                        break;

                    case "UPLOAD_FAILED":
                        String failedFile = parts[1];
                        System.out.println("Client canceled upload: " + failedFile);
                        pendingUploadFilename = null;
                        pendingUploadLength = 0;
                        break;

                    case "LIST":
                        sendFileList();
                        break;

                    case "DOWNLOAD":
                        String requestedFile = parts[1];
                        sendFile(requestedFile);
                        break;

                    case "EXIT":
                        System.out.println(username + " disconnected.");
                        return;

                    default:
                        sendMessage("ERROR|Unknown command: " + input);
                        System.out.println("Unknown command: " + input);
                }
            }
        } catch (Exception e) {
            sendMessage("ERROR|Internal server error. Please try again.");
            System.out.println("Exception in client handler for " + username + ": " + e.getMessage());
            e.printStackTrace();

        } finally {
            try {
                allClients.remove(this);
                socket.close();
                System.out.println("Connection with " + username + " closed");

            } catch (IOException e) {
                System.out.println("Error while closing socket for " + username);
            }
        }
    }


    private void sendMessage(String msg){
        try {
            PrintWriter writer = new PrintWriter (out, true);
            writer.println(msg);
        } catch (Exception e) {
            System.err.println("Failed to send message to " + username);
            e.printStackTrace();
        }
    }

    private void broadcast(String msg) throws IOException {
        for (ClientHandler client : allClients) {
            if (client != this) {
                client.sendMessage("CHAT|" + msg);
            }
        }
    }

    private void sendFileList(){
        try {
            String serverDirectory = System.getProperty("user.dir"); // Current working directory
            String path = serverDirectory + "/src/main/resources/Server.Files/";
            File serverDir = new File(path);

            if (!serverDir.exists() || !serverDir.isDirectory()) {
                sendMessage("ERROR|Server directory not found.");
                System.out.println("ERROR|Server directory not found.");
                return;
            }

            File[] files = serverDir.listFiles();
            if (files == null || files.length == 0) {
                sendMessage("FILE_LIST|"); // empty list
                System.out.println("Sent empty file list to user '" + username + "'.");
                return;
            }

            // Collect file names into a comma-separated string
            StringBuilder stringBuilder = new StringBuilder("FILE_LIST|");
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    stringBuilder.append(files[i].getName());
                    if (i < files.length - 1) {
                        stringBuilder.append(",");
                    }
                }
            }

            // Send the list to the client
            sendMessage(stringBuilder.toString());
            System.out.println("Sent file list to user '" + username + "'.");

        } catch (Exception e) {
            sendMessage("ERROR|Unable to retrieve file list.");
            System.out.println("ERROR sending file list to user '" + username + "': " + e.getMessage());
        }
    }

    private void sendFile(String fileName){
        try {
            String serverDir = System.getProperty("user.dir");
            String path = serverDir + "/src/main/resources/Server.Files/" + fileName;
            File file = new File(path);

            if (!file.exists() || !file.isFile()) {
                sendMessage("ERROR|File not found.");
                System.out.println("ERROR|File not found.");
                return;
            }

            long fileSize = file.length();
            sendMessage("FILE_INFO|" + file.getName() + "|" + fileSize);
            System.out.println("Sent file metadata to user '" + username + "'.");

            // Send raw bytes
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, bytesRead);
            }
            bufferedOutputStream.flush();
            fileInputStream.close();
            System.out.println("Sent file to user '" + username + "'.");

        } catch (IOException e) {
            sendMessage("ERROR|Failed to send file.");
            System.out.println("ERROR sending file to user '" + username + "': " + e.getMessage());
        }
    }

    private void receiveFile(String filename, int fileLength) {
        try {
            // Prepare to receive the file into a byte array
            byte[] fileBytes = new byte[fileLength];
            int totalRead = 0;

            while (totalRead < fileLength) {
                int bytesRead = in.read(fileBytes, totalRead, fileLength - totalRead);

                if (bytesRead == -1) {
                    // Client disconnected unexpectedly
                    System.out.println("Client " + username + " disconnected during file upload: " + filename);
                    return; // stop processing
                }

                totalRead += bytesRead;
            }

            // Save the file using helper method
            saveUploadedFile(filename, fileBytes);

            // Confirm to client
            sendMessage("UPLOAD_SUCCESS|" + filename);
            System.out.println("File " + filename + " received successfully from " + username);

        } catch (IOException e) {
            sendMessage("ERROR| failed to receive file.");
            System.out.println("ERROR receiving file: '" + filename + "' from '" + username + "': " + e.getMessage());
        }
    }

    private void saveUploadedFile(String fileName, byte[] data) throws IOException {
        // Define the directory where files will be stored
        String serverDirectory = System.getProperty("user.dir");
        String path = serverDirectory + "/src/main/resources/Server.Files/";
        File serverDir = new File(path);

        if (!serverDir.exists()) {
            if (serverDir.mkdirs()) {
                System.out.println("Created server directory at: " + serverDir.getAbsolutePath());
            } else {
                System.out.println("ERROR: Failed to create server directory.");
                throw new IOException("Could not create server directory.");
            }
        }

        // Create the target file inside the server directory
        File file = new File(serverDir, fileName);

        // Write byte[] data to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
            System.out.println("Uploaded file saved: " + file.getAbsolutePath());
            System.out.println("File size: " + data.length + " bytes");
        } catch (IOException e) {
            System.out.println("ERROR saving file '" + fileName + "': " + e.getMessage());
            throw e; // rethrow so the caller knows it failed
        }
    }

    private void handleLogin(String username, String password) throws IOException, ClassNotFoundException {
        boolean success = Server.authenticate(username, password);

        PrintWriter writer = new PrintWriter (out, true);
        if (success) {
            this.username = username;
            writer.println("LOGIN-SUCCESS");
            System.out.println("User logged in: " + username);
        } else {
            writer.println("LOGIN-FAILED");
            System.out.println("Failed login attempt for username: " + username);
        }
    }
}
