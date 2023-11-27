SparrowLog{
    classvar <logFile;
    classvar <logFilePath;
    classvar <basePath;

    // Default max log size is 128 MB
    classvar <>maxLogSizeBytes = 128 * 1024 * 1024;

    *initClass{
        // Init after Sparrow
        Class.initClassTree(Sparrow);

        logFilePath = Sparrow.basePath +/+ "sparrow.log";

        // Check size of log file
        if(this.fileSize > this.maxLogSizeBytes,{
            "Log file too big, truncating...".error;
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
            "Log file not open, opening...".postln;
            logFile = File.open(logFilePath.fullPath, "a+");
        };

        logFile.write(string);
        logFile.write("\n");
        logFile.close;
    }

    // Log message to file
    // sparrowInstance is an instance of Sparrow
    // logLevel is a symbol: either 'info', 'error' or 'warning'
    *log{|sparrowInstance, logLevel='info', message|
        var now = Date.getDate;
        var string = "[%][%][%]: %".format(logLevel.asString.toUpper, now, sparrowInstance.name, message);

        this.writeString(string);

        switch(logLevel,
            'info', { string.postln },
            'error', { string.error },
            'warning', { string.warn },
            'warn', { string.warn },
        );
    }

    *error{|sparrowInstance, message|
        this.log(sparrowInstance, 'error', message);
    }

    *info{|sparrowInstance, message|
        this.log(sparrowInstance, 'info', message);
    }

    *warning{|sparrowInstance, message|
        this.log(sparrowInstance, 'warning', message);
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
