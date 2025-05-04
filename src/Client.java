import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;


public class Client {

    public static void main(String[] args) {


        // Pergunta ao usuário a porta
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        int porta = 1505; // valor padrão

        try {
            System.out.print("Digite a porta para se conectar (ex: 8888): ");
            porta = Integer.parseInt(stdIn.readLine());
        } catch (IOException e) {
            System.out.println("Erro ao ler a porta. Usando porta padrão 1505.");
        }

        try (Socket socket = new Socket("localhost", porta);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String fromServer;

            while (true) {
                fromServer = in.readUTF();
                System.out.println(fromServer);

                if (fromServer.toLowerCase().contains("quanto deseja apostar")) {
                    String bet = stdIn.readLine();
                    out.writeUTF(bet);
                }
                if (fromServer.toLowerCase().contains("digite o seu nome") ||
                    fromServer.toLowerCase().contains("escolha um id") ||
                    fromServer.toLowerCase().contains("digite 'pronto") ||
                    fromServer.toLowerCase().contains("digite o nome do jogo") ||
                    fromServer.toLowerCase().contains("id inválido") ||
                    fromServer.toLowerCase().contains("deseja continuar assistindo")
                ) {
                    String userInput = stdIn.readLine();
                    out.writeUTF(userInput);
                    out.flush();
                } 
                else if (fromServer.toLowerCase().contains("escolha uma ação")) {
                    handleInputWithTimeout(stdIn, out, 50000);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao conectar-se ao servidor na porta " + porta);
            e.printStackTrace();
        }
    }

    private static void handleInputWithTimeout(BufferedReader stdIn, DataOutputStream out, int timeoutMillis) {
        Thread inputThread = new Thread(() -> {
            try {
                String userInput = stdIn.readLine();
                out.writeUTF(userInput != null ? userInput : "");
                
            } catch (IOException e) {
                try {
                    out.writeUTF("");
                    
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        inputThread.start();

        try {
            inputThread.join(timeoutMillis);
            if (inputThread.isAlive()) {
                inputThread.interrupt();
                out.writeUTF("");
                
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}