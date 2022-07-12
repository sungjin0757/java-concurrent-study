package item79;

@FunctionalInterface
public interface SetObserVer<E> {
    void added(ObservableSet<E> set, E element);
}
