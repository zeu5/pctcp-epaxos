# Randomized Testing of Cassandra using PCTCP Algorithm

This repository keeps the source code for the experimental evaluation for testing Cassandra 2.0.0 using PCTCP (Probabilistic Concurrency Testing with ChainPartitioning) algorithm.

Our implementation is built on top of the [SAMC/DMCK] (https://github.com/ucare-uchicago/DMCK) model checker. 

## Contents 

The `$HOME/DMCK` folder mainly contains the source code for DMCK tool in the `$HOME/DMCK/src` directory which we extended with our PCTCP implementation, a compressed file for Cassandra 2.0.0 in `$HOME/DMCK/dmck-target-systems` directory which is the system under test and the folder `$HOME/DMCK/cassandra-2.0.0`, which contains some files used by DMCK, related to testing the Cassandra system (e.g. scripts to start nodes, workload files to be submitted to nodes, configurations etc). 

The source code of our PCTCP implementation can be found in the directory `$HOME/DMCK/src/edu/uchicago/cs/ucare/dmck/server/pctcp` and in the `$HOME/DMCK/src/edu/uchicago/cs/ucare/dmck/server/PCTCPModelChecker.java` file.

To repeat the tests in the artifact, we only need to modify small portions of the following two configuration files:

- `$HOME/DMCK/cassandra-2.0.0/target-sys.conf` to configure DMCK test parameters, which we use for configuring to use `PCTCPModelChecker` for PCTCP tests and `RandomModelChecker` for random walk.

- `$HOME/DMCK/cassandra-2.0.0/pctcp.conf` to configure PCTCP test parameters, such as the number of event labels.

We also provide sample output files in `$HOME/DMCK/sampleOutput` folder, which contains `PCTCP-d4.txt`, `PCTCP-d5.txt`, `PCTCP-d6.txt` and `randomWalk.txt`, the output files for the PCTCP tests with the number of event labels 4, 5, 6 and random walk tests respectively. 


## Running the experiments

In this document, we list the steps to run the Cassandra benchmark on DMCK tool using PCTCP and random exploration configurations. For more information about the steps to run a benchmark on DMCK tool, you can visit [DMCK repo](https://github.com/ucare-uchicago/DMCK) and [steps for running DMCK](http://people.cs.uchicago.edu/~lukman/dmck/dmck-walkthrough.html).

Our experimental evaluation involves testing the Cassandra system with the PCTCP method using different bug depth parameters (i.e., the number of event labels) and a naive random walk. 


### Compiling the DMCK tool:


- Go to `$home/DMCK/dmck-target-sys` directory and extract `cassandra-6023.tar.gz`:

  ```
$ cd $home/DMCK/dmck-target-sys
$ tar -xzf cassandra-6023.tar.gz
  ```

- Compile Cassandra:

  ```
$ cd $home/DMCK/dmck-target-sys/cassandra-6023
$ ./compile
  ```
  
- Compile the DMCK tool:

  ```
$ cd $home/DMCK
$ ./compile cassandra-2.0.0
  ```
Note: Linux removes some generated temporary folders if the machine is restarted. If you want to run the tests after a restart, you should execute this compilation step again.



### Running the PCTCP tests:

The results for PCTCP method involves three sets of tests where the number of event labels are set to 4, 5 and 6 respectively. The system is tested by running 1000 times using each configuration.

- (Configuration) Set the model checker parameter in the `$HOME/DMCK/cassandra-2.0.0/target-sys.conf` to PCTCPModelChecker on line 48. (The initial configuration already sets it to PCTCPModelChecker.)

  ```
exploring_strategy=edu.uchicago.cs.ucare.dmck.server.PCTCPModelChecker
  ```

- (Configuration) Set the bug-depth parameter in the `$HOME/DMCK/cassandra-2.0.0/pctcp.conf` file on line 10. This repeats the tests with number of event labels 4. 

  ```
# The size of the d-event tuple
pctcp.bugDepth = 4
  ```

- (Running tests) The Cassandra system can be tested by running the following commands in the `$HOME/DMCK` folder:

  ```
$ cd cassandra-2.0.0
$ ./dmckRunner.sh
  ```
  
	Note: It took around 510 minutes in our experiments to run the command which initiates 1000 runs to test the Cassandra system with the provided `pctcp.bugDepth` parameter.
	
	To test the system with fewer runs you can modify `pctcp.maxIterations` parameter in the `$HOME/DMCK/cassandra-2.0.0/pctcp.conf` file. It takes around 20 seconds to run a single iteration. (If all Cassandra node messages are not captured by DMCK in a run due to communication nondeterminism, the run is repeated with the same random seed. In such a case, it might take longer time to run the iteration.)

- (Reading the output) The screen output mostly involves DMCK activities such as sending workloads to Cassandra nodes, capturing the Cassandra messages, displaying which message is scheduled next. It is not needed to follow the screen output to get the test results as they will be logged in a file. 

	Results of the tests will be saved in a `$HOME/DMCK/cassandra-2.0.0/Test-<date>.txt` file (similar to sample output file `$HOME/DMCK/sampleOutput/PCTCP-d4.txt`).  It lists the random seed and the priority change points used for each test. At the end of this file, you can find the average number of events, the average number of maximum number of chains, the number of iterations and the elapsed time for running the tests.

	To count the number of runs which hit the bug, you can run the following command to grep in the generated result file. 
  ```
$ cd $HOME/DMCK/cassandra-2.0.0
$ grep -nr "DMCK has reproduced the expected bug" Test-<date>.txt | wc -l
  ```
 
- To repeat the tests for each `pctcp.bugDepth = 5` and `pctcp.bugDepth = 6`, set the bug-depth parameter accordingly in `pctcp.conf` as in step 2. Then, run the `dmckRunner.sh` again as in step 3 and read the output as in step 4.  

### Running the random walk tests:

The results for the random walk tests can be reproduced using the following steps:

- (Configuration) The model checker parameter in the `$HOME/DMCK/cassandra-2.0.0/target-sys.conf` file should be set to `RandomModelChecker` on line 48. 

  ```
exploring_strategy=edu.uchicago.cs.ucare.dmck.server.RandomModelChecker
  ```

- (Running the tests) The Cassandra system can be tested by running the following commands in the `$HOME/DMCK` folder:

  ```
$ cd cassandra-2.0.0
$ ./dmckRunner.sh
  ```

	It took around 500 minutes in our experiments to run the command which initiates 1000 random walk runs to test the Cassandra system.

	To test the system with fewer runs you can modify `random.maxIterations` parameter in the `$HOME/DMCK/cassandra-2.0.0/pctcp.conf` file. It takes around 20 seconds to run a single iteration. 
	(If all Cassandra node messages are not captured by DMCK in a run due to communication nondeterminism, the run is repeated with the same random seed. In such a case, it might take longer time to run the iteration.)
	
- Similar to PCTCP tests, the results of the random walk tests are summarized in a `$HOME/DMCK/cassandra-2.0.0/Test-<date>.txt` file.

 
## Modification of the test parameters

Cassandra system can be tested with PCTCP and random walk methods using different parameters by modifying the following configurations in `$HOME/DMCK/cassandra-2.0.0/pctcp.conf`.

```
# This file configures the parameters for PCTCP testing

# Initial seed for the random number generator
pctcp.randomSeed = 12345678

# Max number of messages in the system
pctcp.maxMessages = 54

# The size of the d-event tuple
pctcp.bugDepth = 4

# This parameter is used by the PCTCPModelChecker
pctcp.maxIterations = 1000


# The following configurations are used only when RandomModelChecker is used for the exploration
# (The exploration strategy of DMCK is set in target-sys.conf)

# Initial seed for the random number generator
random.randomSeed = 12345678

# This parameter is used by the RandomModelChecker
random.maxIterations = 1000
```

**Example 1:** The following configuration repeats the single PCTCP test which is found to hit the bug with `pctcp.bugDepth = 5` and `pctcp.randomSeed = 12346395` parameters (as recorded in the sample output file `sampleOutput/PCTCP-d5.txt`).

In file `$HOME/DMCK/cassandra-2.0.0/pctcp.conf`:
  
  ```
pctcp.randomSeed = 12346395
...
pctcp.bugDepth = 5
...
pctcp.maxIterations = 1
  ```
 
In file `$HOME/DMCK/cassandra-2.0.0/target-sys.conf`:

  ```
exploring_strategy=edu.uchicago.cs.ucare.dmck.server.PCTCPModelChecker
  ```

Then, run the test in the folder $HOME/DMCK/cassandra-2.0.0:

  ```
$ ./dmckRunner.sh
  ```
  It takes around 20 seconds to run the test. 
  
  (If all Cassandra node messages are not captured by DMCK in a run due to communication nondeterminism, the run is repeated with the same random seed. In such a case, it might take longer time to run the iteration.)

**Example 2:** The following configuration repeats the single PCTCP test which is found to hit the bug with `pctcp.bugDepth = 6` and `pctcp.randomSeed = 12346547` parameters (as recorded in the sample output file `sampleOutput/PCTCP-d6.txt`).

In file `$HOME/DMCK/cassandra-2.0.0/pctcp.conf`:
  
  ```
pctcp.randomSeed = 12346547
...
pctcp.bugDepth = 6
...
pctcp.maxIterations = 1
  ```
 
In file `$HOME/DMCK/cassandra-2.0.0/target-sys.conf`:

  ```
exploring_strategy=edu.uchicago.cs.ucare.dmck.server.PCTCPModelChecker
  ```
It takes around 20 seconds to run the test.

(If all Cassandra node messages are not captured by DMCK in a run due to communication nondeterminism, the run is repeated with the same random seed. In such a case, it might take longer time to run the iteration.)

## Requirements for the computational environment 

- We performed the experimental evaluation on a virtual machine running Ubuntu 16.04 with 4GB RAM and 4 processor cores hosted on a machine running Windows 7 with 16GB RAM and Intel i5-6600 3.3GHz processor.

- The system can be run on Ubuntu OS (tested with versions 16 and 18) with the following software installed:
	-  Ant build tool
	-  Oracle Java 8
	-  Python 2.7 (python2 is used)
	 
Environments that might break the software:

   - Java versions > 8 breaks the software since the Cassandra system under test uses `sun.misc.unsafe` package removed in later releases.
   - Lower CPU and memory configurations might cause the DMCK tool to miss some Cassandra messages to be captured in the timeout defined by the DMCK. This causes testing the system with some incomplete set of messages.
    	  




