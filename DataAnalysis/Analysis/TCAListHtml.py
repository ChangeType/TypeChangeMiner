import shutil
import html
from jinja2 import Environment, FileSystemLoader
import os
from collections import namedtuple as nt
from collections import Counter as C
from Analysis.RW import readAll
from PrettyPrint import pretty, prettyNameSpace1
from Analysis.CreatePlots import violin

pathToPages = os.path.join(os.path.dirname(os.path.dirname(os.path.realpath('__file__'))), "docs/PagesAllTCA")
pathToProjectsHtml = os.path.join(pathToPages, "projects.html")
pathToIndexFile = os.path.join(os.path.dirname(pathToPages), "index.html")

env = Environment(loader=FileSystemLoader(os.path.dirname(os.path.dirname(os.path.realpath('__file__')))))
template = env.get_template("HTMLTemplate/TypeChangeSummaryTemplate.html")
templateTCI = env.get_template("HTMLTemplate/TypeChangeInstances.html")

projects = readAll("Projects", "Project",
                   protos="/Users/ameya/Research/TypeChangeStudy/SimpleTypeChangeMiner/Output/ProtosOut/")
items = []
TypeChange = nt('TypeChange', ['before', 'after'])



open()


if os.path.isdir(pathToPages):
    shutil.rmtree(pathToPages)
try:
    os.mkdir(pathToPages)
except OSError:
    print("Could not make directory")

typeChanges = {}
typeChanges_commit = {}
typeChange_project = {}
typeChange_hierarchy = {}
typeChange_nameSpace = {}
typeChange_primitiveInfo = {}
pop_typeChange_nameSpace = C({})

processedCodeMapping = readAll("ProcessedCodeMapping", "ProcessedCodeMapping",
                               protos="/Users/ameya/Research/TypeChangeStudy/TypeChangeMiner/Output/CodeMapping/")

x = set()
for pc in processedCodeMapping:
    if len(pc.relevantStmts) > 0:
        for rs in pc.relevantStmts:
            if len(rs.mapping) > 0:
                for em in rs.mapping:
                    x.add(em.replacement)

print(x)


def getStatementMapping(typechange):
    for p in processedCodeMapping:
        if pretty(p.b4) == typechange.before and pretty(p.aftr) == typechange.after:
            return p.relevantStmts

    return []


for p in projects:
    typeChangeCommits = readAll("TypeChangeCommit_" + p.name, "TypeChangeCommit",
                                protos="/Users/ameya/Research/TypeChangeStudy/TypeChangeMiner/Output/")

    commitSummary = []
    for cmt in typeChangeCommits:
        for tca in cmt.typeChanges:
            if not tca.b4.root.isTypeVariable and not tca.aftr.root.isTypeVariable:
                zzz = TypeChange(before=pretty(tca.b4), after=pretty(tca.aftr))
                if zzz.before == "":
                    print()
                typeChanges.setdefault(zzz, []).extend(tca.typeChangeInstances)
                typeChanges_commit.setdefault(zzz, set()).add(cmt.sha)
                typeChange_project.setdefault(zzz, set()).add(p.name)
                if tca.hierarchyRelation != '' and "NO" not in tca.hierarchyRelation:
                    typeChange_hierarchy[zzz] = tca.hierarchyRelation
                elif tca.b4ComposesAfter:
                    typeChange_hierarchy[zzz] = "Composition"
                elif tca.primitiveInfo is not None:
                    if tca.primitiveInfo.boxing:
                        typeChange_primitiveInfo[zzz] = "Boxing"
                    elif tca.primitiveInfo.unboxing:
                        typeChange_primitiveInfo[zzz] = "Unboxing"
                    elif tca.primitiveInfo.narrowing:
                        typeChange_primitiveInfo[zzz] = "Narrowing"
                    elif tca.primitiveInfo.widening:
                        typeChange_primitiveInfo[zzz] = "Widening"
                typeChange_nameSpace[zzz] = prettyNameSpace1(tca.nameSpacesB4) + " -> " + prettyNameSpace1(
                    tca.nameSpaceAfter)

