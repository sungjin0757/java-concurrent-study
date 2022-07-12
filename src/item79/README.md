## Item 79. 과도한 동기화는 피하라
***

과도한 동기화는 성능을 떨어뜨리고, 교착상태에 빠뜨리고, 심지어 예측할 수 없는 동작을 낳기도 합니다.

**응답 불가와 안전 실패를 피하려면 동기화 메서드나 동기화 블록 안에서는 제어를 절대로 클라이언트에 양도하면 안됩니다.**
***
예를 들어 동기화된 영역 안에서는 재정의할 수 잌ㅆ는 메서드는 호출하면 안 되며, 클라이언트가 넘겨준 함수 객체를 호출해서도 안됩니다.

동기화된 영역을 포함한 클래스 관점에서는 이런 메서드는 모두 바깥에상의 관점입니다. 외부의 메서드는 무슨 일을 할지 모르며, 통제할 수도 없습니다.
즉, 예외를 일으키거나, 교착상태에 빠지거나, 데이터를 훼손할 ㅜ 있습니다.

다음의 코드를 살펴봅시다.

```java
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
        synchronized (observers){
            for(SetObserVer<E> observer : observers){
                observer.added(this, element);
            }
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

```

이 클래스의 클라이언트는 집합에 원소가 추가되면 알림을 받을 수 있습니다. 바로 `Observer Pattern` 입니다.

관찰자들은 `addObserver` 와 `removeObserver` 메서드를 호출해 구독을 신청하거나 해지합니다.

```java
public class ObservalbeSetMain {
    public static void main(String[] args){
        ObservableSet<Integer> set = new ObservableSet<>(new HashSet<>());

        set.addObserver((s,e) -> System.out.println(e));

        for(int i = 0 ; i < 100; i++){
            set.add(i);
        }
    }
}
```

위의 코드는 잘 진행되는 것을 확인할 수 있습니다.

이제 다른 것을 시도해봅시다. 평상시에는 앞서와 같이 집합에 추가된 정숫값을 출력하다가, 그 값이 23이면 자기 자신을 제거하는 관찰자를 추가해봅시다.

```java
set.addObserver(new SetObserVer<Integer>() {
            @Override
            public void added(ObservableSet<Integer> set, Integer e) {
                System.out.println(e);
                if(e == 23){
                    set.removeObserver(this);
                }
            }
        });
```

이렇게 관찰자를 설정해두면 0부터 23 까지 출력한 후 관찰자 자신을 구독해지한 다음 종료할 것으로 보입니다.

그런데, 실제로 실행해보면 `ConcurrentModificationException` 이 발생합니다. 관찰자의 `added` 메서드 호출이 일어난 시점이
`notifyElementAdded` 가 관찰자들의 리스트를 순회하는 도중이기 때문입니다.

즉, 허용되지 않은 동작을 진행한 것입니다. `notifyElementAdded` 메서드에서 수행하는 순회는 동기화 블록 안에 있으므로 동시 수정이 일어나지 않도록 보장하지만,
정작 자신이 콜백을 거쳐 되돌아와 수정하는 것까지 막지는 못합니다.

이제는 데드락 상태를 유발하는 코드를 알아봅시다.

```java
set.addObserver(new SetObserVer<Integer>() {
            @Override
            public void added(ObservableSet<Integer> set, Integer e) {
                System.out.println(e);
                if(e == 23){
                    ExecutorService exec =
                            Executors.newSingleThreadExecutor();
                    try{
                        exec.submit(() -> set.removeObserver(this)).get();
                    }catch (ExecutionException | InterruptedException ex){
                        throw new AssertionError(ex);
                    }finally{
                        exec.shutdown();
                    }
                }
            }
        });
```

백그라운드 스레드가 `set.removeObserver` 를 호출하면 관찰자를 잠그려 시도하지만 락을 얻을 수 없습니다.
메인 스레드가 이미 락을 쥐고 있기 때문입니다. 그와 동시에 메인 스레드는 백그라운드 그레드가 관찰자를 제거하기만을 기다리는 중입니다.
바로 교착상태가 발생하게 되는 것이죠.

앞서의 두 예는 운이 좋습니다. 동기화 영역이 보호하는 자원(관찰자)은 외부 메서드(added)가 호출되리 때 일관된 상태여서입니다.

이런 문제는 대부분 어렵지 않게 해결할 수 있습니다.
외주 메서드 호출을 동기화 블록 바깥으로 옮기면 됩니다.

```java
public void notifyElementAdded(E element){
        List<SetObserVer<E>> snapshot = null;
        synchronized (observers){
            snapshot = new ArrayList<>(observers);
        }
        for(SetObserVer<E> observer : snapshot){
            observer.added(this, element);
        }
    }
```

외부 메서드 호출을 동기화 블록 바깥으로 옮기는 더 나은 방법이 있습니다.
자바의 동시성 컬렉션 라이브러리의 `CopyOnWriteArrayList` 입니다.
내부를 변경하는 작업은 항상 깨끗한 복사본을 만들어 수행하도록 구현됩니다.

외부 메서드는 얼마나 오래 실행될지 알 수 없는데, 동기화 영역 안에서 호출된다면 그 동안 다른 스레드는 보호된
자원을 사용하지 못하고 대기해야만 합니다.

<br></br>
**기본 규칙은 동기화 영역에서는 가능한 한 일을 적게하는 것입니다.**
***
락을 얻고, 공유 데이터를 검사하고, 필요하면 수정하고, 락을 놓습니다.

또한, 성능적으로도 멀티코어가 일반화된 오늘날, 과도한 동기화가 초래하는 진짜 비용은 락을 얻는데 드는 CPU 시간이 아닙니다.
바로 경쟁하느라 낭비하는 시간, 즉 병렬로 실행할 기회를 잃고, 모든 코어가 메모리를 일관되게 보기 위한 지연시간이 진짜 비용입니다.

가변 클래스를 작성하려거든 다음 두 선택지중 하나를 따라야 합니다.
1. 동기화를 전혀 하지 말고, 그 클래스를 동시에 사용해야 하는 클래스가 외부에서 알아서 동기화 하게 해야합니다.
2. 동기화를 내부에서 수행해 스레드 안전한 클래스로 만듭니다.