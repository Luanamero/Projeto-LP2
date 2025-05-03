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

    public Player(Socket socket, String nickname) {
        this(socket, nickname, false, null);
    }

    public Player(Socket socket, String nickname, boolean isBot, BotStrategy strategy) {
        this.socket = socket;
        this.nickname = nickname;
        this.chips = 1000; // Starting chips
        this.isActive = true;
        this.hand = new ArrayList<>();
        this.currentBet = 0;
        this.isBot = isBot;
        this.strategy = strategy;
    }

    public void addCard(Card card) {
        hand.add(card);
    }

    public void clearHand() {
        hand.clear();
    }

    public int getHandValue() {
        int value = 0;
        int aces = 0;

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

    public boolean hasBlackjack() {
        return hand.size() == 2 && getHandValue() == 21;
    }

    public boolean isBusted() {
        return getHandValue() > 21;
    }

    public void placeBet(int amount) {
        if (amount <= chips) {
            currentBet = amount;
            chips -= amount;
        }
    }

    public void winBet(boolean blackjack) {
        chips += currentBet * (blackjack ? 2.5 : 2); // 3:2 payout for blackjack
        currentBet = 0;
    }

    public void push() {
        chips += currentBet;
        currentBet = 0;
    }

    // Getters and setters
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

    public String getHandAsString(boolean hideFirstCard) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i == 0 && hideFirstCard) {
                sb.append("[Carta Oculta]");
            } else {
                sb.append(hand.get(i).toString());
            }
            if (i < hand.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public String decideAction(int dealerUpCardValue) {
        if (strategy != null) {
            return strategy.decideAction(this, dealerUpCardValue);
        }
        return "stand"; // Default if no strategy
    }

    public void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}