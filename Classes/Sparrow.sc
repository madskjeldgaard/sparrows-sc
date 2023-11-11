/*

A sparrow represents one device on the network.

It has a name, a network address, and a list of sensors.

This class allows keeping track of the state of the device, and registering OSC callback functions to handle the data coming in from it.

*/
Sparrow{
    classvar <all;
    classvar <sparrowPort;
    classvar <lifetimeThreshold = 5; // Seconds before a device is considered dead
    classvar lifetimeCheckInterval = 1; // Seconds between life checks
    classvar <postErrorNumTimes = 3; // Number of times to post an error before giving up

    var <>alive = false;
    var <>name = "";
    var <sensors;
    var <>addr;
    var <callbackFunctions;
    var <lifeChecker;
    var <timeOfLastPing=0;
    var <actionAfterFirstPing;

    var numErrorsPosted = 0;
    var firstPing = true;

    // Name of device, netaddr of device, an array of sensors, and a function to run after the first ping is received from the device
    *new{|name, netaddr, sensors, action|
        ^super.new.init(name, netaddr, sensors, action)
    }

    *initClass{
        all = IdentityDictionary.new;
        sparrowPort = NetAddr.langPort;
    }

    *add{|name, netaddr, sensors, action|
        var sparrow = Sparrow.new(name, netaddr, sensors, action);
        all.put(name, sparrow);
        ^sparrow;
    }

    *remove{|name|
        all.remove(name);
    }

    init{|deviceName, deviceNetaddr, deviceSensors, action|
        name = deviceName;
        addr = deviceNetaddr;
        sensors = deviceSensors;
        alive = true;
        actionAfterFirstPing = action ? {"Sensor % is alive and OK it seems :)".format(name).postln};
        callbackFunctions = IdentityDictionary.new;
        this.prRegisterDefaultCallbacks();
        this.prCreateLifeCheckTask();
        this.setDeviceTarget(NetAddr.localAddr);

        this.handshake();
        "Sparrow % added: %".format(name, addr).postln;
    }

    // Create a task that checks if the device is still alive
    prCreateLifeCheckTask{
        lifeChecker = Task({
            loop{
                var now = Date.getDate.rawSeconds;
                if(now - timeOfLastPing > lifetimeThreshold){
                    alive = false;
                    if(numErrorsPosted < Sparrow.postErrorNumTimes){
                        "%: Sparrow % is dead".format(Date.getDate.stamp, name).error;
                        numErrorsPosted = numErrorsPosted + 1;
                    }
                };

                lifetimeCheckInterval.wait;
            }
        }).play;
    }

    prRegisterDefaultCallbacks{
        // Sign of life received
        var signOfLifeFunc = {|msg, time, addr, recvPort|
            var name = msg[1];
            var ip = addr;
            timeOfLastPing = Date.getDate.rawSeconds;
            numErrorsPosted = 0;

            if(alive.not, {
                "%: Sparrow % is alive again".format(Date.getDate.stamp, name).postln;
            });

            if(firstPing, {
                actionAfterFirstPing.value(Date.getDate, addr, recvPort, this);
            });

            alive = true;
            firstPing = false;
        };

        this.registerCallback("/awake", signOfLifeFunc);
    }

    setDeviceTarget{|addr|
        this.sendMsg(*(["/ip"] ++ addr.ip.splitIP));
        this.sendMsg("/port", addr.port);
    }

    handshake{
        this.sendMsg("/handshake", true);
    }

    restart{
        this.sendMsg("/restart");
    }

    ping{|actionWhenPonged|
        this.sendMsg("/ping");
        if(actionWhenPonged.notNil, {
            this.registerCallback("/pong", actionWhenPonged ? {"Received pong".postln;}, oneShot: true);
        });
    }

    // Register a callback function for a sensor
    registerCallback{|oscPath, callbackFunction, oneShot=false|
        var oscfunc = OSCFunc.new(
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
            callbackFunctions.put(oscPath, oscfunc);
        });

    }

    sendMsg{|oscPath ... oscArgs|
        addr.sendMsg(*([oscPath] ++ oscArgs));
    }
}
