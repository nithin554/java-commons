package commons.java.agent;

import commons.java.annotations.Bean;
import commons.java.annotations.Inject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bytecode transformer to initialize beans.<br />
 * This is a basic implementation and ignores many checks.
 */
public class InjectTransformer implements ClassFileTransformer {

  private static final String INJECT_ANNOTATION_DESCRIPTOR = "Lcommons/java/annotations/Inject;";
  private static final String BEAN_ANNOTATION_DESCRIPTOR = "Lcommons/java/annotations/Bean;";
  private static final Logger logger = Logger.getLogger(InjectTransformer.class.getName());

  /**
   * Super class bytecode instruction looks like this<br />
   * <code>Invoke[OP=INVOKESPECIAL, m=java/lang/Object.&ltinit&gt()V]</code> Which
   * is interpreted as opcode=INVOKESPECIAL, superClass=java/lang/Object,
   * methodName=&ltinit&gt, methodDescriptor=()V
   */
  private static final BiPredicate<ClassModel, CodeElement> isASuperCallInstruction = (transformingClassModel,
                                                                                       codeElement) -> codeElement instanceof InvokeInstruction instr && instr.opcode() == Opcode.INVOKESPECIAL
      && instr.name().stringValue().equals("<init>") && transformingClassModel.superclass().isPresent()
      && instr.owner().asSymbol().equals(transformingClassModel.superclass().get().asSymbol());

  /**
   * Identifies if the class needs transformation by reading the annotations of
   * all fields
   *
   * @param classModel
   *          ClassModel object representing the class being transformed
   * @return true if the class needs transformation, false otherwise
   */
  private boolean needsTransformation(ClassModel classModel) {
    return classModel.fields().stream().flatMap(fieldModel -> fieldModel.attributes().stream())
        .filter(attribute -> attribute instanceof RuntimeVisibleAnnotationsAttribute)
        .map(RuntimeVisibleAnnotationsAttribute.class::cast).anyMatch(a -> a.annotations().stream()
            .anyMatch(annotation -> annotation.classSymbol().descriptorString().equals(INJECT_ANNOTATION_DESCRIPTOR)));
  }

  /**
   * Loop over all class fields and find fields which is marked with the
   * {@link Inject} annotation
   *
   * @param classModel
   *          ClassModel object representing the class being transformed
   * @return Map of field names and their descriptors
   */
  private Map<String, String> getInjectableFields(ClassModel classModel) {
    Map<String, String> fieldNameDescriptorsMap = new HashMap<>();
    for (FieldModel fieldModel : classModel.fields()) {
      for (Attribute<?> attribute : fieldModel.attributes()) {
        if (attribute instanceof RuntimeVisibleAnnotationsAttribute rva) {
          for (Annotation annotation : rva.annotations()) {
            if (INJECT_ANNOTATION_DESCRIPTOR.equals(annotation.classSymbol().descriptorString())) {
              fieldNameDescriptorsMap.put(fieldModel.fieldName().stringValue(), fieldModel.fieldType().stringValue());
            }
          }
        }
      }
    }
    return fieldNameDescriptorsMap;
  }

  /**
   * Loop over the annotations of a class to find the {@link Bean} annotation
   *
   * @param classModel
   *          ClassModel object representing the class being transformed
   * @return true if the class has the {@link Bean} annotation, false otherwise
   */
  private boolean beanAnnotationExists(ClassModel classModel) {
    boolean hasBeanAnnotation = false;
    for (Attribute<?> attribute : classModel.attributes()) {
      if (attribute instanceof RuntimeVisibleAnnotationsAttribute rva) {
        for (Annotation annotation : rva.annotations()) {
          if (BEAN_ANNOTATION_DESCRIPTOR.equals(annotation.classSymbol().descriptorString())) {
            hasBeanAnnotation = true;
          }
        }
      }
    }
    return hasBeanAnnotation;
  }

