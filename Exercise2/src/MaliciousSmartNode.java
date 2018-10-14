import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class MaliciousSmartNode implements Node {
    private final HashSet<Transaction> pendingTransactions;
    private final int numRounds;
    private final double p_graph, p_malicious, p_txDistribution;
    private int round, size;

    public MaliciousSmartNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
        this.round = 0;
        pendingTransactions = new HashSet<>();
    }

    public void setFollowees(boolean[] followees) {

    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions.addAll(pendingTransactions);
    }

    public Set<Transaction> sendToFollowers() {
        HashSet<Transaction> proposals = new HashSet<>();
        //proposals.addAll(this.pendingTransactions);
        for (int i = 0; i < 4; i++) {
            proposals.add(new Transaction(i));
        }

        if (round == 1) {
            proposals.add(new Transaction(4));
        }
        round++;

        return proposals;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        return;
    }
}
