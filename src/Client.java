import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

public class Client {

    public static void main(String[] args) {
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        int porta = 1505; // valor padrão

        try {
            System.out.print("Digite a porta para se conectar (ex: 8888): ");
            porta = Integer.parseInt(stdIn.readLine());
        } catch (IOException e) {
            System.out.println("Erro ao ler a porta. Usando porta padrão 1505.");
        }

        // try-with-resources garante que socket e streams serao fechados
        try (Socket socket = new Socket("localhost", porta);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String fromServer; // armazenara mensagens recebidas do servidor
            
            // loop principal de comunicação
            while (true) {
                try {
                    fromServer = in.readUTF(); // le mensagem do servidor
                } 
                // pode lançar EOFException se conexao for fechada pelo servidor
                catch (EOFException | SocketException e) {
                    // servidor fechou a conexão
                    System.out.println("Conexão encerrada pelo servidor.");
                    break;
                }

                System.out.println(fromServer);
                String lower = fromServer.toLowerCase();

                // diferentes tratamentos baseados no estado do servidor
                if (lower.contains("quanto deseja apostar")) {
                    String bet = stdIn.readLine();
                    out.writeUTF(bet);
                    out.flush();
                }
                else if (lower.contains("digite o seu nome")
                      || lower.contains("escolha um id")
                      || lower.contains("digite 'pronto")
                      || lower.contains("digite o nome do jogo")
                      || lower.contains("id inválido")
                      ) {

                    String userInput = stdIn.readLine();
                    out.writeUTF(userInput);
                    out.flush();
                }
                else if (lower.contains("escolha uma ação")) {
                    // estado critico com timeout para evitar bloqueio indefinido
                    handleInputWithTimeout(stdIn, out, 50000);
                }
            }

        } catch (IOException e) {
            System.err.println("Erro ao conectar-se ao servidor na porta " + porta);
            e.printStackTrace();
        }
    }

    private static void handleInputWithTimeout(BufferedReader stdIn, DataOutputStream out, int timeoutMillis) {
        // cria thread separada para nao bloquear a thread principal
        Thread inputThread = new Thread(() -> {
            try {
                String userInput = stdIn.readLine();
                out.writeUTF(userInput != null ? userInput : "");
                out.flush();
            } catch (IOException e) {
                try {
                    out.writeUTF("");
                    out.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        inputThread.start(); // inicia a thread de input

        try {
            // aguarda a thread de input por um tempo limite
            // se o tempo limite expirar, interrompe a thread
            inputThread.join(timeoutMillis);
            if (inputThread.isAlive()) {
                inputThread.interrupt();
                out.writeUTF("");
                out.flush();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}