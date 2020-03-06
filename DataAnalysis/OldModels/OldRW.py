from OldModels.CommitInfo_pb2 import CommitInfo
import OldModels.Project_pb2 as p
import os
from google.protobuf.internal.decoder import _DecodeVarint32

def readFile(filename):
    try:
        filehandle = open(filename)
        s = filehandle.read()
        filehandle.close()
        return s
    except:
        print(filename,  ' Not found')
        return ''

fileDir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.realpath('__file__'))))
pathToProtos = os.path.join(fileDir, 'TypeChangeMiner/Input/ProtosOut/')

def readProject(fileName):
    sizes = list(map(lambda s: int(s),filter(lambda s: s != '', readFile(os.path.join(pathToProtos, fileName + 'BinSize.txt')).split(" "))))
    print(sizes)
    buf = os.path.join(pathToProtos, fileName + '.txt')
    l = []
    with open(buf, 'rb') as f:
        buf = f.read()
        n = 0
        for s in sizes:
            msg_buf = buf[n:n+s]
            n += s
            prj = p.Project()
            try:
                prj.ParseFromString(msg_buf)
                if prj.name != '':
                    l.append(prj)
                else:
                    print("Project with no name? ")
            except :
                print("Could not Read project name ")


    return l


def readCommit(fileName):
    sizes = list(map(lambda s: int(s),filter(lambda s: s != '', readFile(os.path.join(pathToProtos, fileName + 'BinSize.txt')).split(" "))))
    if len(sizes) == 0:
        print(fileName, " Not found")
    buf = os.path.join(pathToProtos, fileName + '.txt')
    l = []
    with open(buf, 'rb') as f:
        buf = f.read()
        n = 0
        for s in sizes:
            msg_buf = buf[n:n+s]
            n += s
            c = CommitInfo()
            c.ParseFromString(msg_buf)
            l.append(c)
    return l




