package commons.java.jni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MathTest {

  @Test
  public void testAdd() {
    assertEquals(3, Math.add(1, 2));
  }

}
