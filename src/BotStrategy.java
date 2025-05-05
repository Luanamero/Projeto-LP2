// Interface para decidir as estratégias de cada bot (hit ou stand)
// É usada pela classe Player pra ditar o modo do bot jogar
public interface BotStrategy {
    String decideAction(Player player, int dealerUpCardValue);
}

// Hit somente quando a mão é menor que 12 (evitar estouro)
class ConservativeStrategy implements BotStrategy {
    @Override
    public String decideAction(Player player, int dealerUpCardValue) {
        if (player.getHandValue() >= 12) {
            return "stand";
        }
        return "hit";
    }
}

// Estratégia básica do blackjack (maximizar chances matemáticas)
class BasicStrategy implements BotStrategy {
    @Override
    public String decideAction(Player player, int dealerUpCardValue) {
        int handValue = player.getHandValue();
        boolean hasAce = player.getHand().stream().anyMatch(c -> c.getRank() == Card.Rank.ACE);
        int numCards = player.getHand().size();

        if (hasAce && numCards == 2) { // Soft hand
            if (handValue >= 19) return "stand";
            if (handValue == 18 && dealerUpCardValue >= 9) return "hit";
            if (handValue == 18) return "stand";
            return "hit";
        } else { // Hard hand
            if (handValue >= 17) return "stand";
            if (handValue >= 13 && dealerUpCardValue <= 6) return "stand";
            if (handValue == 12 && dealerUpCardValue >= 4 && dealerUpCardValue <= 6) return "stand";
            return "hit";
        }
    }
}

// Mesmo com alto risco, continua dando hit (a não ser que atinja 17+)
class AggressiveStrategy implements BotStrategy {
    @Override
    public String decideAction(Player player, int dealerUpCardValue) {
        // Hit until 17, double down often
        if (player.getHandValue() < 17) {
            return "hit";
        }
        return "stand";
    }
}