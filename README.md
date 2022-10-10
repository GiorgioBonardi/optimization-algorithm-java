# Optimization algorithms - course assignment

This repository contains everything you need to start working on the Multiple Knapsacks problem with Family-Split Penalties (MKFSP).

## Before you start

  1. Fork this repository
  2. Add your colleagues and the examiner (Lorenzo Moreschini, GitHub username: `lmores`) to your repository

## Requirements

This project needs Java 17 (available on the [Adoptium website](https://adoptium.net/temurin/releases/?version=17)).
The only required external libraries are `jackson-databind` (v2.13.4) and `gurobi` (v9.5.2).

### Using Maven

This project use Maven to manage the dependencies and compile the source files.
It can be downloaded from its [website](https://maven.apache.org/download.cgi).
When used, Maven will automatically fetch the correct version of the jackson-databind library and use it to compile this project.
However, the gurobi library is not available on Maven repositories, so you have to download it from the [Gurobi website](https://www.gurobi.com/downloads/gurobi-software/) and install it following the instructions provided on the same page.
You will also need a Gurobi license to effectively use this external library.
Follow the instructions on [this page](https://www.gurobi.com/academia/academic-program-and-licenses/) to get a Gurobi free academic licence.
Once you have done all the above, if you are on a Unix system (or you have installed the Windows Subsystem for Linux), you can compile and run this project with

```bash
./car.sh Main
```

*Note:* when installed Gurobi should create an environment variable named `GUROBI_HOME` with the path to the directory when Gurobi has been instaleld.
This variable is required by the above script.
If this variable does not exist on your system, add it.

### Not using Maven

If you choose not to use Maven, you are in charge of compiling this project using your favourite tool.
You will also have to produce a self-contained `mkfsp.jar` archive to be submitted at the end of the project and provide enough information about how to execute it (something along the line: `java mkfsp.jar`).

### Operative system

This software has been developed and tested on a Unix system.
It should also run on other operative systems (e.g., Windows), but it is not guaranteed.
For any issue, please contact the examiner.

## How to use

This codebase provides:

  * a function to read JSON instance files,
  * a function to build the Gurobi model for a given instance,
  * a function to check whether a given assignment of items to knapsacks is a feasible solution.

Each of the above functions is annotated with a Java docstring that explains its usage and purpose.
Compiling this project and runnig the source file located at `it.unibs.mao.optalg.mkfsp.Main` you can see all of them in action on the benchmark instances.
If you use Maven on a system with a bash shell it is sufficient to run

```bash
./car.sh Main
```

from the root directory of this project.
Have a look at [Main.java](./src/main/java/it/unibs/mao/optalg/mkfsp/Main.java) for an example of how to use the above functions.

## Documents

The `docs` directory contains:

  * [slides.pdf](./docs/slides.pdf): the presentation of the problem you saw in class,
  * [assignment.pdf](./docs/assignment.pdf): a formal description of the problem with information about instance data format and what you should deliver at the end of the project.
    **Make sure to read this document.**

## Important

Although in the formal statement of the problem indexes start from `1`, this implementation and all benchmark instances use `0`-based indexes (as all array-like data structures in Java use `0`-based indexes).
For example, given a list of `n` items, the first item has index `0`, the second item has index `1` and so on... the last item has index `n-1`.

## Style

Please follow [Google Java style guide](https://google.github.io/styleguide/javaguide.html).
