from datetime import datetime
from numpy import mean
import numpy
from pythonosc import dispatcher
from pythonosc import osc_server

from os.path import dirname, join
from com.chaquo.python import Python

ip = "192.168.1.9"
port = 5000

files_dir = str(Python.getPlatform().getApplication().getFilesDir())
filePath = join(dirname(files_dir), 'files/mean.csv')

recording = False
enable = False
accX = []
accY = []
result = []
server = None
#f = open(filePath, 'w+')

def muse_handler(address, *args):
    """handle all muse data"""
    global recording
    global enable
    global accX
    global accY
    if recording:
        if not enable and address == "/muse/acc":
            print("Enable")
            enable = True
        if enable:
            if address == "/muse/acc":
                # for arg in args:
                # print(args[0], args[1])
                accX.append(float(args[0]))
                accY.append(float(args[1]))

            media = []
            if len(accX) == len(accY) >= 64:
                media.append(mean(accX))
                media.append(mean(accY))

                # server.shutdown()  # The file is written, then stop the recording
                col1 = []
                accX = []
                accY = []
                # print(f"{address}: {args[0]}, {args[1]}")
                print(media)
                result.append(media)


def marker_handler(address: str, i):
    global recording
    global server
    global result
    dateTimeObj = datetime.now()
    timestampStr = dateTimeObj.strftime("%Y-%m-%d %H:%M:%S.%f")
    markerNum = address[-1]
    if markerNum == "1":
        recording = True
        print("Recording Started.")
    if markerNum == "2":
        recording = False
        array = numpy.array(result)
        result = []
        numpy.savetxt(filePath, array, delimiter=",", fmt='%.14f')
        #f.close()
        server.shutdown()
        #return result
        print("Recording Stopped.")


def main():
    global recording
    global server
    dispatche = dispatcher.Dispatcher()
    dispatche.map("/debug", print)
    dispatche.map("/muse/acc", muse_handler)
    dispatche.map("/Marker/*", marker_handler)
    print("Press 1 to starting and 2 to stopping")
    if server == None:
        server = osc_server.ThreadingOSCUDPServer((ip, port), dispatche)  # Sostituire con il tuo IP
    # print("Listening on UDP port " + str(port) + "\nSend Marker 1 to Start recording and Marker 2 to Stop Recording.")
    server.serve_forever()
    #return result


if __name__ == "__main__":
    print("CIAO")
    #result = []
    main()