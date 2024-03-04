import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Bank implementation.
 *
 *
 * @author : Темников Алексей
 */
public class BankImpl implements Bank {
    /**
     * An array of accounts by index.
     */
    private final Account[] accounts;

    /**
     * Creates new bank instance.
     * @param n the number of accounts (numbered from 0 to n-1).
     */
    public BankImpl(final int n) {
        accounts = new Account[n];
        for (int i = 0; i < n; i++) {
            accounts[i] = new Account();
        }
    }

    @Override
    public int getNumberOfAccounts() {
        return accounts.length;
    }
    @Override
    public long getAmount(final int index) {
        accounts[index].lock.lock();
        try {
            return accounts[index].amount;
        } finally {
            accounts[index].lock.unlock();
        }
    }
    @Override
    public long getTotalAmount() {
        for (Account account: accounts) {
            account.lock.lock();
        }
        try {
            long sum = 0;
            for (Account account : accounts) {
                sum += account.amount;
            }
            return sum;
        } finally {
            for (Account account: accounts) {
                account.lock.unlock();
            }
        }
    }
    @Override
    public long deposit(final int index, final long amount) {
        accounts[index].lock.lock();
        try {
            if (amount <= 0)
                throw new IllegalArgumentException("Invalid amount: " + amount);
            final Account account = accounts[index];
            if (amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)
                throw new IllegalStateException("Overflow");
            account.amount += amount;
            return account.amount;
        } finally {
            accounts[index].lock.unlock();
        }
    }
    @Override
    public long withdraw(final int index, final long amount) {
        accounts[index].lock.lock();
        try {
            if (amount <= 0)
                throw new IllegalArgumentException("Invalid amount: " + amount);
            final Account account = accounts[index];
            if (account.amount - amount < 0)
                throw new IllegalStateException("Underflow");
            account.amount -= amount;
            return account.amount;
        } finally {
            accounts[index].lock.unlock();
        }
    }
    @Override
    public void transfer(final int fromIndex, final int toIndex, final long amount) {
        accounts[min(fromIndex, toIndex)].lock.lock();
        accounts[max(fromIndex, toIndex)].lock.lock();
        try {
            if (amount <= 0)
                throw new IllegalArgumentException("Invalid amount: " + amount);
            if (fromIndex == toIndex)
                throw new IllegalArgumentException("fromIndex == toIndex");
            final Account from = accounts[fromIndex];
            final Account to = accounts[toIndex];
            if (amount > from.amount)
                throw new IllegalStateException("Underflow");
            else if (amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)
                throw new IllegalStateException("Overflow");
            from.amount -= amount;
            to.amount += amount;
        } finally {
            accounts[fromIndex].lock.unlock();
            accounts[toIndex].lock.unlock();
        }
    }

    /**
     * Private account data structure.
     */
    static class Account {
        /**
         * Amount of funds in this account.
         */
        long amount;
        private final ReentrantLock lock = new ReentrantLock();
    }
}
