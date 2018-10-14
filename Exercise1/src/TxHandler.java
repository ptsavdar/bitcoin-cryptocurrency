import java.util.ArrayList;

public class TxHandler {
    private UTXOPool utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> validTransactions = new ArrayList<>();

        for(int i = 0; i < possibleTxs.length; i++) {
            Transaction tx = possibleTxs[i];
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

}
