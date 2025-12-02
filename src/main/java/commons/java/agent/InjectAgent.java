package commons.java.agent;

import java.lang.instrument.Instrumentation;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InjectAgent {

  private static final Logger logger = Logger.getLogger(InjectAgent.class.getName());

  public static void premain(String agentArgs, Instrumentation inst) {
    logger.log(Level.WARNING, "[InjectAgent] Starting agent to transform classes.");
    inst.addTransformer(new InjectTransformer());
  }
}
