import java.util.*;

public class MaxFeeTxHandler {
    private UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public boolean isValidTx(Transaction tx) {
        double sumInputValue = 0;
        int inputIdx = 0;
        UTXOPool doubleSpent = new UTXOPool();
        for(Transaction.Input input : tx.getInputs()) {
            UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);
            // 1. Output claimed by input is in current UTXO pool
            if (!utxoPool.contains(ut)) return false;
            Transaction.Output output = utxoPool.getTxOutput(ut);
            // 2. Signature is valid and 3. No double spent
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(inputIdx), input.signature)
                    || doubleSpent.contains(ut)) return false;
            sumInputValue += output.value;
            doubleSpent.addUTXO(ut, output);
            inputIdx++;
        }

        // 4. All of tx output values are non-negative
        double sumOutputValue = 0;
        for(Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) return false;
            sumOutputValue += output.value;
        }

        // 5. Sum of all
        return sumInputValue >= sumOutputValue;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        HashMap<ByteArrayWrapper, Node> transactionGraph = new HashMap<>(possibleTxs.length);
        HashMap<OutputPair, ArrayList<Transaction>> collisionLists = new HashMap<>();

        // Transaction collisions
        for (Transaction transaction : possibleTxs) {
            for (Transaction.Input input : transaction.getInputs()) {
                OutputPair op = new OutputPair(input.prevTxHash, input.outputIndex);
                if (!collisionLists.containsKey(op)) collisionLists.put(op, new ArrayList<>());
                collisionLists.get(op).add(transaction);
            }
        }

        ArrayList<Transaction> maxPossibleTxs = new ArrayList<>();
        for (ArrayList<Transaction> collisionList : collisionLists.values()) {
            if (collisionList.size() > 1) {
                System.out.println("Collisions: "+collisionList.size());
                maxPossibleTxs.add(findMaxFromCollisions(collisionList));
            } else {
                maxPossibleTxs.add(collisionList.get(0));
            }
        }


        // Insert transactions to hashmap
        for (Transaction transaction : maxPossibleTxs) {
            Node txNode = new Node(transaction);
            transactionGraph.put(new ByteArrayWrapper(transaction.getHash()), txNode);
        }

        // Build graph of transactions
        for (Transaction transaction : maxPossibleTxs) {
            Node txNode = transactionGraph.get(new ByteArrayWrapper(transaction.getHash()));
            for (Transaction.Input input : transaction.getInputs()) {
                if (transactionGraph.containsKey(new ByteArrayWrapper(input.prevTxHash))) {
                    transactionGraph.get(new ByteArrayWrapper(input.prevTxHash)).addNeighbor(txNode);
                }
            }
        }

        // Perform Topological sort in transactions graph
        Stack<Transaction> sorted = this.topologicalSort(transactionGraph);

        // Get valid transactions
        ArrayList<Transaction> validTransactions = new ArrayList<>();
        while (!sorted.isEmpty()) {
            Transaction tx = sorted.pop();
            if (this.isValidTx(tx)) {
                // Remove all input transactions
                for (Transaction.Input input : tx.getInputs()) {
                    UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);
                    this.utxoPool.removeUTXO(ut);
                }

                // Add all unspent outputs
                int idxOutput = 0;
                for (Transaction.Output output : tx.getOutputs()) {
                    this.utxoPool.addUTXO(new UTXO(tx.getHash(), idxOutput), output);
                    idxOutput++;
                }
                validTransactions.add(tx);
            }
        }

        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

    private Transaction findMaxFromCollisions(ArrayList<Transaction> collisions) {
        double fee = Double.NEGATIVE_INFINITY;
        Transaction max = null;
        for (Transaction tx : collisions) {
            double sumInputValue = 0;
            int inputIdx = 0;
            for(Transaction.Input input : tx.getInputs()) {
                UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);
                Transaction.Output output = utxoPool.getTxOutput(ut);
                // 2. Signature is valid and 3. No double spent
                if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(inputIdx), input.signature)) continue;
                sumInputValue += output.value;
                inputIdx++;
            }

            // 4. All of tx output values are non-negative
            double sumOutputValue = 0;
            for(Transaction.Output output : tx.getOutputs()) {
                if (output.value < 0) continue;
                sumOutputValue += output.value;
            }

            // 5. Sum of all
            double feeTmp = sumInputValue - sumOutputValue;
            if (feeTmp > fee) {
                fee = feeTmp;
                max = tx;
            }
        }

        return max;
    }

    private Stack<Transaction> topologicalSort(HashMap<ByteArrayWrapper, Node> graph) {
        Stack<Transaction> sorted = new Stack<>();
        // Initialize visited table
        HashMap<ByteArrayWrapper, Boolean> visited = new HashMap<>(graph.size());
        for (ByteArrayWrapper key : graph.keySet()) {
            visited.put(key, false);
        }

        for (Node txNode : graph.values()) {
            if (!visited.get(new ByteArrayWrapper(txNode.tx.getHash()))) {
                dfs(txNode, visited, sorted);
            }
        }

        return sorted;
    }

    private void dfs(Node txNode, HashMap<ByteArrayWrapper, Boolean> visited, Stack<Transaction> sorted) {
        for (Node neighbor : txNode.neighbors) {
            if (!visited.get(new ByteArrayWrapper(neighbor.tx.getHash()))) {
                dfs(neighbor, visited, sorted);
            }
        }
        visited.put(new ByteArrayWrapper(txNode.tx.getHash()), true);
        sorted.push(txNode.tx);
    }

    private class OutputPair {
        private byte[] txHash;
        private int index;

        private OutputPair(byte[] txHash, int index) {
            this.txHash = txHash;
            this.index = index;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OutputPair)) return false;
            OutputPair that = (OutputPair) other;
            if (this.index != that.index) return false;
            if (!Arrays.equals(this.txHash, that.txHash)) return false;

            return true;
        }
    }

    private class Node {
        private Transaction tx;
        private ArrayList<Node> neighbors;

        private Node(Transaction tx) {
            this.tx = tx;
            this.neighbors = new ArrayList<>();
        }

        private void addNeighbor(Node txNode) {
            this.neighbors.add(txNode);
        }
    }

    private class ByteArrayWrapper {

        private byte[] contents;

        public ByteArrayWrapper(byte[] b) {
            contents = new byte[b.length];
            for (int i = 0; i < contents.length; i++)
                contents[i] = b[i];
        }

        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (getClass() != other.getClass()) {
                return false;
            }

            ByteArrayWrapper otherB = (ByteArrayWrapper) other;
            byte[] b = otherB.contents;
            if (contents == null) {
                if (b == null)
                    return true;
                else
                    return false;
            } else {
                if (b == null)
                    return false;
                else {
                    if (contents.length != b.length)
                        return false;
                    for (int i = 0; i < b.length; i++)
                        if (contents[i] != b[i])
                            return false;
                    return true;
                }
            }
        }

        public int hashCode() {
            return Arrays.hashCode(contents);
        }
    }
}
