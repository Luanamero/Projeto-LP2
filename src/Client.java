import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class Client {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 1505);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            String fromServer;
            // Modifique o loop principal para:
            while ((fromServer = in.readLine()) != null) {
                System.out.println(fromServer);

                if (fromServer.startsWith("##Apostas##")) {
                    // Esta é a marcação oculta, não mostramos no console
                    // Ler a próxima linha que contém a mensagem real
                    String betPrompt = in.readLine();
                    System.out.println(betPrompt);  // Mostra apenas a mensagem amigável
                    String bet = stdIn.readLine();
                    out.println(bet);
                }
                else if (fromServer.startsWith("Escolha um ID de jogo ou digite 'criar' para criar um novo jogo.")
                        || fromServer.startsWith("Digite o seu nome/apelido:")
                        || fromServer.startsWith("Digite 'pronto' quando estiver pronto para começar a jogar.")
                        || fromServer.startsWith("Digite o nome do jogo: ")
                        || fromServer.startsWith("Digite um número de ID válido.")) {
                    String userInput = stdIn.readLine();
                    out.println(userInput);
                } 
                else if (fromServer.startsWith("Echo") 
                        || fromServer.startsWith("Escolha uma ação (hit/stand):")) {
                    handleInputWithTimeout(stdIn, out, 50000);
                }
            }
        } catch (IOException e) {
            System.err.println("Não conseguiu se conectar ao localhost");
            e.printStackTrace();
        }
    }

    private static void handleInputWithTimeout(BufferedReader stdIn, PrintWriter out, int timeoutMillis) {
        Thread inputThread = new Thread(() -> {
            try {
                String userInput = stdIn.readLine();
                if (userInput != null) {
                    out.println(userInput);
                } else {
                    out.println("");
                }
            } catch (IOException e) {
                System.err.println("Erro enquanto lia dados do cliente");
                out.println("");
            }
        });

        inputThread.start();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (inputThread.isAlive()) {
                    inputThread.interrupt();
                    out.println("");
                }
                timer.cancel();
            }
        }, timeoutMillis);

        try {
            inputThread.join(timeoutMillis);
            timer.cancel();
        } catch (InterruptedException e) {
            System.err.println("Thread main foi interrompida enquanto esperava as outras.");
        }
    }
}