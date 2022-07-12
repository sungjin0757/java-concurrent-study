## Item 78. 공유 중인 가변 데이터는 동기화해 사용하라
***

`synchronized` 키워드는 해당 메서드나 블록을 한번에 한 스레드씩 수행하도록 보장합니다.

동기화는 한 객체가 일관된 상태를 가지고 생성되고, 이 객체에 접근하는 메서드는 그 객체에
을 겁니다.

락을 건 메서드는 객체의 상태를 확인하고 필요하면 수정합니다.
즉, 객체를 하나의 일관된 상태에서 다른 일관된 상태로 변화시킵니다. 동기화를 제대로 사용하면 
어떤 메서드도 이 객체의 상태가 일관되지 않은 순간을 볼 수 없을 것입니다.

동기화에는 중요한 기능이 한 가지 더 있습니다.
동기화 없이는 한 스레드가 만든 변화를 다른 스레드에서 확인하지 못할 수 있습니다.
동기화는 동기화된 메서드나 블록에 들어간 스레드가 같은 락의 보호하에 수행된 모든 이전 수정의 최종 결과를 보게해줍니다.

언어 명세상 `long`과 `double` 외의 변수를 읽고 쓰는 동작은 원자적입니다.
여러 스레드가 같은 변수를 동기화 없이 수정하는 중이라도, 항상 어떤 스레드가 정상적으로
저장한 값을 온전히 읽어옴을 보장한다는 뜻입니다.

그렇지만서도, "성능응ㄹ 높이기 위해 원자적 데이터를 읽고 쓸 때 동기화 하지 않는 것은" 매우 위험합니다.
자바 언어 명세는 스레드가 필드를 읽을 때 항상 '수정이 완전히 변영된' 값을 얻는다고 보장하지만,
한 스레드가 저장한 값이 다른 스레드에게 '보이는가'는 보장하지 않습니다.

**동기화는 배타적 실행뿐 아니라 스레드 사이의 안정적인 통신에 꼭 필요합니다.**
***
이는 한 스레드가 만든 변화가 다른 스레드에게 언저 어떻게 보이는지를 규정한 자바의 메모리 모델 때문입니다.


공유 중인 가변 데이터를 비록 원자적으로 읽고 쓸 수 있을지라도 동기화에 실패하면 처참한 결과로 이끌수 있습니다.

다음의 잘못된 코드를 봅시다.
```java
public class StopThread {
    private static boolean stopRequested;

    public static void main(String[] args)
        throws InterruptedException{
        Thread backgroundThread = new Thread(()->{
            int i = 0;
            while(!stopRequested){
                i++;
            }
        });
        backgroundThread.start();

        TimeUnit.SECONDS.sleep(1);
        stopRequested = true;
        
    }
}
```

위의 코드를 보면 1초 후에 종료되리라 생각됩니다. 메인 스레드가 1초 후 `stopRequested`를 `true`로 설정하면
`backgroundThread`는 반복문을 빠져나올것처럼 보이기 때문입니다. 하지만, 이 프로그램은 끝나지 않습니다.

원인은 동기화에 있습니다. 동기화하지 않으면 메인 스레드가 수정한 값을 백그라운드 스레드가 언제쯤에나 보게될지 보증할 수 없기 때문입니다.

`stopRequested` 필드를 동기화해 접근하면 이 문제를 해결할 수 있습니다.

```java
package item78;

import java.util.concurrent.TimeUnit;

public class StopThread {
    private static boolean stopRequested;

    private static synchronized void requestStop(){
        stopRequested = true;
    }

    private static synchronized boolean stopRequested(){
        return stopRequested;
    }

    public static void main(String[] args)
        throws InterruptedException{
        Thread backgroundThread = new Thread(()->{
            int i = 0;
            while(!stopRequested()){
                i++;
            }
        });
        backgroundThread.start();

        TimeUnit.SECONDS.sleep(1);
        requestStop();

    }
}

```

이런 식으로 코드를 작성하게 되면, 정확히 1초 후에 프로그램이 종료하는 것을 볼 수 있습니다.

여기서 주목해야할 점은, `read`와 `write` 과정을 모두 동기화 했다는 점입니다.
두 과정 모두 동기화되지 않으면 동작을 보장하지 않습니다.

동기화는 배타적 수행과 스레드 간 통신이라는 두 가지 기능을 수행하는데, 이 코드에서는 통신 목적으로만 사용된 것입니다.

**이 코드 보다도 속도를 더 빠르게 할 수 있다고 합니다**

```java
public class StopThreadV2 {
    private static volatile boolean stopRequested;

    public static void main(String[] args)
        throws InterruptedException{
        Thread backgroundThread = new Thread(()->{
            int i = 0;
            while(!stopRequested){
                i++;
            }
        });
        backgroundThread.start();

        TimeUnit.SECONDS.sleep(1);
        stopRequested = true;

    }
}

```

`volatile` 을 선언한 것을 보실 수 있습니다.
`volatile` 은 배타적 수행과는 상관없지만 항상 최근에 기록된 값을 읽게 됨을 보장합니다.

즉, 배타적 상황을 위해서 `volatile` 을 사용하면 안됩니다.

```java
public class ProblemCode {
    private static volatile int nextSerialNumber = 0;

    public static int generateSerialNumber(){
        return nextSerialNumber++;
    }
}
```

이 메서드는 매번 고유한 값을 반환할 의도로 만들어졌습니다. 이 메서드의 상태는 단 하나의 필드로 결정 되며,
원자적으로 접근할 수 있고 어떤 값이든 허용합니다.

따라서 굳이 동기화를 하지 않아도 될 것 같지만 동기화를 하지 않으면 올바로 동작하지 않습니다.

문제는 증가 연산자 때문입니다. 먼저 값을 일고, 그런다음 새로운 값을 증가하는 메서드 입니다.
만약 두 번째 스레드가 이 두 접근 사이를 비집고 들어와 값을 읽어가면 첫 번째 스레드와 똑같은 값을 돌려받게 됩니다.

프로그램이 잘못된 결과를 계산해내는 이런 오류를 안전 실패라고 합니다.

이 문제를 해결하기 위해서는, 메서드에 `synchronized` 키워드를 분여야 합니다.

동시에 호출하더라고 서로 간섭하지 않게 되기 때문입니다. 메서드에 `synchronized`를 붙였다면 `volatile`을 제거하면 됩니다.

<br></br>
이보다 더욱 우수한 방법이 있습니다. 바로 `AtomicLong`을 사용하는 것입니다.
`volatile`은 동기화의 두 효과 중 통신 쪽만 지원하지만 이 패키지는 원자성까지 지원합니다.

```java
import java.util.concurrent.atomic.AtomicLong;

public class ProblemCode {
    private static AtomicLong nextSerialNumber = 0;

    public static int generateSerialNumber() {
        return nextSerialNumber++;
    }
}
```

이렇게 가변 데이터를 동기화 하는 방법들을 알아보았습니다.

물론, 위의 방식들로 동기화하는 방법도 있지만, 가장 좋은 방법은 애초에 가변 데이터를 공유하지 않는 것입니다.
가변 데이터는 단일 스레드에서만 쓰도록 하는 것이 좋습니다.