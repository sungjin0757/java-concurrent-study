package item78;

public class ProblemCode {
    private static volatile int nextSerialNumber = 0;

    public static int generateSerialNumber(){
        return nextSerialNumber++;
    }
}
