from datetime import datetime
from numpy import mean
import numpy
from pythonosc import dispatcher
from pythonosc import osc_server

from os.path import dirname, join
from com.chaquo.python import Python

port = 5000

files_dir = str(Python.getPlatform().getApplication().getFilesDir())
filePath = join(dirname(files_dir), 'files/mean.csv')

recording = False
enable = False
accX = []
accY = []
result = []
server = None


def muse_handler(address, *args):
    """handle all muse data"""
    global result
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
                col1 = []
                accX = []
                accY = []
                print(media)
                result.append(media)
                array = numpy.array(result)
                result = []
                numpy.savetxt(filePath, array, delimiter=",", fmt='%.14f')
                server.shutdown()



def marker_handler(address: str, i):
    global recording
    global server

    dateTimeObj = datetime.now()
    timestampStr = dateTimeObj.strftime("%Y-%m-%d %H:%M:%S.%f")
    markerNum = address[-1]
    if markerNum == "1":
        recording = True
        print("Recording Started.")
    if markerNum == "2":
        recording = False
        server.shutdown()
        print("Recording Stopped.")


def main(ip):
    global recording
    global server
    dispatche = dispatcher.Dispatcher()
    dispatche.map("/debug", print)
    dispatche.map("/muse/acc", muse_handler)
    dispatche.map("/Marker/*", marker_handler)
    print("Press 1 to starting and 2 to stopping")
    if server == None:
        server = osc_server.ThreadingOSCUDPServer((ip, port), dispatche)  # Sostituire con il tuo IP
    server.serve_forever()


if __name__ == "__main__":
    main()