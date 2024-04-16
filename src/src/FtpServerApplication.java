import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FtpServerApplication {
    private static final int APPLICATION_PORT = 1025;
    boolean isServerRunning = true;

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(APPLICATION_PORT);
            System.out.println("FTP Server is running on port " + APPLICATION_PORT);
            listen(serverSocket);
        } catch (IOException e) {
            System.out.println("Could not create server socket. Reason: " + e.getMessage());
            System.exit(-1);
        }
        System.out.println("FTP Server started listening on port " + APPLICATION_PORT);
    }

    private static void listen(ServerSocket serverSocket) throws IOException {
        while(true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
            ClientHandler handler = new ClientHandler(clientSocket, APPLICATION_PORT);
            handler.start();
        }
    }
}