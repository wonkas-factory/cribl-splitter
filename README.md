# Cribl Splitter

## Quick Start

Pull down code and run```./mvnw clean test``` 

Folders
1. ```node``` is the application folder
2. ```inputs``` contains all the inputs for the test
3. ```logs``` generated at run time are all the logs from the tests
4. ```target/surefire-reports/html/index.html``` generated at runtime is the test report


## Installation

The basic installations to run the code. Note this was mostly ran on a macOS

1.	Git: To pull the project down ```brew install git```
2.	Java 8: To run the test suite ```brew cask install adoptopenjdk8```
3.	Docker: To run the application under test. Any recent version should work was tested on 20.10.8, ```brew install --cask docker``` or [Docker website](https://docs.docker.com/engine/install/) 

## Investigations
The following below is a brief break down of things I noted before coding the the solution

### Objective

Automate the following tasks using language of your choice:

- [x] Download and install the provided application on each of the 4 hosts mentioned in the “Setup” section Note: The link to the application can be found in the “Resources” section below
- [x] For each “Application Mode”, there is a corresponding configuration directory in the provided package; please examine these files before proceeding. You will need to start the applications in the exact order: Targets, Splitter, Agent. Otherwise, the deployment may not function as expected. To start each application, run node app.js <conf_directory>
- [x] Automate the following test cases:
  - [x] Validates if data received on the ‘Target’ nodes are correct
  - [x] Optional: Any additional test cases that provides coverage
- [x] Capture all output and artifacts generated from each application/host

### Acceptance Criteria

- [x] Test suite with the automated test case as noted in the “Objectives” section
  - [x] Each test case should have documentation describing the purpose and goal of the test. **Note: "In the test execution file SplitterTests.java"**
- [x] Setup & teardown of the deployment must all be automated. **Note: "With docker-compose"**
- [x] Node.js application and configuration files should not be modified in any way with the exception of the “inputs.json” file. **Note: "inputs.json changed to reference inputs/input.log"**
- [x] Create a Github repository and add the following to it
  - [x] Test implementation
  - [x] README.md file documenting your approach and complete instructions for test execution outside of the CI environment
- [x] Integrate the Github repository with one of the publicly available CI/CD services
  - [x]Such as, but not limited to: GitHub, CircleCI, TravisCI. **Note: "GitHub actions used"**
- [x] The resulting submission should be a link to the Github repository README.mdwhich contains all necessary information for evaluating the solution


### Notes on the Nodejs Application Code 

Agent notes
- Files System Promise https://nodejs.org/api/fs.html#fs_promises_api 
  - Async file system methods – Node.js thread pool 
  - Used on outputs and inputs.json
- Net.createConnection is aysnc https://nodejs.org/api/net.html#net_net 
- Files System Read Stream https://nodejs.org/api/fs.html#fs_fs_createreadstream_path_options 
  - Buffer Default: 64 * 1024
  - Encoding default by buffer is UTF-8 https://nodejs.org/api/buffer.html#buffer_class_buffer 
  - Auto closes
- Stream pipe https://nodejs.org/en/knowledge/advanced/streams/how-to-use-stream-pipe/ 
  - Connect readable stream to write stream

Splitter notes
```code()	            
      // find new line if it exists. 
      //      Send 1st part to current socket (sockIdx)
      //      Send 2nd part to next socket(socket2). Make socket2 the current socket
      // If no new line exists, send to the current socket
```
- Switch between targets by sockIdx %= outSocks.length;
- If there is no new line in file it should only print to one events.log only since it would never switch
- Note the buffer is 64KB and search for the first line makes the output to two targets appear line by line

Target
- Nothing really to comment on this portion as it just appends what it gets to the events.log file https://nodejs.org/api/fs.html#fs_fs_appendfile_path_data_options_callback 

### Questions

These were some of the questions I had that would probably ask the dev and product team 
1.  When is simulation done to start verification?
    1.  Agent exits when done processing and can look for this event
2.  How to tests different sets of files?
    1.  Docker file to take in an arg/env variable
    2.  Overwrite the input.json with a common constant name. The input would then be copied and renamed
3.  How to test different main splitter application in isolation as this is the main component
    1.  Mock the targets –  listener
    2.  Script to send inputs to see behavior
4.  How to test different environmental setups
    1.	When one target is down can maybe use a separate compose file or docker command to take down
    1.	Additional targets not possible as can’t change configuration
    1.	Run out of storage space on volumes – docker compose to limit volumes
5.	How to manage different tests and files associated with the specific test
    1.	Since doing file I/O it's better to stream the files and do all the possible checks in one pass like 
        1.	Line by line content
        1.	Total counter
        1.	Possible have markers in file to mark different test scenario with something like the moustache {{{TC1}}} 
    1.	Each input file could be a new test and handle the file copying, renaming etc at programming level
    1.	Could use mvn surefire to pass in arguments corresponding to file being tested
6.	How to setup and teardown the environment easily for each test, since typically the system under test is always available can have proper test setup and teardown
    1.	Most likely use docker compose and could possibly use Kubernetes/minikube – however these are typically used with command line/shell and would need to find a way to integrate with automation framework

### Assumptions

The definition of testing for correctness if open for interpretation. Given that the application reads from a file and makes asynchronous calls to a server the context of the problem would be very similar to video streaming

1.	A certain level of corrupt packets is acceptable. Treating a log line as a simplified packet a line “This is event number (\\d+)” would be considered valid and anything else would be a corrupt packet/log line. The generally accepted packet loss is assumed to be 1-2.5%. In our testing, I will take a conservative rounded number of 3%. Reference https://en.wikipedia.org/wiki/Packet_loss#Acceptable_packet_loss 
2.	Similar to streaming the order is not guaranteed. The recreation of the order would more be an implementation of an application consuming the target outputs. What is more import is that the despite the order, the absolute content from the agent input to the targets don’t change. This would be checked. If time it would be interesting to try and recreate the order similar to how a video stream buffers with an appropriate buffer size relative to the default 64KB on Nodejs server. 
3.	The default buffer for Nodejs is by default UTF-8 and Java uses UTF-8 as well. However the Java implementation of the BufferedReader when reading a character is limited to the char primitive type The scope of the log verification would be limited to the values of a Java char primitive type of 65,535 https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html  whereas UTF-8 technically has 1,112,064 valid characters https://en.wikipedia.org/wiki/UTF-8 so the scope would be bound by the Java limitation for verification of the actual characters. 
4.	Assumed macOS or Unix type system is used because it is known that Windows encodes the line separate differently
    1.	Unix: ‘\n’
    1.	Window: ‘\r\n’
    1.	https://www.geeksforgeeks.org/system-lineseparator-method-in-java-with-examples/ 

### Test Cases

These are a brainstorming list of test cases. The final tests are located at SplitterTests.java along with the description of the purpose and goal.

1.	Scenario scans 1M events
    1.	File sizes adds up
2.	Log with all the same events
3.	Empty log file
4.	One line log file
5.	File does not exist
6.	Special characters
    1.	Covered in this test would be URI encoded text because of http
7.	UTF encoded text from here: https://toolslick.com/text/encoder/utf-8 
8.	Send a renamed binary Jpeg file
9.	Files less than buffer size
    1.	Covered in 4)