  /**
   * Load class using class descriptor and find the {@link Bean} annotation
   *
   * @param fieldClassDesc
   *          Class descriptor of the bean
   * @return true if the class has the {@link Bean} annotation, false otherwise
   */
  private boolean beanAnnotationExists(ClassDesc fieldClassDesc) {
    ClassLoader classLoader = InjectTransformer.class.getClassLoader();
    String className = fieldClassDesc.displayName();
    String packageName = fieldClassDesc.packageName();
    // convert commons.java.test.util.TestClassForBean to
    // commons/java/test/util/TestClassForBean.class
    String resourcePath = packageName.concat(String.valueOf(File.separatorChar)).concat(className).replace('.',
        File.separatorChar) + ".class";
    try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
      if (is == null) {
        return false;
      }
      byte[] classBytes = is.readAllBytes();
      return beanAnnotationExists(ClassFile.of().parse(classBytes));
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Builds a method transformer to inject bytecode into constructor and
   * initialize beans
   *
   * @param transformingClassModel
   *          ClassModel object representing the class being transformed
   * @param fieldNameDescriptorsMap
   *          Map of field names and their descriptors
   * @param transformingClassName
   *          Name of the class being transformed
   * @return Method transformer to inject bytecode into constructor
   */
  private MethodTransform getConstructorTransformer(ClassModel transformingClassModel,
                                                    Map<String, String> fieldNameDescriptorsMap, String transformingClassName) {
    return MethodTransform.transformingCode((codeBuilder, codeElement) -> {
      // Proceed with original constructor instruction until we encounter a super call
      // instruction
      codeBuilder.with(codeElement);
      // Once the super call is finished add additional bean initialization bytecode
      // instructions
      if (isASuperCallInstruction.test(transformingClassModel, codeElement)) {
        for (Map.Entry<String, String> entry : fieldNameDescriptorsMap.entrySet()) {
          // Retrieve class descriptor of the bean field
          ClassDesc classDesc = ClassDesc.ofDescriptor(entry.getValue());
          // Check if bean annotation exists on the class
          if (!beanAnnotationExists(classDesc)) {
            continue;
          }
          // aload(0) -> Load a reference from local variable at index 0, which is
          // basically this object
          // Bytecode Instruction Stack : [this]
          codeBuilder.aload(0);
          // new_(classDesc) -> Create a new instance of the bean class and pushes it to
          // the stack
          // Bytecode Instruction Stack : [this, bean]
          codeBuilder.new_(classDesc);
          // Duplicate the last instruction on the stack and pushes it to stack again.
          // This duplicates the reference to the new instance of the bean class
          // This is necessary on next step as invokespecial() pops the reference from the
          // stack
          // Bytecode Instruction Stack : [this, bean, bean]
          codeBuilder.dup();
          // Pop the object reference and invoke instruction with opcode "INVOKESPECIAL"
          // to call the
          // constructor of the bean
          // Bytecode Instruction Stack : [this, bean]
          codeBuilder.invokespecial(classDesc, "<init>", MethodTypeDesc.ofDescriptor("()V"));
          // Pops 2 references from stack, one is the object to set and the other is the
          // object reference
          // where the field needs to be set.
          // Insert the bean object to the field in the transforming class
          // Bytecode Instruction Stack : []
          codeBuilder.putfield(ClassDesc.ofDescriptor("L" + transformingClassName + ";"), entry.getKey(), classDesc);
        }
      }
    });
  }

  /**
   * Builds a class transformer to inject bytecode into constructor and initialize
   * beans
   *
   * @param transformingClassModel
   *          ClassModel object representing the class being transformed
   * @param fieldNameDescriptorsMap
   *          Map of field names and their descriptors
   * @param transformingClassName
   *          Name of the class being transformed
   * @return Class transformer to inject bytecode into constructor
   */
  private ClassTransform getClassTransformer(ClassModel transformingClassModel,
                                             Map<String, String> fieldNameDescriptorsMap, String transformingClassName) {
    return (classBuilder, classElement) -> {
      // Search for constructor to start injecting bytecode which initializes beans
      if (classElement instanceof MethodModel methodModel && "<init>".equals(methodModel.methodName().stringValue())) {
        classBuilder.transformMethod(methodModel,
            getConstructorTransformer(transformingClassModel, fieldNameDescriptorsMap, transformingClassName));
      } else {
        // Proceed with existing instructions if the element is not a constructor
        classBuilder.with(classElement);
      }
    };
  }

  @Override
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classFileBuffer) {
    // Avoid transforming JDK classes or the agent itself
    if (className == null || className.startsWith("java/") || className.startsWith("javax/")
        || className.startsWith("sun/") || className.startsWith("commons/java/agent")) {
      return classFileBuffer;
    }

    try {
      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classFileBuffer);
      if (needsTransformation(classModel)) {
        logger.log(Level.WARNING, "[InjectTransformer] Transforming class: " + className.replace('/', '.'));
        Map<String, String> fieldNameDescriptorsMap = getInjectableFields(classModel);
        if (!fieldNameDescriptorsMap.isEmpty()) {
          return cf.transformClass(classModel, getClassTransformer(classModel, fieldNameDescriptorsMap, className));
        }
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error transforming class " + className, e);
    }
    return classFileBuffer;
  }
}
