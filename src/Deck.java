import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;
    // indice para controle de qual carta foi virada
    private int currentCardIndex;

    // construtor inicializa o baralho e embaralha as cartas
    public Deck() {
        cards = new ArrayList<>();
        initializeDeck();
        shuffle();
    }

    // popula o deck com todas combinacoes de naipe/valor
    private void initializeDeck() {
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    // embaralha as cartas e reseta o contador
    public void shuffle() {
        Collections.shuffle(cards);
        currentCardIndex = 0;
    }

     // distribui a proxima carta do deck
    public Card dealCard() {
        // se todas as cartas foram distribuidas, embaralha novamente
        if (currentCardIndex >= cards.size()) {
            shuffle();
        }
        return cards.get(currentCardIndex++);
    }
}