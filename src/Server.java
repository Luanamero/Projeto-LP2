// Importa classes necessárias para entrada/saída e conexões de rede
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) {
        int port = 1505; // Porta do tipo Resgistrada: Não são reservadas, mas apenas listadas para coordenar o uso para serviços não padronizados

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Servidor esperando por jogadores na porta: " + port);

            // Loop infinito para aceitar múltiplas conexões de jogadores
            while (true) {
                // Aguarda permissão no semáforo antes de aceitar um novo jogador
                ProtocolServer.sem.acquire();

                // Aceita uma nova conexão de jogador (bloqueia até que um cliente se conecte)
                Socket playerSocket = serverSocket.accept();

                // Cria e inicia uma nova thread que irá lidar com a lógica do jogador usando o protocolo definido
                new ProtocolServer(playerSocket).start();

                
            }

        // Trata exceções de entrada/saída (problemas com socket) ou de interrupção (semáforo)
        } catch (IOException | InterruptedException e) {
            System.out.println("Exceção do servidor: " + e.getMessage());
        }
    }
}
