public class Card {
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public int getValue() {
        return rank.getValue();
    }

    @Override
    public String toString() {
        return rank + " de " + suit;
    }

    public enum Suit {
        HEARTS("Copas"), DIAMONDS("Ouros"), CLUBS("Paus"), SPADES("Espadas");

        private final String name;

        Suit(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Rank {
        ACE("Ás", 11), TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
        SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
        JACK("Valete", 10), QUEEN("Dama", 10), KING("Rei", 10);

        private final String name;
        private final int value;

        Rank(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}