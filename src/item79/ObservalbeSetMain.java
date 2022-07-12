package item79;

import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObservalbeSetMain {
    public static void main(String[] args){
        ObservableSet<Integer> set = new ObservableSet<>(new HashSet<>());

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

        for(int i = 0 ; i < 100; i++){
            set.add(i);
        }
    }
}