10.	Very long lines
    1.	Covered in 7)
11.	Entire file with \n
    1.	One, Two, Three
    1.	Multiple entries appear as empty
12.	Real logs – Apache access logs
13.	A file with characters from a different language
14.	Other if time
    1.	Target with limit/full storage
    1.	One target down
    1.	Handle more than 2 targets in future – verification code designed to handl
    1.	Stop in the middle of streaming – content still correct up to that point

### Dockerfile and Compose Files

- Reference: https://docs.docker.com/language/java/ 
- One docker file and pass in args for startup of applications
  - Node Reference: https://nodejs.org/en/docs/guides/nodejs-docker-webapp/ 
  - Docker Reference: https://docs.docker.com/engine/reference/builder/
  - Application command entry point at end to use arg/env to have only one docker file
  - Command to test
      - ```docker build --tag cribl-app --build-arg app=target .```
      - ```docker run -d --name cribl-target cribl-app```
- Docker compose file 
  - Reference: https://docs.docker.com/compose/compose-file/compose-file-v3/
  - pass in args for app type at build time and always build to copy input file for agent to run
  - binds for file outputs https://docs.docker.com/storage/bind-mounts/ 
    - Tried and was a bit inconsistent and prone to errors – like appending to existing events.log and depending when opening the file could have incomplete lines
    - Use the cp commands instead ```docker cp cribl-splitter_target_1_1:/usr/src/app/events.log ./logs/events1.log```
  -	Container logs other than events.log and console output doesn’t seem to be anything else useful in var/log locations https://www.cyberciti.biz/faq/linux-log-files-location-and-how-do-i-view-logs-files/ 
  - Commands Reference
    - ```docker-compose up --build -d```
    - ```docker-compose down```

### Test Automation Implementation

1.	The file name for the at ‘inputs/’ is named the same as the test method to make references and easier to run all tests
2.	Copy input test file from ‘inputs/<file>’ to ‘node/agent/inputs/input.json’ before each run. The inputs.json file for the agent was permanently changed to reduce dynamic references
3.	TestContainer to start docker compose file in Java - https://www.testcontainers.org/modules/docker_compose/ 
    1.	Otherwise run basic commands https://www.codejava.net/java-se/file-io/execute-operating-system-commands-using-runtime-exec-methods 
4.	Pull artifacts – event.log and container logs
5.	Run tests verification based on specific test. Would want to stream files for processing to be efficient as possible but generally want to check
    1.	Exact content match – hash map
    1.	Log file sizes – file system calls
    1.	Balanced splitter files
    1.	Corrupt log lines – regex mapping

### CI/CD – GitHub actions

- Docker reference: https://docs.docker.com/language/java/configure-ci-cd/ 
- Github actions reference - https://docs.github.com/en/actions 
- Additional reading: https://medium.com/@michaelekpang/creating-a-ci-cd-pipeline-using-github-actions-b65bb248edfe
- The default image has docker tools installed: https://github.com/actions/virtual-environments/blob/main/images/linux/Ubuntu2004-README.md 
- Reference of actions
  - Checkout: https://github.com/actions/checkout 
  - Java: https://github.com/actions/setup-java 
    - adopt
    - 8
  - Artifact: https://github.com/actions/upload-artifact
    - inputs/
    - logs/
    - target/surefire-reports
  - Try the matrix for different OS

### Future Considerations

1.	Efficiently try to recreate the log outputs with appropriate buffer sizes
2.	Running tests in parallel
3.	Additional docker compose configurations
    1.	Two agents
    1.	One target
    1.	Three or more targets
4.	Jenkins file
5.	Kubernetes minikube deployment - Ansible/Ninja files
6.	Deploying to cloud environment 

