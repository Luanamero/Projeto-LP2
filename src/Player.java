// A classe representa um jogador conectado ao servidor, seja humano (com Socket)
// ou bot (estratégia de decisão automática)

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Player {
    private Socket socket;
    private String nickname;
    private int chips;
    private boolean isActive;
    private List<Card> hand;
    private int currentBet;
    private boolean isBot;
    private BotStrategy strategy;

    // Jogador humano
    public Player(Socket socket, String nickname) {
        this(socket, nickname, false, null);
    }

    // Bot (isBot = true) – define estratégia
    public Player(Socket socket, String nickname, boolean isBot, BotStrategy strategy) {
        this.socket = socket;           // Só usado por humanos, conexão com servidor
        this.nickname = nickname;       // Nome do jogador
        this.chips = 1000;              // Qntd de fichas iniciais
        this.isActive = true;           // Confirmar se o jogador está ou não participando do jogo ainda
        this.hand = new ArrayList<>();  // Cartas na mão do jogador 
        this.currentBet = 0;            // Aposta feita na rodada
        this.isBot = isBot;             // Se é bot ou não
        this.strategy = strategy;       // Estratégia do bot pra decidir as ações
    }

    // Colocar carta na mão
    public void addCard(Card card) {
        hand.add(card);
    }

    // Limpar mão (usar sempre no início de cada rodada)
    public void clearHand() {
        hand.clear();
    }

    // Calcula o valor da mão
    public int getHandValue() {
        int value = 0;
        int aces = 0;

        // lógica para considerar Ás como 1 ou 11 (sem estourar)
        for (Card card : hand) {
            value += card.getValue();
            if (card.getRank() == Card.Rank.ACE) {
                aces++;
            }
        }

        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }

    // Blackjack = 21 com 2 cartas
    public boolean hasBlackjack() {
        return hand.size() == 2 && getHandValue() == 21;
    }

    // Se > 21, então estourou
    public boolean isBusted() {
        return getHandValue() > 21;
    }

    // Faz a aposta e desconta fichas
    public void placeBet(int amount) {
        if (amount <= chips) {
            currentBet = amount;
            chips -= amount;
        }
    }

    // Calcula o ganho e devolve fichas
    public void winBet(boolean blackjack) {
        chips += currentBet * (blackjack ? 2.5 : 2); // blackjack: recebe 3:2
        currentBet = 0;
    }

    // Empate (devolve a aposta ao jogador) 
    public void push() {
        chips += currentBet;
        currentBet = 0;
    }

    // Getters e setters
    public Socket getSocket() {
        return socket;
    }

    public String getNickname() {
        return nickname;
    }

    public int getChips() {
        return chips;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<Card> getHand() {
        return hand;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public boolean isBot() {
        return isBot;
    }

    // Retorna a mão do jogador como string
    public String getHandAsString(boolean hideFirstCard) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i == 0 && hideFirstCard) {          // Oculta a primeira mão (Dealer)
                sb.append("[Carta Oculta]");
            } else {
                sb.append(hand.get(i).toString());  // Formata para exibir como string
            }
            if (i < hand.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // Usado CASO não tenha ação do jogador
    public String decideAction(int dealerUpCardValue) {
        if (strategy != null) {
            return strategy.decideAction(this, dealerUpCardValue);
        }
        return "stand";
    }

    // Encerrar conexão quando o jogador sai ou perde
    public void closeSocket() {
        try {
            this.isActive = false;
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}