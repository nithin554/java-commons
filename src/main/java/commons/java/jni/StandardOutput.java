package commons.java.jni;

public class StandardOutput {

  static {
    System.loadLibrary("commons-java-1.0.0");
  }

  public static native void printStr(String str);

}
