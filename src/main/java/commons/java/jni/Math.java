package commons.java.jni;

public class Math {

  static {
    System.loadLibrary("commons-java-1.0.0");
  }

  public static native int add(int a, int b);

}
