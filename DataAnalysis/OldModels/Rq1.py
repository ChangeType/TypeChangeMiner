from collections import Counter as C
from collections import namedtuple as nt
from os.path import dirname as parent
from os.path import join as join
from os.path import realpath as realpath
# import Analysis.CreatePlots as cp
import git
import time
import pandas as pd
import OldRW

# from Analysis.RW import readAll

fileDir = parent(parent(parent(realpath('__file__'))))
pathToTypeChanges = join(fileDir, 'TypeChangeMiner/Output/')

all_int_commands = C({})
projects = OldRW.readProject('Projects')

for p in projects:
    commits = OldRW.readCommit('commits_' + p.name)
    if len(commits) > 0:
        project_int_commands = C({})
        project_int_commands += C({'corpusSize': 1})
        for cmt in commits:
            project_int_commands += C({'noOfCommitsAnalyzed': 1})
            if len(cmt.refactorings) > 0:
                project_int_commands += C({'NoOfRefactoringsMined': len(cmt.refactorings)})

    all_int_commands += project_int_commands

print(all_int_commands)