import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Темников Алексей
 */
public class Solution implements Lock<Solution.Node> {
    private final Environment env;

    private final AtomicReference<Node> tail = new AtomicReference<>(null);
    public Solution(Environment env) {
        this.env = env;
    }

    @Override
    public Node lock() {
        final Node my = new Node(); // сделали узел
        final Node pred = tail.getAndSet(my);
        if (pred != null) {
            my.locked.set(true);
            pred.next.set(my);
            while (my.locked.get()) {
                env.park();
            }
        }
        return my; // вернули узел
    }

    @Override
    public void unlock(final Node node) {
        if (node.next.get() == null) {
            if (tail.compareAndSet(node, null)) {
                return;
            }
            while (node.next.get() == null) {
                continue;
            }
        }
        node.next.get().locked.set(false);
        env.unpark(node.next.get().thread);
    }

    static class Node {
        final Thread thread = Thread.currentThread(); // запоминаем поток, которые создал узел
        final AtomicReference<Boolean> locked = new AtomicReference<>(false);
        final AtomicReference<Node> next = new AtomicReference<>(null);
    }
}
