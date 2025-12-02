package commons.java.annotations;

import commons.java.test.util.RandomTestClass;
import commons.java.test.util.TestClassForBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestComponentInjection {

  @Inject
  TestClassForBean testClassForBean;

  TestClassForBean testClassWithoutAnnotation;

  @Inject
  RandomTestClass randomTestClass;

  @Test
  void test() {
    // Injected bytecode by the transformer initializes the field
    assertNotNull(testClassForBean);
    // No annotation, object is not injected
    assertNull(testClassWithoutAnnotation);
    // No bean annotation on the class, object is not injected
    assertNull(randomTestClass);
  }

}
