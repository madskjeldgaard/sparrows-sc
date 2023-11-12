/*

This class is used to keep track of sparrows, mainly by listening to the /whoami messages coming in from them.

It takes a "deviceMap" as an argument, this is a dictionary of deviceID->(oscPath: actionFunc, oscPath: actionFunc, etc.)

This way, when a device is registered, the callbacks are automatically registered and then called when the device sends a message.

*/
// Keep track of sparrows
SparrowHQ{
    var <oscFunc;
    *new{|deviceMap, action|
        ^super.new.init(deviceMap, action)
    }

    init{|deviceMap, action|

        if((Sparrow.all.size > 0), {
            "Detected the existence of sparrows on startup, restarting all".postln;

            // Reset all the sparrows
            Sparrow.resetAll();

            // Restart all the sparrows
            Sparrow.restartAll();

        });

        // Create a listener, waiting for /whoami messages and then registering the device
        oscFunc = OSCFunc({|msg, time, addr, recvPort|
            var name = msg[1];
            var port = msg[2];
            var sensorTypes = msg[3..];
            var ip = addr;
            var oscfuncs = [];
            var sparrow, callbacks;
            var sparrowExists = Sparrow.all[name].notNil;

            "<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>".postln;
            "A WILD SPARROW APPEARS".postln;
            "".postln;
            "Received information about a sensor".postln;
            "Name of sensor: ".post; name.postln;
            "IP: ".post; ip.postln;
            "Type: ".post; sensorTypes.postln;
            "Message: ".post; msg.postln;
            "Time: ".post; time.postln;
            "Address: ".post; addr.postln;
            "Port: ".post; port.postln;

            // Add sparrow
            if(sparrowExists.not, {
                "Adding new sparrow".postln;
                sparrow = Sparrow.add(name, ip, sensorTypes, action: nil);
            }, {
                "Sparrow already exists, updating".postln;
                sparrow = Sparrow.all[name];
                sparrow.reset();
                sparrow.init(name, ip, sensorTypes);
            });

            // Match up the deviceMap
            deviceMap.notNil.if({
                callbacks = deviceMap[name];
                if(callbacks.notNil){
                    callbacks.keysValuesDo{|oscPath, actionFunc|
                        sparrow.registerCallback(oscPath, actionFunc);
                    };
                }
            });

            if(action.notNil, {
                action.value(sparrow);
            });

        }, "/whoami");

        "Waiting for sparrows to appear on the network and present themselves with the /whoami message".postln;

    }

    // TODO:
    broadcast{|broadcastAddr, path, message|
        var bf = NetAddr.broadcastFlag;
        NetAddr.broadcastFlag_(true);
        NetAddr.sendMsg(path, message);
        NetAddr.broadcastFlag_(bf);
    }
}
