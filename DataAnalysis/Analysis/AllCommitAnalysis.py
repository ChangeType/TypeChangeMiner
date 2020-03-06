from Analysis.RW import readAll
import pandas as pd
from pandas import DataFrame as df
from collections import Counter as C
from Models.Models.PrettyPrint import pretty, prettyElementKind


def labelRefactoring(ref: str) -> str:
    if "Type" in ref:
        return "Change Type"
    if "Extract" in ref:
        if "class" not in ref and "Class" not in ref and "Interface" not in ref:
            return "Extract"
    if "Move" in ref:
        if "class" not in ref and "Class" not in ref and "Interface" not in ref and "Source Folder" not in ref:
            return "Move"
    if "Rename" in ref:
        if "class" not in ref and "Class" not in ref and "Interface" not in ref:
            return "Rename"
    return "Others"


def genLatexNewCommandLong(commandName: str, value: int) -> str:
    return "\\newcommand{\\" + commandName + "}{" + f'{value:,}' + "\\xspace}"


def genLatexNewCommandFP(commandName: str, value: float) -> str:
    return "\\newcommand{\\" + commandName + "}{" + "{:4.4f}".format(value) + "\\xspace}"


def isDependencyUpdate(c):
    return len(c.dependencyUpdate.added) > 0 or len(c.dependencyUpdate.removed) > 0 or len(
        c.dependencyUpdate.update) > 0


projs = readAll("Projects", "Project",
                protos="/Users/ameya/Research/TypeChangeStudy/SimpleTypeChangeMiner/Output/ProtosOut/")

typeChangerefactorings = []
refactorings = []
latexCommandsInts = C({})
latexCommandsFPs = C({})
for p in projs:
    typeChangeCommits = readAll("TypeChangeCommit_" + p.name, "TypeChangeCommit",
                                protos="/Users/ameya/Research/TypeChangeStudy/TypeChangeMiner/Output/")
    if len(typeChangeCommits) > 0:
        latexCommandsInts += C({'corpusSize': 1})
        commits = readAll("commits_" + p.name, "Commit")
        latexCommandsInts += C({'noOfCommitsAnalyzed': len(commits)})
        for c in commits:
            if isDependencyUpdate(c):
                latexCommandsInts += C({'commitsWithDependencyUpd': 1})
            if len(c.refactorings) > 0:
                latexCommandsInts += C({'commitsWithRefactoring': 1})
                if c.isTypeChangeReported:
                    latexCommandsInts += C({'commitsWithCTT': 1})
                    if isDependencyUpdate(c):
                        latexCommandsInts += C({'commitsWithCTTAndDependencyUpd': 1})
                refs = list(map(lambda r: (labelRefactoring(r.name), r.name, r.occurences), c.refactorings))
                refactorings.extend(refs)
                if any(r[0] == "Rename" for r in refs):
                    latexCommandsInts += C({'commitsWithRename': 1})
                if any(r[0] == "Extract" for r in refs):
                    latexCommandsInts += C({'commitsWithExtract': 1})
                if any(r[0] == "Move" for r in refs):
                    latexCommandsInts += C({'commitsWithMove': 1})
                latexCommandsInts += C({'NoOfRefactoringsMined': sum(r.occurences for r in c.refactorings)})

        for tc in typeChangeCommits:
            for tca in tc.typeChanges:
                if pretty(tca.b4) != pretty(tca.aftr):
                    latexCommandsInts += C({'NoOfTCIMined': len(tca.typeChangeInstances)})
                for tci in tca.typeChangeInstances:
                    if prettyElementKind(tci.elementKindAffected) == "Field":
                        typeChangerefactorings.append(("ChangeType", "Field", 1))
                    if prettyElementKind(tci.elementKindAffected) == "LocalVariable":
                        typeChangerefactorings.append(("ChangeType", "LocalVariable", 1))
                    if prettyElementKind(tci.elementKindAffected) == "Parameter":
                        typeChangerefactorings.append(("ChangeType", "Parameter", 1))
                    if prettyElementKind(tci.elementKindAffected) == "Return":
                        typeChangerefactorings.append(("ChangeType", "Return", 1))

refactoringDf = pd.DataFrame(refactorings, columns=['Kind', 'Refactoring', 'Occurences'])

refByType = refactoringDf.groupby("Refactoring")["Occurences"].sum()

refByKind = refactoringDf.groupby('Kind')['Occurences'].sum()

latexCommandsInts += C({'noOfOtherRef': refByKind["Others"]})
latexCommandsInts += C({'noOfExtracts': refByKind["Extract"]})
latexCommandsInts += C({'noOfRenames': refByKind["Rename"]})
latexCommandsInts += C({'noOfMoves': refByKind["Move"]})
latexCommandsInts += C({'noOfCTT': refByKind["Change Type"]})


changeTyperefactoringDf = pd.DataFrame(typeChangerefactorings, columns=['Kind', 'Refactoring', 'Occurences'])
changeTyperefByType = changeTyperefactoringDf.groupby("Refactoring")["Occurences"].sum()

latexCommandsInts += C({'noOfChangeTypeParam': changeTyperefByType["Parameter"]})
latexCommandsInts += C({'noOfChangeTypeRet': changeTyperefByType["Return"]})
latexCommandsInts += C({'noOfChangeTypeField': changeTyperefByType["Field"]})
latexCommandsInts += C({'noOfChangeTypeVar': changeTyperefByType["LocalVariable"]})

latexCommandsInts += C({'noOfMoveField': refByType["Move And Rename Attribute"] + refByType["Move Attribute"]})
latexCommandsInts += C({'noOfMoveOperation': latexCommandsInts['noOfMoves'] - latexCommandsInts['noOfMoveField']})

latexCommandsInts += C({'noOfRenameMethod': refByType["Rename Method"]})
latexCommandsInts += C({'noOfRenameParam': refByType["Rename Parameter"]})
latexCommandsInts += C({'noOfRenameField': refByType["Rename Attribute"]})
latexCommandsInts += C({'noOfRenameVar': refByType["Rename Variable"]})
latexCommandsInts += C({'noOfExtractVar': refByType["Extract Variable"] + refByType["Extract Attribute"]})
latexCommandsInts += C({'noOfExtractMethod': refByType["Extract Method"] + refByType["Extract And Move Method"]})

latexCommandsInts += C(
    {'noOfCttRemovedRefactoringEffect': latexCommandsInts['noOfCTT'] - latexCommandsInts['NoOfTCIMined']})

latexCommandsFPs += C(
    {'CTTvsRename': latexCommandsInts['NoOfTCIMined'] / latexCommandsInts['noOfRenames']})
latexCommandsFPs += C(
    {'CTTvsMove': latexCommandsInts['NoOfTCIMined'] / latexCommandsInts['noOfMoves']})
latexCommandsFPs += C(
    {'CTTvsExtract': latexCommandsInts['NoOfTCIMined'] / latexCommandsInts['noOfExtracts']})

latexCommandDFInt = pd.DataFrame.from_dict(dict(latexCommandsInts), orient='index', columns=['Value'])
latexCommandDFFP = pd.DataFrame.from_dict(dict(latexCommandsFPs), orient='index', columns=['Value'])

for k, v in dict(latexCommandsInts).items():
    print(genLatexNewCommandLong(k, v))

for k, v in dict(latexCommandsFPs).items():
    print(genLatexNewCommandFP(k, v))

# print(latexCommandDFInt)
# print(latexCommandDFFP)
