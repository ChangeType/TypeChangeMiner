from CommitInfo_pb2 import CommitInfo
import Project_pb2 as p
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

def readProject(fileName):
    fileDir = os.path.dirname(os.path.realpath('__file__'))
    sizes = list(map(lambda s: int(s),filter(lambda s: s != '', readFile(os.path.join(fileDir, '../../Output/ProtosOut/' + fileName + 'BinSize.txt')).split(" "))))
    print(sizes)
    buf = os.path.join(fileDir, '../../Output/ProtosOut/' + fileName + '.txt')
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
    fileDir = os.path.dirname(os.path.realpath('__file__'))
    sizes = list(map(lambda s: int(s),filter(lambda s: s != '', readFile(os.path.join(fileDir, '../../Output/ProtosOut/' + fileName + 'BinSize.txt')).split(" "))))
    if len(sizes) == 0:
        print(fileName, " Not found")
    buf = os.path.join(fileDir, '../../Output/ProtosOut/' + fileName + '.txt')
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




