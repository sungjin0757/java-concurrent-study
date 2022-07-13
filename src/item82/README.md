## Item 82. 스레드 안전성 수준을 문서화하라
***

한 메서드를 여러 스레드가 동시에 호출할 때 그 메서드가 어떻게 동작하느냐는 해당 클래스와
이를 사용하는 클라이언트 사이의 중요한 계약과 같습니다.

API 문서에 `synchronized` 한정자가 보이는 메서드는 스레드 안전하다는 이야기는
몇 가지 면에서 틀렸습니다. 자바독이 기본 옵션에서 생성한 API 문서에는 `synchronized` 한정자가
포함 되지 않습니다.

**메서드 선언에 `synchronized` 한정자를 선언할지는 구현 이슈일 뿐 API에 속하지 않습니다.**

**멀티스레드 환경에서도 API를 안전하게 사용하게 하려면 클래스가 지원하는 스레드 안정성 수준을
정확히 명시해야 합지다.**

다음 목록은 스레드 안정성이 높은 순으로 나열한 것입니다.

- 불변(immutable) : 이 클래스의 인스턴스는 마치 상수와 같아서 외부 동기화도 필요 없습니다. `String`, `Long`, `BigInteger`가 대표적입니다.
- 무조건적 스레드 안전 : 이 클래스의 인스턴스는 수정될 수 있으나, 내부에서 충실히 동기화하여 별도의 외부 동기화 없이 동시에 사용해도 안전합니다. `AtomicLong`, `ConcurrentHashMap` 이 여기에 속합니다.
- 조건부 스레드 안전 : 무조건적 스레드 안전과 같으나, 일부 메서드는 동시에 사용하려면 외부 동기화가 필요합니다. `Collections.synchronized` 래퍼 메서드가 반환한 컬렉션들이 여기에 속합니다.
- 스레드 안전하지 않음 : 이 클래스의 인스턴스는 수정될 수 있습니다. 동기화에 사용하려면 동기화 선언을 해야하며 일반적인 `Collections`들이 여기에 속합니다.
- 스레드 적대적 : 이 클래스는 모든 메서드 호출을 외부 동기화로 감싸더라도 멀티스레드 환경에서 안전하지 않습니다. 이 수준의 클래스는 일반적으로 정적 데이터를 아무 동기화 없이 수정합니다.
스레드 적대적으로 밝혀진 클래스나 메서드는 일반적으로 문제를 고쳐 재배포하거나 `deprecated` API로 지정합니다.

조건부 스레드 안전한 클래스는 주의해서 문서화 해야합니다. 어떤 순서로 호출할 때 외부 동기화가 필요한지, 그리고 그 순서로 호출하려면 어떤 락 혹은 락들을을 얻어야
하는지 알려줘야 합니다.

예를 들어 `Collections.synchronizedMap` 의 API 문서에는 다음과 같이 써 있습니다.
```java
/**
     * Returns a synchronized (thread-safe) map backed by the specified
     * map.  In order to guarantee serial access, it is critical that
     * <strong>all</strong> access to the backing map is accomplished
     * through the returned map.<p>
     *
     * It is imperative that the user manually synchronize on the returned
     * map when traversing any of its collection views via {@link Iterator},
     * {@link Spliterator} or {@link Stream}:
     * <pre>
     *  Map m = Collections.synchronizedMap(new HashMap());
     *      ...
     *  Set s = m.keySet();  // Needn't be in synchronized block
     *      ...
     *  synchronized (m) {  // Synchronizing on m, not s!
     *      Iterator i = s.iterator(); // Must be in synchronized block
     *      while (i.hasNext())
     *          foo(i.next());
     *  }
     * </pre>
     * Failure to follow this advice may result in non-deterministic behavior.
     *
     * <p>The returned map will be serializable if the specified map is
     * serializable.
     *
     * @param <K> the class of the map keys
     * @param <V> the class of the map values
     * @param  m the map to be "wrapped" in a synchronized map.
     * @return a synchronized view of the specified map.
     */
    public static <K,V> Map<K,V> synchronizedMap(Map<K,V> m) {
        return new SynchronizedMap<>(m);
    }
```

클래스가 외부에서 사용할 수 있는 락을 제공하면 클라이언트에서 일련의 메서드 호출을 원자적으로 수행할 수 있습니다.
하지만, 이 유연성에는 대가가 따릅니다. 내부에서 처리하는 고성능 동시성 제어 메커니즘과 혼용할 수 없게 되는 것입니다.
그래서 `ConcurrentHashMap` 같은 동시성 컬렉션과는 함께 사용하지 못합니다.
또한, 클라이언트가 공개된 락을 오래 쥐고 놓지 않는 서비스 거부 공격을 수행할 수도 있습니다.

서비스 거부 공격을 막으려면 `synchronized` 메서드 대신 비공개 락 객체를 사용해야 합니다.

```java
private final Object lock = new Object();

public void fooI){
    synchronized(lock){
        
    }    
}
```

**`lock`을 `final`로 만든 이유는 우연히라도 락 객체가 교체되는 일을 예방 해줍니다. 즉, `lock`필드의 변경가능성을 최소화 한것입니다.**

비공개 락 객체 관용구는 무조건적 스레드 안전 클래스에서만 사용할 수 있습니다. 조건부 스레드 안전 클래스에서는 특정 호출 순서에 필요한 락이 무엇인지를
클라이언트에게 알려줘야하므로 이 관용구를 사용할 수 없습니다.