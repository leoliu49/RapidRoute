# RapidRoute

Fast and lightweight routing of communication networks using [Xilinx RapidWright](http://www.rapidwright.io)

## Compiling
1. gradle (https://gradle.org/install/)
2. Java 1.8+ (http://www.oracle.com/technetwork/java/javase/downloads/)
3. RapidWright (https://github.com/Xilinx/RapidWright)
4. Vivado 2018.1+ to actually make it useful (https://www.xilinx.com/products/design-tools/vivado.html)


### Linux
Run the setup script. It will pull the latest [RapidWright source](https://github.com/Xilinx/RapidWright) and build for you.
```
sh setup.sh
```
### Windows
TBD

## Running
Run the launch script:
```
launch.sh [-h] [FILE_NAME] [--jobs NUM_JOBS] [--interactive]
```
This will execute a Jython instance for you to use RapidRoute. A Python may be passed in to automate creaton of custom designs (see below). Running in interactive mode will spawn a console after your code has been executed.

## Creating custom designs with Jython
[Jython](http://www.jython.org) is an included dependency which allows you to access Java objects using our `rapidroute` Python2 module. You can automate the creation of your custom designs using APIs provided by our included toolkit.

The RapidRoute module is provided in the `rapidroute/` folder. To run with `rapidroute`, see the `routing_tests` folder.

For example:
```
from rapidroute.device_toolkit import *

# Initialize RapidRoute with up to 4 threads
init(num_jobs=4)

new_design("example", "xcku115-flva1517-3-e")

# Import a DCP file
add_modules("src/main/resources/default-templates/dcps-xcku115-flva1517-3-e/east_16b.dcp", 16, [], [])

...
place_design()
route_design()
write_checkpoint("example.dcp")
```


