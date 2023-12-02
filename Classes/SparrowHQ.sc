/*

This class is used to keep track of sparrows, mainly by listening to the /whoami messages coming in from them.

It takes a "deviceMap" as an argument, this is a dictionary of deviceID->(oscPath: actionFunc, oscPath: actionFunc, etc.)

This way, when a device is registered, the callbacks are automatically registered and then called when the device sends a message.

*/
SparrowHQ{
    var <oscFunc, <>broadcastNetaddr, broadcastRoutine, <netAddr;

    *new{|deviceMap, action, stopBroadcastingAfter = inf|
        ^super.new.init(deviceMap, action, stopBroadcastingAfter)
    }

    init{|deviceMap, action, stopBroadcastingAfter|

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
                sparrow = Sparrow.add(name: name, netaddr: ip, sensors: sensorTypes, action: nil);
            }, {
                "Sparrow already exists".postln;
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

            this.changed(this, sparrow);

        }, "/whoami");

        broadcastNetaddr = Platform.case(
            \osx,       {
                var ip = "ifconfig en0 | awk '$1 == \"inet\" {print $2}'".unixCmdGetStdOut;
                netAddr = NetAddr.new(ip, Sparrow.sparrowPort);

                // Replace last part of address with .255
                ip = ip.splitIP;
                ip[3] = 255;
                ip = ip.join(".");

                "Found broadcast address: ".post; ip.postln;
                NetAddr.new(ip, 8888);
            },
            \linux,     {
                "%: broadcastNetaddr on wifi not yet automatic on linux. Set it manually using .broadcastNetaddr = NetAddr(\"x.x.x.x\")".format(this.class.name).warn;
            },
            \windows,   {
                "%: broadcastNetaddr on wifi not yet automatic on windows. Set it manually using .broadcastNetaddr = NetAddr(\"x.x.x.x\")".format(this.class.name).warn;
            }
        );

        "Waiting for sparrows to appear on the network and present themselves with the /whoami message".postln;
        this.callSparrows(broadcastTime: stopBroadcastingAfter);

    }

    callSparrows{|broadcastTime=120|
        var ip = netAddr.ip.splitIP();
        this.broadcastEnable("/sparrowcall", ip[0], ip[1], ip[2], ip[3], Sparrow.sparrowPort);
        fork{
            broadcastTime.wait;
            SparrowLog.info(message:"Stopping sparrow call broadcast");
            this.broadcastDisable("/sparrowcall");
        }
    }

    broadcastRestart{
        this.broadcastMessage("/restart");
    }

    broadcastMessage{|path, message|
        var bf = NetAddr.broadcastFlag;
        NetAddr.broadcastFlag_(true);
        broadcastNetaddr.sendMsg(*([path] ++ message));
        NetAddr.broadcastFlag_(bf);
    }

    broadcastEnable{|path ... message|

        if(broadcastRoutine.notNil, {
            broadcastRoutine.stop;
        });

        "Broadcasting to all devices on the network".postln;
        "Path: ".post; path.postln;
        "Message: ".post; message.postln;
        "Broadcast IP: ".post; broadcastNetaddr.postln;

        broadcastRoutine = Routine({
            SparrowLog.info(message:"Broadcasting to all devices on the network: %, %".format(path, message));

            loop{
                this.broadcastMessage(path, message);
                2.wait;
            }
        }).play;
    }

    broadcastDisable{|path|
        if(broadcastRoutine.notNil, {
            "Disabling broadcast".postln;
            SparrowLog.info(message:"Disabling broadcast to all devices on the network: %".format(path));
            broadcastRoutine.stop;
        });
    }
}
