(
// Put tests here and exit with exit code 0 if all tests pass, otherwise exit with non-zero code.
var returnCode;

fork {
    var condVar = CondVar.new();
    a = TestSparrow.run();

    if(a.failures.size == 0, {
        returnCode = 0;
    }, {
        "Some tests failed".postln;
        returnCode = 1;
    });

    condVar.waitFor(10, { returnCode.notNil});

};

returnCode.exit;
)