typeChangeSummary = []

renameTypeChange = {}

for typeChange, instances in typeChanges.items():
    for instance in instances:
        if instance.nameB4 != instance.nameAfter:
            renameTypeChange.setdefault(typeChange, instances)
            break


renameRatios = []
theCounter = 0
for typeChange, instances in renameTypeChange.items():
    cnt = 0
    for instance in instances:
        if instance.nameB4 != instance.nameAfter:
            theCounter += 1
            cnt+=1
    renameRatios.append(cnt/len(instances))

d = {}
d.setdefault("",renameRatios)

violin(d, "RenameRatio", "Ratio", height=2, legend=False)



tciCounter = 0
for k, v in typeChanges.items():
     if len(typeChange_project[k]) > 1 or len(typeChanges_commit) > 4:
        f = dict(TypeChange=html.escape(k.before + " to " + k.after),
                 hierarchy=typeChange_hierarchy[k] if k in typeChange_hierarchy.keys() else "-",
                 primitiveInfo=typeChange_primitiveInfo[k] if k in typeChange_primitiveInfo.keys() else "-",
                 namespace=typeChange_nameSpace[k] if k in typeChange_nameSpace.keys() else "-")

        mapping = getStatementMapping(k)

        minedReplacements = {}

        for m in mapping:
            for em in m.mapping:
                minedReplacements.setdefault(em.replacement, []).append(
                    dict(frm=html.escape(em.b4 if em.b4 else m.b4), to=html.escape(em.aftr if em.aftr else m.after),
                         urlB4=m.urlbB4,
                         urlAftr=m.urlAftr, stmtB4=m.b4, strmtAftr=m.after))
        mappings = []

        if typeChange_nameSpace[k] == 'Jdk -> Jdk':
            pop_typeChange_nameSpace += C({'Jdk -> Jdk': 1})
        elif 'Internal' in typeChange_nameSpace[k]:
            pop_typeChange_nameSpace += C({'InvolvesInternal': 1})
        elif 'External' in typeChange_nameSpace[k]:
            pop_typeChange_nameSpace += C({'InvolvesExternal': 1})

        for key, val in minedReplacements.items():
            mappings.append(dict(name=key.replace("\\percent", ""), instances=val))

        pathToProjectTCI = os.path.join(pathToPages, "tci" + str(tciCounter) + ".html")
        with open(pathToProjectTCI, 'a') as fh:
            fh.write(templateTCI.render(mappings=mappings, TypeChange=html.escape(k.before + " to " + k.after),
                                        hierarchy=typeChange_hierarchy[k] if k in typeChange_hierarchy.keys() else "-",
                                        primitiveInfo=typeChange_primitiveInfo[
                                            k] if k in typeChange_primitiveInfo.keys() else "-",
                                        namespace=typeChange_nameSpace[k] if k in typeChange_nameSpace.keys() else "-",
                                        noOfInst=len(v), noOfCommits=len(typeChanges_commit[k]),
                                        noOfProjects=str(typeChange_project[k])))
            fh.write('\n')
            fh.close()
        tciCounter += 1

        typeChangeSummary.append(
            dict(b4=html.escape(k.before), after=html.escape(k.after), tcisLink=pathToProjectTCI, noOfTCI=len(v),
                 noOfCommits=len(typeChanges_commit[k]),
                 noOfProjects=len(typeChange_project[k]),
                 hierarchy=typeChange_hierarchy[k] if k in typeChange_hierarchy.keys() else "-",
                 primitiveInfo=typeChange_primitiveInfo[
                     k] if k in typeChange_primitiveInfo.keys() else "-",
                 namespace=typeChange_nameSpace[k] if k in typeChange_nameSpace.keys() else "-"))

typeChangeSummary = sorted(typeChangeSummary, key=lambda i: (i['noOfTCI']), reverse=True)

pathToProjectCommits = os.path.join(pathToPages, "TypeChangeSummary.html")
with open(pathToProjectCommits, 'a') as fh:
    fh.write(template.render(typeChangeAnalysisList=typeChangeSummary))
    fh.write('\n')
    fh.close()

print()
