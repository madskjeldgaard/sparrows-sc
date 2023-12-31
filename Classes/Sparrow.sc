// A sparrow represents one device on the network.
// It has a name, a network address, and a list of sensors.
// This class allows keeping track of the state of the device, and registering OSC callback functions to handle the data coming in from it.

Sparrow{
    classvar <minSupportedVersion = "0.0.2";

    classvar <all;
    classvar <sparrowPort = 8888;
    classvar <>lifetimeThreshold = 5; // Seconds before a device is considered dead
    classvar <>lifetimeCheckInterval = 1; // Seconds between life checks
    classvar <postErrorNumTimes = 3; // Number of times to post an error before giving up
    classvar <basePath;

    var <>alive = false;
    var <>name = "";
    var <sensors;
    var <>addr;
    var <callbackFunctions;
    var <lifeChecker;
    var <timeOfLastPing=0;
    var <actionAfterFirstPing;
    var <data;

    var numErrorsPosted = 0;
    var firstPing = true;

    // Name of device, netaddr of device, an array of sensors, and a function to run after the first ping is received from the device
    *new{|name, netaddr, sensors, action|
        ^super.new.init(name, netaddr, sensors, action)
    }

    *initClass{
        all = ();
        sparrowPort = NetAddr.langPort;

        basePath = PathName(Platform.userAppSupportDir) +/+ "sparrows";

        // Make a sparrow directory if it doesn't exist
        if(basePath.isFolder.not, {
            SparrowLog.info("Creating sparrow directory at %".format(basePath));
            basePath.fullPath.mkdir;
        });
    }

    *add{|name, netaddr, sensors, action|
        var sparrow = Sparrow.new(name, netaddr, sensors, action);
        all.put(name.asSymbol, sparrow);
        this.changed("add", name);
        all.changed("add", name);
        ^sparrow;
    }

    *remove{|name|

        // Reset the sparrow
        all[name.asSymbol].reset;

        // Remove the sparrow from the list
        all.remove(name.asSymbol);

        // Remove the sparrow file
        this.changed("remove", name);
    }

    *pingAll{|action|
        all.keysValuesDo{|name, sparrow|
            sparrow.ping({"Pong from %".format(name).postln});
            if(action.notNil, {
                action.value(sparrow);
            })
        };
    }

    *restartAll{
        all.keysValuesDo{|name, sparrow|
            sparrow.restart();
        };
    }

    *resetAll{
        all.keysValuesDo{|name, sparrow|
            sparrow.reset();
        };
    }

    *setDeviceTargetPortAll{|newPort, action|
        all.keysValuesDo{|name, sparrow|
            sparrow.setDeviceTargetPort(newPort, action);
        };
    }

    *setDeviceTargetIPAll{|newIPString, action|
        all.keysValuesDo{|name, sparrow|
            sparrow.setDeviceTargetIP(newIPString, action);
        };
    }

    // Deregister all callbacks
    reset{
        callbackFunctions.keysValuesDo{|callbackName, func|
            SparrowLog.info(this, "Deregistering callback %".format(callbackName));
            func.free();
        };

        callbackFunctions = ();

        lifeChecker.stop;
        lifeChecker.free;

        firstPing = true;
        timeOfLastPing = 0;
        alive = false;

        this.changed("reset", name);
    }

    init{|deviceName, deviceNetaddr, deviceSensors, action|
        name = deviceName;
        data = ();
        addr = deviceNetaddr;
        sensors = deviceSensors;
        alive = true;
        actionAfterFirstPing = action ? {"Sensor % is alive and OK it seems :)".format(name).postln};
        callbackFunctions = ();
        this.prRegisterDefaultCallbacks();
        this.prCreateLifeCheckTask();
        this.setDeviceTargetPort(Sparrow.sparrowPort, {
            SparrowLog.info(this, "Setting target IP for sparrow % to % and shaking hands on it".format(name, Sparrow.sparrowPort));
            this.handshake();
        });
        SparrowLog.info(this, "Sparrow % created: %".format(name, addr));
        this.changed("init", name);
    }

    // Create a task that checks if the device is still alive
    prCreateLifeCheckTask{
        lifeChecker = Task({
            SparrowLog.info(this, "Starting life checker for sparrow %".format(name));
            loop{
                var now = Date.getDate.rawSeconds;
                if((now - timeOfLastPing) > lifetimeThreshold){
                    alive = false;
                    this.changed("dead", name);
                    if(numErrorsPosted < Sparrow.postErrorNumTimes){
                        SparrowLog.error(this, "Sparrow % is dead".format(name));
                        numErrorsPosted = numErrorsPosted + 1;
                    }
                };

                lifetimeCheckInterval.wait;
            }
        });
    }

    prRegisterDefaultCallbacks{
        // Sign of life received
        var signOfLifeFunc = {|msg, time, addr, recvPort|
            // var name = msg[1];
            var ip = addr;
            timeOfLastPing = Date.getDate.rawSeconds;
            numErrorsPosted = 0;

            if(alive.not, {
                SparrowLog.info(this, "Sparrow % is alive again".format(name));
            });

            if(firstPing, {
                actionAfterFirstPing.value(Date.getDate, addr, recvPort, this);
                SparrowLog.info(this, "First ping received, Sparrow % is alive and OK it seems :)".format(name));
                lifeChecker.play;
            });

            this.changed("alive", name);
            alive = true;
            firstPing = false;
        };

        // Resets the last ping time
        // var pingCallback = {|msg, time, addr, recvPort|
        //     timeOfLastPing = Date.getDate.rawSeconds;
        //     alive = true;
        //     this.changed("alive", name);
        //     this.sendMsg("/pong");
        //     SparrowLog.info(this, "Received ping from sparrow %".format(name));
        // };

        var errorLogCallback = {|msg, time, addr, recvPort|
            SparrowLog.error(this, msg[1..]);
        };

        var warningLogCallback = {|msg, time, addr, recvPort|
            SparrowLog.warning(this, msg[1..]);
        };

        var infoLogCallback = {|msg, time, addr, recvPort|
            SparrowLog.info(this, msg[1..]);
        };

        this.registerCallback("/log/error", errorLogCallback);
        this.registerCallback("/log/warning", warningLogCallback);
        this.registerCallback("/log/info", infoLogCallback);
        this.registerCallback("/tweettweet", signOfLifeFunc);
        this.registerCallback("/ping", signOfLifeFunc);
        this.registerCallback("/version", {|msg, time, addr, recvPort|
            var version = msg[1];
            SparrowLog.info(this, "version: %".format(version));
            if(version < Sparrow.minSupportedVersion, {
                SparrowLog.error(this, "Sparrow % is running an old version (%), please update to at least % and restart it".format(name, version, Sparrow.minSupportedVersion));
            });
            this.changed("version", version);
        }, oneShot: true);
    }

    prUpdateAllCallbackRecvPorts{
        callbackFunctions.do{|oscPath, oscFunc|
            oscFunc.recvPort = sparrowPort;
        };
    }

    addState{|key, initialValue, func|
        data[key] = SimpleState.new(initialValue, func);
    }

    state{|key|
        ^if(data[key].notNil, {
            data[key]
        }, {
            SparrowLog.error(this, "State does not exist for key % in sparrow %".format(key, name));
            nil;
        })
    }

    // FIXME: All callback functions should have their recvPort updated when the device is moved to a new port
    setDeviceTargetIP{|newIPString, action|
        this.sendMsg(*(["/ip"] ++ newIPString.splitIP));

        this.registerCallback("/ip", {|msg, time, addr, recvPort|
            var newIPReceived = msg[1..];
            SparrowLog.info(this, "New target IP set for sparrow %: %".format(name, newIPReceived));
            if(action.notNil, {
                action.value()
            });

            this.changed("ip", newIPReceived);
        }, oneShot: true);
    }

    // FIXME: All callback functions should have their recvPort updated when the device is moved to a new port
    setDeviceTargetPort{|newPort, action|
        // First check if it is a valid and open port
        var openPorts = thisProcess.openPorts;
        if(openPorts.includes(newPort).not, {
            SparrowLog.error(this, "Port % is not open".format(newPort));
            ^nil;
        }, {
            // Set port
            this.sendMsg("/port", newPort);
            this.registerCallback("/port", {|msg, time, addr, recvPort|
                var newPortReceived = msg[1];
                SparrowLog.info(this, "New target port set for sparrow %: %".format(name, newPortReceived));
                if(action.notNil, {
                    action.value()
                });

                this.changed("port", newPortReceived);
            }, oneShot: true);

        })
    }

    handshake{
        this.sendMsg("/handshake", true);
        this.registerCallback("/thanks", {|msg, time, addr, recvPort|
            SparrowLog.info(this, "Handshake complete for sparrow %".format(name));
            this.changed("handshake", name);
        }, oneShot: true);
    }

    restart{
        SparrowLog.info(this, "Restarting sparrow %".format(name));
        this.sendMsg("/restart");
    }

    ping{|actionWhenPonged|
        this.sendMsg("/ping");
        this.registerCallback("/pong", actionWhenPonged ? {
            SparrowLog.info(this, "Received pong from sparrow %".format(name));
        }, oneShot: true);
    }

    // Turn the ping transmission on the device on/off
    pingState{|state|
        this.sendMsg("/pingState", state);
    }

    hasCallbackForKey{|key|
        ^callbackFunctions.keys.asArray.contains(key.asSymbol);
    }

    disableCallback{|key|
        if(this.hasCallbackForKey(key), {
            callbackFunctions[key.asSymbol].disable;
        });
    }

    enableCallback{|key|
        if(this.hasCallbackForKey(key), {
            callbackFunctions[key.asSymbol].enable;
        });
    }

    // tracePath{|path, traceState=true|
    //     if(traceState, {
    //         this.registerCallback((path.asString "_trace").asSymbol, {|msg, time, addr, recvPort|
    //             "Received % from %".format(msg, path).postln;
    //         });

    //     }, {
    //         if(hasCallback)
    //         this.disableCallback((path.asString "_trace").asSymbol);
    //     })
    // }

    // Register a callback function for a sensor
    registerCallback{|oscPath, callbackFunction, oneShot=false|
        var oscfunc;

        // Check if it already exists
        if(this.hasCallbackForKey(oscPath), {
            // Remove the old one
            callbackFunctions[oscPath.asSymbol].free;
        });

        // TODO: Pass sparrow instance to osc func callback
        oscfunc = OSCFunc.new(
            func:callbackFunction,
            path:oscPath,
            srcID:addr,
            recvPort: sparrowPort,
            argTemplate:nil,
            dispatcher:nil
        );

        if(oneShot, {
            // One shots aren't stored
            oscfunc.oneShot();
        }, {
            // Store the callback function so we can remove it later
            callbackFunctions.put(oscPath.asSymbol, oscfunc);
        });
    }

    sendMsg{|oscPath ... oscArgs|
        addr.sendMsg(*([oscPath] ++ oscArgs));
    }
}
