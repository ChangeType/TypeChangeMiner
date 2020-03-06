import os
import subprocess
import shutil


def mavenBuild(p, name):

    os.chdir(p)
    if os.path.isdir(os.path.join(p, "bin")):
        shutil.rmtree(os.path.join(p, "bin"))
    print("Deleted the jar for" + name )
    print("Building ------ " + name)
    out = subprocess.Popen(["mvn", "clean", "install"], stdout=subprocess.PIPE)
    out.wait()
    o,e = out.communicate()
    if e is None:
        print("BUILD \"" + name + "\" ------ SUCCESSFUL")
        # print(o.decode("utf-8"))
    else:
        print("BUILD \"" + name + "\" ------ FAILURE")
        print(e.decode("utf-8"))
    return o,e

def gradleBuild(p, name):

    os.chdir(p)
    shutil.rmtree(os.path.join(pathToRMiner, "bin"))
    print("Deleted the jar for" + name )
    print("Building ------ " + name)
    out = subprocess.Popen(("./gradlew", "jar"), stdout=subprocess.PIPE)
    out.wait()
    o,e = out.communicate()
    if e is None:
        print("BUILD \"" + name + "\" ------ SUCCESSFUL")
        # print(o.decode("utf-8"))
    else:
        print("BUILD \"" + name + "\" ------ FAILURE")
        print(e.decode("utf-8"))
    return o,e

baseDir = os.path.dirname(__file__)
pathToRMiner = os.path.join(baseDir, 'lib/RMiner')
pathToModels = os.path.join(baseDir, 'Models')
pathToMigrationMiner = os.path.join(baseDir, 'MigrationMiner/MigrationMiner/')



o, e = mavenBuild(pathToModels, "Models")
if e is None and os.path.isdir(os.path.join(baseDir, "bin")):
    o, e = gradleBuild(pathToRMiner, "RefactoringMiner")
    if e is None and os.path.isdir(os.path.join(baseDir, "bin")):
        o, e = mavenBuild(baseDir, pathToMigrationMiner)
        if e is None and os.path.isdir(os.path.join(baseDir, "bin")):
            o, e = mavenBuild(baseDir, "SimpleTypeChangeMiner")






