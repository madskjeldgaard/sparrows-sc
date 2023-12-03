# Sparrows-SC

This package contains an interface for the [Sparrow devices](https://github.com/madskjeldgaard/Sparrows), a series of networked sensor devices that communicate via OSC over WiFi.

## Features

The SuperCollider interface provides:

- A Sparrow headquarters for controlling a network of sensors
- A handshake procedure for discovering Sparrows on the network and automatically setting their target IP and port
- Logging of device activity to disk
- Callback registering for each Sparrow's sensor data
- Easy to use controls over each sparrow including ping, restart, etc.

