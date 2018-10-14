// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.HashMap;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private HashMap<ByteArrayWrapper, Node> blockchain;
    private TransactionPool txPool;
    private Node maxHeightNode;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        txPool = new TransactionPool();
        Node node = new Node(genesisBlock, null);
        blockchain = new HashMap<>();
        blockchain.put(new ByteArrayWrapper(genesisBlock.getHash()), node);
        maxHeightNode = node;
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.txHandler.getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null || block.getPrevBlockHash().length == 0) return false;
        Node prev = blockchain.get(new ByteArrayWrapper(block.getPrevBlockHash()));
        if (prev == null || prev.height + 1 <= maxHeightNode.height - CUT_OFF_AGE) return false;
        Node node = new Node(block, prev);
        if (!node.valid) return false;
        if (node.height > maxHeightNode.height) maxHeightNode = node;
        for (Transaction tx : block.getTransactions()) {
            txPool.removeTransaction(tx.getHash());
        }
        blockchain.put(new ByteArrayWrapper(block.getHash()), node);

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }

    private class Node {
        private Block block;
        private TxHandler txHandler;
        private int height;
        private boolean valid;

        public Node(Block block, Node prev) {
            this.block = block;
            valid = false;
            height = 1;
            UTXOPool utxoPool = new UTXOPool();
            if (prev != null) {
                height += prev.height;
                utxoPool = prev.txHandler.getUTXOPool();
            }
            txHandler = new TxHandler(utxoPool);
            int length = txHandler.handleTxs(block.getTransactions().toArray(new Transaction[0])).length;
            if (length == block.getTransactions().size()) valid = true;
            Transaction coinbase = block.getCoinbase();
            txHandler.getUTXOPool().addUTXO(new UTXO(coinbase.getHash(), 0), coinbase.getOutput(0));
        }
    }
}