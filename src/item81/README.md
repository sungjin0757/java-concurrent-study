## Item 81. wait와 notify보다는 동시성 유틸리티를 애용하라
***

**wait와 notify는 올바르게 사용하기가 아주 까다로우니 고수준 동시성 유틸리티를 사용하자.**
***

`java.util.concurrent`의 고수준 유틸리티는 세 범주로 나눌 수 있습니다.
실행자 프레임워크, 동시성 컬렉션, 동기화 장치 입니다. 

동시성 컬렉션이란 표준 컬렉션 인터페이스에 동시성을 가미해 구현한 고성능 컬렉션 입니다.
높은 동시성에 도달하기 위해 동기화를 각자의 내부에서 수행합니다.

따라서, **동시성 컬렉션에서 동시성을 무력화하는 건 불가능하며, 외부에서 락을 추가로 사용하면 오히려 속도가 느려집니다.**

동시성 컬렉션에서 동시성을 무력화하지 못하므로 여러 메서드를 원자적으로 묶어 호출하는 일 역시 불가능합니다.

그래서 여러 기본 동작을 하나의 원자적 동작으로 묶는 '상태 의존적 수정' 메서드들이 추가되었습니다.

이 메서드들은 아주 유용헤서 일반 컬렉션 인터페이스에도 디폴트 메서드 형태로 추가되었습니다.

예를 들어 `Map` 의 `putIfAbsent(key, value)` 메서드는 주어진 키에 매핑된 값이 없을 때문 새 값을 집어 넣습니다.
그리고 기존 값이 있었다면 반환하고 없다면 `null`을 반환 합니다.

이러한, 메서드 덕에 스레드 안전한 정규화 맵을 쉽게 구현할 수 있습니다.

```java
public class NormalizationMap {
    private static final ConcurrentHashMap<String, String> map =
            new ConcurrentHashMap<>();

    public static String intern(String s){
        String previousValue = map.putIfAbsent(s, s);
        return previousValue == null ? s : previousValue;
    }
}
```

`ConcurrentMap` 은 `get` 같은 검색 기능에 최적화 되었습니다. 따라서 `get`을 먼저 호출하여
필요할 때만 `putIfAbsent` 를 호출하면 더 빠릅니다

```java
public class NormalizationMap {
    private static final ConcurrentHashMap<String, String> map =
            new ConcurrentHashMap<>();

    public static String intern(String s){
        String result = map.get(s);
        if(result == null){
            result = map.putIfAbsent(s, s);
            if(result == null)
                result = s;
        }
        return result;
    }
}
```

`ConcurrentHashMap`은 동시성이 뛰어나며 속도도 무척 빠릅니다.

이제는 `Collections.synchronizedMap` 보다는 `ConcurrentHashMap`을 사용하는게 훨씬 좋습니다.

컬렉션 인터페이스 중 일부는 작업이 성공적으로 완료될 때까지 기다리도록 확장되었습니다.
예를들어, `Queue`를 확장한 `BlockingQueue` 에 추가된 메서드 중 `take`는 큐의 첫 원소를 꺼냅니다.

이때 만약 큐가 비었다면 새로운 원소가 추가될 때 까지 기다립니다. 이런 특성 덕에 `BlockingQueue`는 작업큐로 쓰기에 적합합니다.

동기화 장치는 스레드가 다른 스레드를 기다릴 수 있게 하여, 서로 작업을 조율할 수 있게 해줍니다. `CountDownLatch` 와 `Semaphore`
는 가장 자주 쓰이는 동기화 장치입니다.

카운트다운 래치는 일회성 장벽으로, 하나 이상의 스레드가 또 다른 하나 이상의 스레드 작업이 끝날 때까지 기다리게 합니다.
`CountDownLatch`의 유일한 생성자는 `int` 가 값을 받으며, 이 값이 래치의 `countDown`메서드를 몇번 호출해야 대기 중인
스레드들을 깨우는지를 결정합니다.

`countDownLatch` 는 `wait`와 `notify` 만으로 구현하려면 아주 난해하고 지저분한 코드를 직관적으로 구현할 수 있습니다.

```java
public class SimpleFramework {
    /**
     * 어떤 동작들을 동시에 시작해 모두 완료하기까지의 시간을 재는 메서드
     * @param executor
     * @param concurrency
     * @param action
     * @return
     * @throws InterruptedException
     */
    public static long time(Executor executor, int concurrency,
        Runnable action) throws InterruptedException{
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);

        for(int i = 0; i < concurrency ; i++){
            executor.execute(()->{
                // 타이머에게 준비를 마쳤음을 말한다.
                ready.countDown();
                try{
                    //모든 작업자 스레드가 준비될 때까지 기다린다.
                    start.await();
                    action.run();
                }catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }finally {
                    //타이머가 작업을 마쳤음을 알린다.
                    done.countDown();
                }
            });
        }

        ready.await(); // 모든 작업자가 준비될 때까지 기다린다.
        long startNanos = System.nanoTime();
        start.countDown(); // 작업자들을 깨운다.
        done.await(); // 모든 작업자가 일을 끝마치기를 기다린다.
        return System.nanoTime() - startNanos;
    }
}
```

