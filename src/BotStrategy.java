public interface BotStrategy {
    String decideAction(Player player, int dealerUpCardValue);
}

class ConservativeStrategy implements BotStrategy {
    @Override
    public String decideAction(Player player, int dealerUpCardValue) {
        // Stand on 12 or higher
        if (player.getHandValue() >= 12) {
            return "stand";
        }
        return "hit";
    }
}

class BasicStrategy implements BotStrategy {
    @Override
    public String decideAction(Player player, int dealerUpCardValue) {
        int handValue = player.getHandValue();
        boolean hasAce = player.getHand().stream().anyMatch(c -> c.getRank() == Card.Rank.ACE);
        int numCards = player.getHand().size();

        // Basic strategy rules
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