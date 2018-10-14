import java.util.*;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private boolean[] followees, malicious;
    private final HashSet<Transaction> pendingTransactions;
    private HashMap<Integer, HashSet<Transaction>> nodeTransactions;
    private HashMap<Transaction, HashSet<Integer>> transactionNodes;

    private final double p_graph, p_malicious, p_txDistribution;
    private final int numRounds;
    private int round;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        round = 0;
        pendingTransactions = new HashSet<>();
        // Topology info
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    /**
     * Initializes followees list
     * @param followees
     */
    public void setFollowees(boolean[] followees) {
        this.malicious = new boolean[followees.length];
        this.followees = Arrays.copyOf(followees, followees.length);
    }

    /**
     * Initialize proposal list of transactions
     * @param pendingTransactions
     */
    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions.addAll(pendingTransactions);
    }

    /**
     * Returns initial list of transaction on every round
     * except last round when the final set of proposals is returned
     *
     * @return proposals
     */
    public Set<Transaction> sendToFollowers() {
        Set<Transaction> proposals;
        if (this.round < 3) {
            this.round++;
            proposals = new HashSet<>(round % 2 == 1 ? getOddTransactions(this.pendingTransactions) : getEvenTransactions(this.pendingTransactions));
        } else {
            proposals = new HashSet<>(this.pendingTransactions);
            this.pendingTransactions.clear();
        }

        //pendingTransactions.clear();

        return proposals;
    }

    /**
     * Receive transactions from followees and judge if followee is malicious or not
     * @param candidates
     */
    public void receiveFromFollowees(Set<Candidate> candidates) {
        Set<Integer> senders = candidates.stream().map(c -> c.sender).collect(Collectors.toSet());
        if (this.round < 3) {
            for (Candidate c: candidates) {
                if(!malicious[c.sender] && c.tx.id % 2 != this.round % 2) {
                    System.out.println("Round: "+this.round);
                    System.out.println("Transaction: "+c.tx.id);
                    System.out.println("Malicious found by even/odd: "+c.sender);
                    malicious[c.sender] = true;
                }
            }
        } else {
            for (int i = 0; i < followees.length; i++) {
                if (followees[i] && !senders.contains(i)){
                    System.out.println("Malicious found: "+i);
                    malicious[i] = true;
                }
            }
        }

        for (Candidate c: candidates) {
            if(!malicious[c.sender]) {
                this.pendingTransactions.add(c.tx);
            }
        }
    }

    private Set<Transaction> getOddTransactions(Set<Transaction> txs) {
        Set<Transaction> odd = new HashSet<>();
        for (Transaction tx: txs) {
            if (tx.id % 2 == 1) odd.add(tx);
        }

        return odd;
    }

    private Set<Transaction> getEvenTransactions(Set<Transaction> txs) {
        Set<Transaction> even = new HashSet<>();
        for (Transaction tx: txs) {
            if (tx.id % 2 == 0) even.add(tx);
        }

        return even;
    }
}
