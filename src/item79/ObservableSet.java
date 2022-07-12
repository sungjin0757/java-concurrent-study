package item79;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ObservableSet<E> extends ForwardingSet<E> {
    public ObservableSet(Set<E> set){
        super(set);
    }

    private final List<SetObserVer<E>> observers = new ArrayList<>();

    public void addObserver(SetObserVer<E> observer){
        synchronized (observers){
            observers.add(observer);
        }
    }

    public boolean removeObserver(SetObserVer<E> observer){
        synchronized (observers){
            return observers.remove(observer);
        }
    }

    public void notifyElementAdded(E element){
        List<SetObserVer<E>> snapshot = null;
        synchronized (observers){
            snapshot = new ArrayList<>(observers);
        }
        for(SetObserVer<E> observer : snapshot){
            observer.added(this, element);
        }
    }

    @Override
    public boolean add(E e) {
        boolean added = super.add(e);
        if(added){
            notifyElementAdded(e);
        }
        return added;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean result = false;
        for(E element : c)
            result |= add(element);
        return result;
    }
}
