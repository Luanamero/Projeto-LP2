import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {
        int port = 1505;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor esperando por jogadores na porta: " + port);

            while (true) {
                ProtocolServer.sem.acquire();
                Socket playerSocket = serverSocket.accept();
                new ProtocolServer(playerSocket).start();
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Exceção do servidor: " + e.getMessage());
        }
    }
}