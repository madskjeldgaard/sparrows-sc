SparrowLog{
    classvar <>verbose=true; // Post to console
    classvar <>writeToFile=true; // May be disabled for testing purposes
    classvar <logFile;
    classvar <logFilePath;
    classvar <basePath;

    // Default max log size is 128 MB
    classvar <>maxLogSizeBytes = 134217728;

    *initClass{
        // Init after Sparrow
        Class.initClassTree(Sparrow);

        logFilePath = Sparrow.basePath +/+ "sparrow.log";

        // Check size of log file
        if(this.fileSize > this.maxLogSizeBytes,{
            "SparrowLog log file too big ...".error;
        });

        // Create log file if not exists
        logFile = File.new(logFilePath.fullPath, "a+");

        // Write log start to file
        this.writeString("--------------------------------------------------------------------");
        this.writeString("-                                                                   ");
        this.writeString("-  Sparrow log start                                                ");
        this.writeString("-                                                                   ");
        this.writeString("--------------------------------------------------------------------");
    }

    *clean{
        // Delete contents of log file
        logFile = File.new(logFilePath.fullPath, "w");
        logFile.close();
    }

    *writeString{|string|
        logFile.isOpen.not.if{
            logFile = File.open(logFilePath.fullPath, "a+");
        };

        logFile.write(string);
        logFile.write("\n");
        logFile.close;
    }

    // Log message to file
    // sparrowInstance is an instance of Sparrow
    // logLevel is a symbol: either 'info', 'error' or 'warning'
    *log{|sparrowInstance, logLevel='info', message, writeToFile = true|
        var now = Date.getDate;
        var name = if(sparrowInstance.isNil, { "GLOBAL" }, {sparrowInstance.name});
        var string = "[%] %: %, %".format(now, name, logLevel.asString.toUpper, message);

        if(writeToFile, {
            this.writeString(string);
        });

        verbose.if({
            switch(logLevel,
                'info', { string.postln },
                'error', { string.error },
                'warning', { string.warn },
                'warn', { string.warn },
            )
        })
    }

    *error{|sparrowInstance, message, writeToFile=true|
        this.log(sparrowInstance, 'error', message, writeToFile: writeToFile);
    }

    *info{|sparrowInstance, message, writeToFile=true|
        this.log(sparrowInstance, 'info', message, writeToFile: writeToFile);
    }

    *warning{|sparrowInstance, message, writeToFile=true|
        this.log(sparrowInstance, 'warning', message, writeToFile: writeToFile);
    }

    // File size in bytes
    *fileSize{
        ^File.fileSize(logFilePath.fullPath);
    }

    *openOS{
        logFilePath.fullPath.openOS();
    }

    *openFolder{
        logFilePath.pathOnly.openOS();
    }

    *openNvim{
        NvimOpen.openTab(logFilePath.fullPath);
    }
}
