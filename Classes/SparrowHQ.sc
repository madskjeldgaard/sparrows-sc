/*

This class is used to keep track of sparrows, mainly by listening to the /whoami messages coming in from them.

It takes a "deviceMap" as an argument, this is a dictionary of deviceID->(oscPath: actionFunc, oscPath: actionFunc, etc.)

This way, when a device is registered, the callbacks are automatically registered and then called when the device sends a message.

*/
// Keep track of sparrows
SparrowHQ{
    var <oscFunc;
    *new{|deviceMap|
        ^super.new.init(deviceMap)
    }

    init{|deviceMap|

        if((Sparrow.all.size > 0), {
            "Detected the existence of sparrows on startup, restarting all".postln;

            // Reset all the sparrows
            Sparrow.all.do{|s| s.reset };

            // Restart all the sparrows
            Sparrow.restartAll;
        });

        // Create a listener, waiting for /whoami messages and then registering the device
        oscFunc = OSCFunc({|msg, time, addr, recvPort|
            var name = msg[1];
            var port = msg[2];
            var sensorTypes = msg[3..];
            var ip = addr;
            var oscfuncs = [];
            var sparrow, callbacks;

            "Received information about a sensor".postln;
            "Name of sensor: ".post; name.postln;
            "IP: ".post; ip.postln;
            "Type: ".post; sensorTypes.postln;
            "Message: ".post; msg.postln;
            "Time: ".post; time.postln;
            "Address: ".post; addr.postln;
            "Port: ".post; port.postln;

            // Add sparrow
            sparrow = Sparrow.add(name, addr, sensorTypes, action: nil);

            // Match up the deviceMap
            callbacks = deviceMap[name];
            if(callbacks.notNil){
                callbacks.keysValuesDo{|oscPath, actionFunc|
                    sparrow.registerCallback(oscPath, actionFunc);
                };
            };

        }, "/whoami");

    }

    // TODO:
    broadcast{|broadcastAddr, path, message|
        var bf = NetAddr.broadcastFlag;
        NetAddr.broadcastFlag_(true);
        NetAddr.sendMsg(path, message);
        NetAddr.broadcastFlag_(bf);
    }
}
