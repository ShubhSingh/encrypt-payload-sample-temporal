# Java Hide Sensitive Info in Temporal Sample
This sample demonstrate the way to encrypt Sensitive info when it passes through Java Temporal client and server.
It utilizes Jackson Crypto to encrypt the sensitive UserInfo which needs to pass through temporal web and CLI.

## Setup

### macOS Specific
Due to issues with default hostname resolution 
(see [this StackOverflow question](https://stackoverflow.com/questions/33289695/inetaddress-getlocalhost-slow-to-run-30-seconds) for more details), 
macOS Users may see gRPC `DEADLINE_EXCEEDED` errors when running the samples or any other gRPC related code.

To solve the problem add the following entries to your `/etc/hosts` file (where my-macbook is your hostname):

```conf
127.0.0.1   my-macbook
::1         my-macbook
```

### Get the Hide Sensitive Info in Temporal Sample

Run the following commands:

     git clone https://github.com/ShubhSingh/encrypt-payload-sample-temporal.git
     cd encrypt-payload-sample-temporal

### Build the Samples

      ./gradlew build

### Import into IntelliJ

It is possible to run the samples from the command line, but if you prefer the IntelliJ here are the import steps:

* Navigate to **File**->**New**->**Project from Existing Sources**.
* Select the cloned directory.
* In the **Import Project page**, select **Import project from external model**
* Choose **Gradle** and then click **Next**
* Click **Finish**.

### Run Temporal Server

This sample requires Temporal service to run. We recommend a locally running version of Temporal Server 
managed through [Docker Compose](https://docs.docker.com/compose/gettingstarted/):

     curl -L https://github.com/temporalio/temporal/releases/latest/download/docker.tar.gz | tar -xz --strip-components 1 docker/docker-compose.yml
     docker-compose up

If this does not work, see the instructions for running Temporal Server at https://github.com/temporalio/temporal/blob/master/README.md.

## See Temporal UI

The Temporal Server running in a docker container includes a Web UI.

Connect to [http://localhost:8088](http://localhost:8088).

Click on a *RUN ID* of a workflow to see more details about it. Try different view formats to get a different level
of details about the execution history.

## Install Temporal CLI (tctl)

[Command Line Interface Documentation](https://docs.temporal.io/docs/tctl)


## EncryptPayload

EncryptPayload sample demonstrates one feature of the SDK in a single file. Note that single file format is 
used for sample brevity and is not something we recommend for real applications.

This sample has specific requirements for running it. The following instructs about
how to run it after you've built it using the preceding instructions.

  * **[ConverterApp](https://github.com/ShubhSingh/encrypt-payload-sample-temporal/blob/main/src/main/java/io/temporal/samples/dataconverter/ConverterApp.java)**: a single activity workflow which demonstrates usage of Custom DataConverter for encrypting and decrypting passing through temporal
  * **[EncryptPayloadApp](https://github.com/ShubhSingh/encrypt-payload-sample-temporal/blob/main/src/main/java/io/temporal/samples/encryption/EncryptPayloadApp.java)**: a single activity workflow which demonstrates a BAD way to use EncryptionUtil to encrypt and decrypt data passing through temporal
   
  To run these samples:
      ./gradlew -q execute -PmainClass=io.temporal.samples.dataconverter.ConverterApp
      ./gradlew -q execute -PmainClass=io.temporal.samples.encryption.EncryptPayloadApp
      
### Output of executed ConverterApp in Temporal web

As can be seen below "input" and "result" both are in the Encrypted JSON format.

![Temporal Web Output for ConverterApp](https://github.com/ShubhSingh/encrypt-payload-sample-temporal/blob/main/src/main/resources/example-converter.PNG)
      
### Output of executed EncryptPayloadApp in Temporal web

As can be seen below "input" is in the Encrypted JSON format and returned "result" from Workflow is in the decrypted JSON format. 

![Temporal Web Output for EncryptPayloadApp](https://github.com/ShubhSingh/encrypt-payload-sample-temporal/blob/main/src/main/resources/example.PNG)
