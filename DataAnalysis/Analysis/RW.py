from os.path import join as join
from os.path import dirname as parent
from os.path import realpath as realPath

from TheWorld_pb2 import TheWorld
from TypeChangeCommit_pb2 import TypeChangeCommit
from Models.Models.CommitInfo_pb2 import CommitInfo
from Models.Models.Project_pb2 import Project
from Models.Models.ProcessedCodeMappings_pb2 import ProcessedCodeMappings
from Models.Models.MigrationData_pb2 import MigrationData
from Verification_pb2 import Verification


def readFile(filename):
    try:
        filehandle = open(filename)
        s = filehandle.read()
        filehandle.close()
        return s
    except:
        # print(filename,  ' Not found')
        return ''

fileDir = parent(parent(parent(realPath('__file__'))))
pathToProtos = join(fileDir, 'TypeChangeMiner/Input/ProtosOut/')


def readAll(fileName, kind, protos=pathToProtos):
    sizeFile = readFile(join(protos, fileName + 'BinSize.txt'))
    if(sizeFile == ''):
        # print(fileName, " Not found")
        return []
    sizes = list(map(lambda s: int(s), filter(lambda s: s != '', sizeFile.split(" "))))
    buf = join(protos, fileName + '.txt')
    l = []
    with open(buf, 'rb') as f:
        buf = f.read()
        n = 0
        for s in sizes:
            msg_buf = buf[n:n+s]
            n += s
            c = None
            if kind == "Commit":
                c = CommitInfo()
            if kind == "Project":
                c = Project()
            if kind == "TypeChangeCommit":
                c = TypeChangeCommit()
            if kind == "TheWorld":
                c = TheWorld()
            if kind == "ProcessedCodeMapping":
                c = ProcessedCodeMappings()
            if kind == "Migration":
                c = MigrationData()
            if kind == "Verification":
                c = Verification()
            if c is not None:
                c.ParseFromString(msg_buf)
                l.append(c)
        return l





