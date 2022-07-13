## Item83. 지연 초기화는 신중히 사용하라
***
지연 초기화는 필드의 초기화 시점을 그 값이 처음 필요할 때까지 늦추는 기법입니다.

그래서 값이 전혀 쓰이지 않으면 초기화도 결코 일어나지 않습니다.
지연 초기화는 주로 최적화 용도로 쓰이지만, 클래스와 인스턴스 초기화 때 발생하는 위험한 순환 문제를 해결하는 효과도 있습니다.

지연 초기화는 양날의 검입니다. 클래스 혹은 인스턴스 생성 시의 초기화 비용은 줄지만 그 대신 지연 초기화하는 필드에 접근하는 비용은 커집니다.
지연 초기화하려는 필드들 중 결국 초기화가 이뤄지는 비용에 따라, 실제 초기화에 드는 비용에 따라, 초기화된 각 필드를
얼마나 빈번히 호출하느냐에 따라 지연 초기화가 실제로는 성능을 느려지게 할 수 있습니다.

그럼에도 지연 초기화가 필요할 때가 있습니다. 해당 클래스의 인스턴스 중 그 필드를 사용하는 인스턴스의 비율이 낮은 반면, 그 필드를 초기화하는 비용이 크다면 
지연 초기화가 제 역할을 해줄 것입니다.

멀티스레드 환경에서는 지연 초기화를 하기가 까다롭습니다. 지연 초기화하는 필드를 둘 이상의 스레드가
공유한다면 어떤 형태로든 반드시 동기화 해야합니다.

**대부분의 상황에서 일반적인 초기화가 지연 초기화보다 낫습니다.**

다음은 인스턴스 필드를 선언할 때 수행하는 일반적인 초기화의 모습입니다.

```java
private final FieldType field = computeFieldValue();
```

**지연 초기화가 초기화 순환성을 깨뜨릴 것 같으면 `synchronized`를 단 접근자를 사용합시다.**

```java
private FieldType field;

private synchronized FieldType getField(){
    if(field == null)
        field = computeFieldValue();
    return field;
}
```

**성능 때문에 정적 필드를 지연 초기화해야 한다면 지연 초기화 홀더 클래스 관용구를 사용합니다.**

```java
private static class FieldHolder{
    static final FieldType field = computeFieldValue();
}

private static FieldType getField(){
    return FieldHolder.field;
}
```

이 관용구의 멋진 점은 `getField`메서드가 필드에 접근하면서 동기화를 전혀 하지 않으니 성능이 느려질 거리가 없습니다.

**성능 때문에 인스턴스 필드를 지연 초기화해야 한다면 이중검사 관용구를 사용해야합니다.**
이 관용구는 초기화된 필드에 접근할 때의 동기화 비용을 없애줍니다. 필드의 값을 두 번 검사하는 방식으로, 한 번은 동기화 없이 검사하고,
(필드가 아직 초기화 되지 않았다면) 동기화하여 검사합니다. 두 번째 검사에서도 필드가 초기화 되지 않았을 때만 필드를 초기화 합니다.

필드가 초기화된 후로는 동기화하지 않으므로 해당 필드은 반드시 `volatile`로 선언해야 합니다.

```java
private volatile FieldType field;

private FieldType getField(){
    FieldType result = field;
    if(result != null)
        return result;
    
    synchronized(this){
        if(field == null)
            field = compteFieldValue();
        return field;
    }
}
```

이중검사를 정적 필드에도 적용할 수 있지만 굳이 그럴 이유는 없이 홀더 클래스 방식이 더 빠릅니다.

이중검사에도 변종이 있습니다. 반복해서 초기화 해도 상관없는 인스턴스 필드를 지연 초기화해야할 때가 있는데,
이런 경우는 이중 검사에서 두 번째 검사를 생략할 수 있습니다.

```java
private volatile FieldType field;

private FieldType getField(){
    FieldType result = field;
    if(result == null)
        field = result = computeField();
    return result;
}
```

모든 스레드가 필드의 값을 다시 계산해도 상관없고 필드의 타입이 `long`, `double` 을 제외한 다른 기본 타입이라면, 단일검사의
필드 선언에서 `volatile`한정자를 없애도 됩니다. 

이 관용구는 어떤 환경에서는 필드 접근 속도를 높여주지만, 초기화가 스레드당 최대 한번 더 이뤄질 수 있습니다. 보통은 거의 쓰이지 않습니다.