# Lego Sorter App - (LW - lightweight)

### Enhanced copy of repository:
https://github.com/LegoSorter/LegoSorterApp

**Lego Sorter App** is an android application designed to work with [Lego Sorter Server](https://github.com/czpmk/LegoSorterServerLW). \
The purpose of this application is to send images of lego bricks to the server and handle responses.

## How to start
The easiest way is to download already released apk, which is available [here](https://github.com/czpmk/LegoSorterAppLW/releases/download/release/app-debug.apk). \
After the installation, insert a correct ip address of Lego Sorter Server and have fun!

## Available Features
**Lego Sorter App** provides three basic modes for working with lego bricks.

### Asynchronous switch
There is a new switch in main menu which selects one of the working modes (sync/async).

### Capture mode
This mode is designed for capturing lego bricks and storing them on the server - the main use case of this mode is dataset creation. \
User inputs a name of lego bricks which are going to be captured, and then they are stored on the server with an appropriate label. \
This way with *auto-capture mode* it's possible to create a big dataset in reasonable time. \
Captured images can contain a lot of bricks of the same type - all of them will be cut from the original image and then saved individually.

### Analyze mode
This mode allows analyzing captured images in a context of detected bricks. \
In this mode all detected bricks are marked on the live camera preview, so a user can test a detection model.

### Sorting mode
This is an extension of the analysis mode. In this mode server is sorting bricks placed on conveyor belt. \
There are a few options of photo capturing mode:
* *Stop-capture-run* - Conveyor belt is stopped when the photo is being captured. Interval is defined in settings.
* *Continuous move* - Conveyor belt is running all the time with no delay between photo captures.
* *Continuous move (capture delayed)* - Conveyor belt is running all the time but the photos are taken with delay defined in settings. This was implemented to stop constant overflowing a queue in async mode.