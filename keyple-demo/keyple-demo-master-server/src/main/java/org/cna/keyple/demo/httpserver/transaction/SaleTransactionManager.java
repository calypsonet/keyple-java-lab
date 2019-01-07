package org.cna.keyple.demo.httpserver.transaction;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SaleTransactionManager {


    List<SaleTransaction> transactions;


    public SaleTransactionManager(){
        this.transactions = new ArrayList<SaleTransaction>();
    }

    public List<SaleTransaction> findAll(){
        return transactions;
    }

    public String create(String poReader){
        SaleTransaction transaction = new SaleTransaction(poReader);
        this.transactions.add(transaction);
        return transaction.getId();
    }

    public SaleTransaction getById(String transactionId){
        for(SaleTransaction tx : transactions){
            if(tx.getId().equals(transactionId)){
                return tx;
            }
        }
        return null;
    }

    public SaleTransaction getByPoReaderName(String poReaderName){
        for(SaleTransaction tx : transactions){
            if(tx.getPoReader().equals(poReaderName)){
                return tx;
            }
        }
        return null;
    }

    public SaleTransaction getByPoSn(byte[] poSerialNumber){
        for(SaleTransaction tx : transactions){
            if(Arrays.equals(tx.getCardContent().getSerialNumber(), poSerialNumber)){
                return tx;
            }
        }
        return null;
    }

    public void deleteById(String txId){
        SaleTransaction tx = getById(txId);
        if(tx != null){
            transactions.remove(tx);
        }
    }

    public void update(String txId, SaleTransaction saleTransaction){
        SaleTransaction tx = getById(txId);
        transactions.remove(tx);
        transactions.add(saleTransaction);
    }

}
