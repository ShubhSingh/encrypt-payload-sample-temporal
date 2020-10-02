package io.temporal.samples.encryption;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encrypt Payload Temporal workflow that executes a single activity. Requires a local instance the
 * Temporal service to be running.
 */
public class EncryptPayloadActivity {

  private static final Logger log = LoggerFactory.getLogger(EncryptPayloadActivity.class);

  static final EncryptionUtil encryptionUtil = new EncryptionUtil();
  static final String TASK_QUEUE = "EncryptPayloadActivity";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  @WorkflowInterface
  public interface EncryptWorkflow {
    @WorkflowMethod
    Signup executeSignup(String encryptedRequest);
  }

  /** Activity interface is just a POJI. */
  @ActivityInterface
  public interface EncryptActivities {
    @ActivityMethod
    Signup decryptSignupRequest(String encrypted);
  }

  /** EncryptWorkflow implementation that calls EncryptActivities#decryptSignupRequest. */
  public static class EncryptWorkflowImpl implements EncryptWorkflow {

    private static final Logger log = LoggerFactory.getLogger(EncryptWorkflowImpl.class);
    /**
     * Activity stub implements activity interface and proxies calls to it to Temporal activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final EncryptActivities activities =
        Workflow.newActivityStub(
            EncryptActivities.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(2)).build());

    @Override
    public Signup executeSignup(String encrypted) {

      log.info("Executing currently encrypted request: {}", encrypted);

      // This is a blocking call that returns only after the activity has completed.
      return activities.decryptSignupRequest(encrypted);
    }
  }

  static class EncryptActivitiesImpl implements EncryptActivities {
    @Override
    public Signup decryptSignupRequest(String encryptedSignup) {
      return encryptionUtil.getDecryptedSignup(encryptedSignup);
    }
  }

  public static void main(String[] args) {
    // gRPC stubs wrapper that talks to the local docker instance of temporal service.
    WorkflowServiceStubs service = WorkflowServiceStubs.newInstance();
    // client that can be used to start and signal workflows
    WorkflowClient client = WorkflowClient.newInstance(service);

    // worker factory that can be used to create workers for specific task queues
    WorkerFactory factory = WorkerFactory.newInstance(client);
    // Worker that listens on a task queue and hosts both workflow and activity implementations.
    Worker worker = factory.newWorker(TASK_QUEUE);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(EncryptWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new EncryptActivitiesImpl());
    // Start listening to the workflow and activity task queues.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    // Uses task queue from the GreetingWorkflow @WorkflowMethod annotation.
    EncryptWorkflow workflow =
        client.newWorkflowStub(
            EncryptWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    // Execute a workflow waiting for it to complete. See {@link
    // io.temporal.samples.hello.HelloSignal}
    // for an example of starting workflow without waiting synchronously for its result.

    Signup request =
        Signup.builder()
            .id("s1")
            .userInfo(
                UserInfo.builder()
                    .userName("user1")
                    .password("Wow!123")
                    .address("Chennai TN")
                    .build())
            .build();

    String encryptedSignupRequest = null;
    try {
      encryptedSignupRequest = encryptionUtil.getEncryptedSignup(request);
    } catch (JsonProcessingException e) {
      log.error("Failed to encrypt Signup request: {}", request);
    }
    // Trigger workflow
    Signup response = workflow.executeSignup(encryptedSignupRequest);

    log.info("Finally decrypted signup request received back: {}", response);
    System.exit(0);
  }
}
