TestSparrow : SparrowTest {
    var sparrow;

    setUp {
        // Disable logging to avoid spamming local log file
        SparrowLog.writeToFile_(false);

        // Set the lifetime threshold to 0.1 seconds to speed up tests
        Sparrow.lifetimeThreshold = 100;

        Sparrow.lifetimeCheckInterval = 10;
        SparrowLog.verbose = false;

        sparrow = Sparrow.new("faketest", netaddr: NetAddr.localAddr);
    }

    // this will be called after each test
    tearDown {
        sparrow.reset();
    }

    test_hasCorrectAddr{
        this.assert(sparrow.addr == NetAddr.localAddr, "Sparrow should have correct address");
    }

    test_hasCorrectName{
        this.assert(sparrow.name == "faketest", "Sparrow should have correct name");
    }

    test_checkDeath{
        var condition = CondVar.new();

        // Check that the life checker is running
        this.assert(sparrow.alive, "Sparrow should be alive");

        // this.wait({ sparrow.alive.not }, "Waiting for sparrow to die", 0.25);
        // this.assert(sparrow.alive.not, "Sparrow should be dead if exceeded lifetime check time");

    }

    test_registerCallback{
        var condVar = CondVar.new();
        var receivedCallback = false;
        sparrow.registerCallback("/test/callback", { |msg|
            receivedCallback = true;
            condVar.signalOne();
        });

        sparrow.sendMsg("/test/callback", "test");
        condVar.waitFor(0.1);
        this.assert(receivedCallback, "Callback should have been received");
    }

    test_changeTargetPort{
        var condVar = CondVar.new();
        var newPort = thisProcess.openPorts.asArray.first, receivedPort;
        var receivedNewPort = false;

        var oscFunc = OSCFunc.new({ |msg|
            receivedPort = msg[1];
            receivedNewPort = true;
            condVar.signalOne();
        }, "/port").oneShot();

        sparrow.setDeviceTargetPort(newPort);
        condVar.waitFor(0.1);
        this.assert(receivedNewPort, "New port should have been received");
        this.assert(receivedPort == newPort, "New port should be correct");

        oscFunc.free();
    }

    test_changeTargetIP{
        var condVar = CondVar.new();
        var newHost = "192.155.122.66", receivedHost;

        var oscFunc = OSCFunc.new({ |msg|
            receivedHost = msg[1..];
            condVar.signalOne();
        }, "/ip").oneShot();

        sparrow.setDeviceTargetIP(newHost);
        condVar.waitFor(0.1);
        this.assert(receivedHost[0] == 192, "New host should be correct");
        this.assert(receivedHost[1] == 155, "New host should be correct");
        this.assert(receivedHost[2] == 122, "New host should be correct");
        this.assert(receivedHost[3] == 66, "New host should be correct");

        oscFunc.free();
    }


}