여기서, `countDownLatch`의 `countDown` 메서드는 `latch`의 개수를 1개 줄이는 것을 의미하며,
`await` 메서드는 `latch`의 개수가 0이 될 때까지 기다리는 것을 의미합니다.

이 코드는 카운트다운 래치를 3개 사용합니다.
`ready` 래치는 작업자 스레드들이 준비가 완료됐음을 타이머 스레드에 통지할 때 사용합니다.
통지를 끝낸 작업자 스레드들은 두 번째 래치인 `start` 가 열리기를 기다립니다.
마지막 작업자 스레드가 `ready.countDown` 을 호출하면 타이머 스레드가 시작 시각을 기록하고 `start.countDown`을
호출하여 기다리던 작업자 스레드들을 깨웁니다.

그 직후 타이머 스레드는 세 번째 래치인 `done`이 열리기를 기다립니다. `done` 래치는 마지막 남은
작업자 스레드가 동작을 마치고 `done.countDown` 을 호출하면 열립니다.
타이머 스레드는 `done` 래치가 열리자마자 깨어나 종료시각을 기록합니다.

메서드에 넘겨진 실행자는 `concurrency` 매개변수로 지정한 동시성 수준만큼의 스레드를 생성할 수 있어야합니다.
그렇지 않으면 메서드를 실행한 스레드에서 이 메서드를 끝낼 방도가 없으므로 교착 상태가 발생하게 됩니다.

**시간 간격을 잴 때는 항상 `System.currentTimeMills` 가 아닌 `System.nanoTime`을 사용해야 합니다.**
더욱 정밀하며 시스템의 실시간 시계의 시간 보정에 영향받지 않습니다.

이렇게 만약 새로운 코드를 짜야하는 상황이라면, `wait` 와 `notify` 가 아닌 동시성 유틸리티를 써야 합니다.

하지만 어쩔수 없이 레거시 코드를 사용할 때도 있을 것입니다. `wait` 메서드는 반드시 그 객체를 잠근 동기화 영역 안에서 호출해야합니다.

```java
synchronized(obj){
    while("조건")
        obj.wait();
}
```

**wait 메서드를 사용할 때눈 반드시 대기 반복문 관용구를 사용하고 반복문을 사용하면 안됩니다.**

대기전에 조건을 검사하여 조건이 이미 충족되었다면 `wait`를 건너뛰게 한 것은 응답 불가 상태를 예방하는 조치입니다.
만약 조건이 이미 충족되었는데 스레드가 `notify` 메서드를 먼저 호출한 후 대기 상태로 빠지면, 그 스레드를 다시 깨울수 없을 것입니다.

대기 후에 조건을 검사하여 조건이 충족되지 않았다면 다시 대기하게 하는 것은 안전 길패를 막는 조치입니다.
만약 조건이 충족되지 않았는데 스레드가 동작을 이어가면 락이 보호하는 불변식을 깨뜨릴 위험이 있습니다.

조건이 만족되지 않아도 스레드가 깨어날 수 있는 상황이 몇가지 있습니다.

1. 스레드가 `notify`를 호출한 다음 대기 중이던 스레드가 깨어나는 사이에 다른 스레드가 락을 얻어 그 락이 보호하는 상태를 변경한다.
2. 조건이 만족되지 않았음에도 다른 스레드가 실수로 혹은 악의적으로 `notify`를 호출한다. 곡개된 객체를 락으로 사용해 대기하는
클래스는 이런 위험에 노출된다. 외부에 노출된 객체의 동기화된 메서드 안에서 호출하는 `wait`는 모두 이 문제에 영향을 받는다.
3. 깨우는 스레드는 지나치게 관대해서, 대기중인 스레드 중 일부만 조건이 충족되어도 `notifyAll`을 호출해 모든 스레드를 깨울 수도 있습니다.
4. 대기 중인 스레드가 `notify` 없이도 깨어나는 경우가 있습니다. 허위 각성이라는 현상입니다.

이와 같은 조건에서 `notify` 와 `notifyAll`중 어떤 것을 써야할 지 모호할 수 있습니다.
일반적으로 언제나 `notifyAll`을 사용하라는게 합리적이고 안전한 조언언입니다.

다른 스레드까지 모두 깨어날 수도 있긴 하지만, 그것이 프로그램의 정확성에는 영향을 주지 않을 것입니다. 깨어난 스레드들은 기다리던 조건이 충족되었는지 확인하여,
충족되지 않았다면 다시 대기할 것입니다.

모든 스레드가 같은 조건을 기다리고, 조건이 한 번 충족될 때마다 단 하나의 스레드만 혜택을 받을 수 있다면 `notify`를 사용해 최적화할 수 있지만,
외부로 공개된 객체에 대해 실수로 혹은 악의적으로 `notify`를 호출하는 상황에 대비하기 위해 `wait` 를 반복문안에서 호출했듯, `notifyAll`을 사용하면 관련없는 스레드가
실수로 혹은 악의적으로 `wait`를 호출하는 공격으로 부터 보호할 수 있습니다.