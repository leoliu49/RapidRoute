# RapidRoute

URA with Professor Kapre using Xilinx RapidWright

## Compiling
1. gradle (https://gradle.org/install/)
2. Java 1.8+ (http://www.oracle.com/technetwork/java/javase/downloads/)
3. RapidWright (https://github.com/Xilinx/RapidWright)
4. Vivado 2018.1+ to actually make it useful  
**Place this repository in the same folder as your RapidWright repo. (i.e. folder/RapidWright/ and folder/rapidwright_ura/**  

### Linux
Source the env.sh file at the root of the repository, passing in the path to RapidWright directory
```
. env.sh $RAPIDWRIGHT_PATH
```
### Windows
1. Add JARs in RapidWright (RapidWright/jars/*.jar) to your CLASSPATH
2. Add JARs in the deps folder to your CLASSPATH
3. Add JARs generated in build/libs/ to your CLASSPATH (after running gradle)

## Running
Run `gradle jar`  
From here, 3 classes may be run:
```
java ComplexRegister [--help] [--verbose] [--out OUT_FILE_NAME]
    Create a complex register of 3 modules at SLICE_X56Y120, SLICE_X57Y120, SLICE_X56Y121.
```
```
java RegisterPair [--help] [--verbose] [--out OUT_FILE_NAME]
    Create and route 2 complex registers as described in src/main/resources/register_pair_example.conf.
```
```
java CustomDesign [-h] [-v] [--example] [--name DESIGN_NAME] [--out OUT_FILE_NAME]
  Create and route a design based on placements.conf and routes.conf. Uses routes_example.conf instead if --example is specified.
```
**Run this at the root directory of the repository.**  

## Creating custom designs
3 files are used when creating custom designs:
1. `src/main/resources/register_components.conf` (see `src/main/resources/complex_register_example.conf` for example)
2. `src/main/resources/placements.conf` (see `src/main/resources/register_pair_example.conf` for example)
3. `src/main/resources/routes.conf` (see `src/main/resources/routes_example.conf` for example)

## Declaring components
A `ComplexRegister` is a collection of out-of-context DCP modules. A few examples of these are found under `src/main/resources/reg_bank/`. These modules only occupy a single site within the board.  
Components that are used must be placed under the `resources/components/` folder, and be named as a `type\d+`. Then they must be declared in the `register_components.conf` file, along with other information:
```
[type1]

bw=4

inPIP0=BYPASS_E8
inPIP1=BYPASS_E12
inPIP2=BYPASS_E9
inPIP3=BOUNCE_E_13_FT0

outPIP0=LOGIC_OUTS_E30
outPIP1=LOGIC_OUTS_E7
outPIP2=LOGIC_OUTS_E22
outPIP3=LOGIC_OUTS_E3
```
  
The `placements.conf` file specifies how each register is made up of declared components, and where they are placed:
```
[reg1]
name = reg1

comp0 = type0, SLICE_X56Y122
comp1 = type1, SLICE_X57Y122
comp2 = type0, SLICE_X56Y123
```
  
The `routes.conf` file specifies how the registers are connected. The general syntax is shown with this example:  
  For routing `reg3` bits `[6..3]` to `reg7` bits `[10..7]`: `reg7[10..7] <= reg3[6..3]`.

**For each design run, an unrouted DCP is also generated, in which only the logical nets have been specified**  
**Outputted files are in the output folder**
