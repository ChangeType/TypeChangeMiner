import statistics
from collections import Counter as C
from collections import namedtuple as nt
from os.path import dirname as parent
from os.path import join as join
from os.path import realpath as realpath
import Analysis.CreatePlots as cp
import git
import time
import pandas as pd
import scipy.stats as stats
import scikit_posthocs as posthocs
import PrettyPrint as Pretty
from Analysis.AnalysisFns import tca_tci_analysis, theWorldContains, mergeDict, mergeDictSet
from Analysis.RW import readAll
import re

fileDir = parent(parent(parent(realpath('__file__'))))
pathToTypeChanges = join(fileDir, 'TypeChangeMiner/Output/')

Mapping = nt('Mapping', ['nameB4', 'nameAfter', 'before', 'after'])
TypeChangeExample = nt('TypeChangeExample', ['before', 'after', 'mappings'])

projects = readAll("Projects", "Project")  # [:10]

pathToVerificationProtos = join(pathToTypeChanges, 'Verification')
verificationData = []
for vp in ['guava', 'error-prone', 'java-parser', 'CoreNLP']:
    verificationData.extend(readAll('Verification_'+vp, 'Verification', protos=pathToVerificationProtos))


matched = 0
matched = sum(vd.matched for vd in verificationData)

print('TFMiner precision = ' , matched/len(verificationData))


projects = list(filter(
    lambda x: 'jfreechart' not in x.name and '99' not in x.name and 'comma' not in x.name and 'binnavi' not in x.name,
    projects))

pr_cmt={}
for pr in projects:
    typeChangeCommits = readAll("TypeChangeCommit_" + pr.name, "TypeChangeCommit", protos=pathToTypeChanges)
    for tc in typeChangeCommits:
        pr_cmt.setdefault(pr.name,[]).append(tc.sha)

def findCommit(sha):
    for prj,cmt in pr_cmt.items():
        if sha in cmt:
            return prj
    return None


migrationMap = {}
popularMigrationMapJdk = {}
popularMigrationMapInternal = {}
popularMigrationMapExternal = {}

pathToMigrationProtos = join(pathToTypeChanges, 'Migration')
for p in projects:
    migrations = readAll('Migration_'+p.name, 'Migration', protos= pathToMigrationProtos)
    for m in migrations:
        ns = Pretty.prettyNameSpace1(m.namespace)
        if 'TypeVariable' not in ns:
            migrationMap.setdefault(ns,[]).append(m.ratio)
            if m.ratio == 1.0:
                if "Jdk" in ns:
                    #popularMigrationMapJdk.setdefault(Pretty.pretty(m.type),[]).append(m)
                    popularMigrationMapJdk.setdefault(Pretty.pretty(m.type),[]).extend(list(dict.fromkeys(list(map(lambda x: findCommit(x.sha), m.commitToType)))))
                if "External" in ns:
                    popularMigrationMapExternal.setdefault(Pretty.pretty(m.type),[]).extend(list(dict.fromkeys(list(map(lambda x: findCommit(x.sha), m.commitToType)))))
                    # popularMigrationMapExternal.setdefault(Pretty.pretty(m.type),[]).append(m)
                if "Internal" in ns:
                    popularMigrationMapInternal.setdefault(Pretty.pretty(m.type),[]).extend(list(dict.fromkeys(list(map(lambda x: findCommit(x.sha), m.commitToType)))))
                    # popularMigrationMapInternal.setdefault(Pretty.pretty(m.type),[]).append(m)

for t, g in migrationMap.items():
    print(t)
    print(statistics.median(g))

popularMigrationMapJdk = dict(sorted(popularMigrationMapJdk.items(), key=lambda x: len(x[1]), reverse=True))
popularMigrationMapInternal = dict(sorted(popularMigrationMapInternal.items(), key=lambda x: len(x[1]), reverse=True))
popularMigrationMapExternal = dict(sorted(popularMigrationMapExternal.items(), key=lambda x: len(x[1]), reverse=True))

for ns, ratios in migrationMap.items():
    migrationsDetected = sum(x == 1.0 for x in ratios)
    print("FrequencyMigration"+ns +"  " + str(migrationsDetected/len(ratios)))

cp.violin(migrationMap, xlabel="Migration", ylabel="Ratio", isLog=False, height=3 , legendDontOVerlap=True)

migrationsDetected = 0
totalCandidate = 0
for ns, ratios in migrationMap.items():
    migrationsDetected += sum(x == 1.0 for x in ratios)
    totalCandidate += len(ratios)
    # print("FrequencyMigration"+ns +"  " + str(migrationsDetected/len(ratios)))

print(migrationsDetected/totalCandidate)

def getSubDict1(fromDict: dict, orFromDict: dict, forKeys) -> dict:
    d = {}
    for l in forKeys:
        if l[1] in fromDict.keys():
            d[l[1]] = fromDict[l[1]]
        else:
            if l[1] in orFromDict:
                d[l[1]] = len(orFromDict[l[1]])
    return d


def getSubDict(fromDict: dict, forKeys) -> dict:
    d = {}
    for l in forKeys:
        if l[1] in fromDict.keys():
            d[l[1]] = fromDict[l[1]]
    return d


def calculateProportions(feats, name, project_int_commands, label=False):
    ratios = {}
    featureMapProps = {}
    total_count = 0
    for feat in feats:
        if 'DontKnow' not in feat[0] and 'TypeVariable' not in feat[0] and 'DontKnow' not in feat[1]:
            total_count += project_int_commands[feat[1]]
    for feat in feats:
        if 'DontKnow' not in feat[0] and 'TypeVariable' not in feat[0]:
            if not label:
                featureMapProps.setdefault('Project-level proportion' + name, set()).add(
                    ('Proportion each project of ' + feat[0], "P " + feat[0]))
                ratios.setdefault("P " + feat[0], []).append(project_int_commands[feat[1]] / total_count)
                z = project_int_commands[feat[1]] / total_count
                if z == 1.0:
                    print()
            else:
                x = feat[1].replace('noOf', '').replace('TCI', '')
                x = ''.join(map(lambda x: x if x.islower() else " " + x, x))
                featureMapProps.setdefault('Project-level proportion' + name, set()).add(
                    ('Proportion each project of ' + x, "P " + x))
                z = project_int_commands[feat[1]] / total_count
                if z == 1.0:
                    print()
                ratios.setdefault("P " + x, []).append(z)

    return [ratios, featureMapProps]


def normalizeProjectLevel(feats, theWorldFeats, name, project_int_commands, label=False):
    ratios = {}
    featureMapProps = {}
    for feat in feats:
        if 'DontKnow' not in feat[1] and 'TypeVariable' not in feat[1]:
            for worldFeat in theWorldFeats:
                if feat[0] == worldFeat[0]:
                    if not label:
                        featureMapProps.setdefault('Project-level frequency ' + name, set()).add(
                            ('Freq for each project ' + feat[0], "F " + feat[0]))
                        ratios.setdefault("F " + feat[0], []).append(
                            project_int_commands[feat[1]] / project_int_commands[worldFeat[1]])
                    else:
                        x = feat[1].replace('noOf', '').replace('TCI', '')
                        x = ''.join(map(lambda x: x if x.islower() else " " + x, x))
                        featureMapProps.setdefault('Project-level frequency ' + name, set()).add(
                            ('Freq for each project ' + x, "F " + x))
                        ratios.setdefault("F " + x, []).append(
                            project_int_commands[feat[1]] / project_int_commands[worldFeat[1]])
    return [ratios, featureMapProps]


def get_timestamp_for_Commit(sha, commit_timestamp):
    for x in commit_timeStamp:
        if x[0] == sha:
            return x[1]
    return None


cntr = 0
for p in projects:
    if p.totalCommits > 10000:
        cntr += 1

pathToTypeChanges = join(fileDir, 'TypeChangeMiner/Output/')
all_int_commands = C({})
all_float_commands = C({})
all_ratios = {}
all_featureCommandMap = {}
all_typeChanges_TCI = C({})
migration_candidates = C({})
all_typeChanges_Project = C({})

for p in projects:

    repo = git.Repo("/Users/ameya/Research/TypeChangeStudy/Corpus/Project_" + p.name + "/" + p.name)
    tree = repo.tree()
    commit_timeStamp = list(
        map(lambda c: (c.hexsha, time.asctime(time.gmtime(c.committed_date))), list(repo.iter_commits('HEAD'))))
    # print(commit.message, time.asctime(time.gmtime(commit.committed_date)))

    project_typeChanges = C({})
    project_typeChanges_migration_Candidates = {}
    project_int_commands = C({})
    project_float_commands = C({})
    project_ratios = {}
    project_featureCommandMap = {}
    typeChangeCommits = readAll("TypeChangeCommit_" + p.name, "TypeChangeCommit", protos=pathToTypeChanges)
    theWorld_project = readAll("TheWorld_" + p.name, "TheWorld", protos=pathToTypeChanges)
    if len(theWorld_project) > 0:
        project_featureCommandMap.setdefault('General', set()).add(('Total Projects Analyzed', 'corpusSize'))
        project_int_commands += C({'corpusSize': 1})
        if len(typeChangeCommits) > 0:
            print(p.name + "   " + str(len(typeChangeCommits)))
            for t in typeChangeCommits:
                theWorld = theWorldContains(theWorld_project, t.sha)
                if theWorld is not None:

                    timestamp_commit = get_timestamp_for_Commit(t.sha, commit_timeStamp)
                    project_featureCommandMap.setdefault('General', set()).add(
                        ('Total Commits Analyzed', 'noOfCommitsAnalyzed'))
                    project_int_commands += C({'noOfCommitsAnalyzed': 1})

                    analyzeTC = []
                    for tc in t.typeChanges:
                        if Pretty.pretty(tc.b4) != Pretty.pretty(tc.aftr):
                            if tc.b4.root.isTypeVariable and tc.aftr.root.isTypeVariable:
                                print("Remove TypeVar to Type Var from analysis")
                            else:
                                analyzeTC.append(tc)

                    int_commands, ratios_commit, featureMAp, typeChange_TCI = tca_tci_analysis(
                        analyzeTC, theWorld)

                    project_int_commands += int_commands
                    project_ratios = mergeDict(project_ratios, ratios_commit)
                    project_featureCommandMap = mergeDictSet(project_featureCommandMap, featureMAp)
                    project_typeChanges += typeChange_TCI

                    for migration in t.migrationAnalysis:
                        if 'Project' in migration.typeMigrationLevel:
                            project_typeChanges_migration_Candidates.setdefault(migration.type, []).append(
                                ('Project', timestamp_commit))

                    for k, v in typeChange_TCI.items():
                        for m, l in project_typeChanges_migration_Candidates.items():
                            if m == k.before and 'Project' not in l[0]:
                                project_typeChanges_migration_Candidates.setdefault(k.before, []).append(
                                    (t.sha, timestamp_commit))

            # Calculate project level proportions

            for e in ['Element Visibility', 'Element Kind', 'Namespace Change', 'Syntactic Transformation Kind']:
                prop_Ratios, feats = calculateProportions(project_featureCommandMap[e], e, project_int_commands, label=(
                        e == 'Namespace Change' or e == 'Syntactic Transformation Kind'))
                project_ratios = mergeDict(project_ratios, prop_Ratios)
                project_featureCommandMap = mergeDictSet(project_featureCommandMap, feats)

                freq_Ratios, freq_feats = normalizeProjectLevel(project_featureCommandMap[e],
                                                                project_featureCommandMap['TheWorld ' + e], e,
                                                                project_int_commands, label=(
                            e == 'Namespace Change' or e == 'Syntactic Transformation Kind'))
                project_ratios = mergeDict(project_ratios, freq_Ratios)
                project_featureCommandMap = mergeDictSet(project_featureCommandMap, freq_feats)

        for k in project_typeChanges.keys():
            all_typeChanges_Project.setdefault(k, set()).add(p.name)

    project_typeChanges_migration_Candidates = dict(filter(lambda item: len(item[1]) > 1 or item[1][0][1] == 'Project',
                                                           project_typeChanges_migration_Candidates.items()))

    all_int_commands += project_int_commands
    all_ratios = mergeDict(all_ratios, project_ratios)
    all_featureCommandMap = mergeDictSet(all_featureCommandMap, project_featureCommandMap)
    all_typeChanges_TCI += project_typeChanges
    migration_candidates = mergeDict(migration_candidates, project_typeChanges_migration_Candidates)

# print(stats.kruskal(pr.iloc[:,0],pck.iloc[:,0],c.iloc[:,0],s.iloc[:,0]))
# f = posthocs.posthoc_dunn([pr.iloc[:,0],c.iloc[:,0],s.iloc[:,0],pck.iloc[:,0]])

all_featureCommandMap.setdefault('General', set()).add(('Total Popular Type Changes', 'popularTypeChanges'))
all_featureCommandMap.setdefault('General', set()).add(
    ('Total number of instances for Popular Type Changes', 'noOfTypeChangesOfPopularTypeChanges'))
for k, v in all_typeChanges_Project.items():
    if len(v) > 1:
        all_int_commands += C({'popularTypeChanges': 1})
        all_int_commands += C({'noOfTypeChangesOfPopularTypeChanges': (all_typeChanges_TCI[k])})

all_featureCommandMap.setdefault('General', set()).add(('Total Distinct Type Changes', 'totalDistinctTypeChanges'))
all_int_commands += C({'totalDistinctTypeChanges': len(all_typeChanges_TCI.keys())})

for key, value in all_featureCommandMap.items():
    print('------------------------------')
    print("For " + key)
    print('------------------------------')
    d = getSubDict1(all_int_commands, all_ratios, value)
    if len(d) > 0:
        latexCommandDFInt = pd.DataFrame.from_dict(dict(d), orient='index', columns=['Value'])
    print(latexCommandDFInt.to_string())
    print('==============================')

for key, value in all_featureCommandMap.items():
    x = []
    if "Visibility" in key:
        d = getSubDict(all_ratios, value)
        if len(d) > 0:
            if 'frequency' in key or 'Frequency' in key:
                x.append([key, 'Frequency', d, True])
            if 'proportion' in key or 'Proportion' in key:
                x.append([key, 'Proportion', d, False])
    for z in x:
        if "P Block" in z[2].keys():
            del z[2]["P Block"]
        if "F Block" in z[2].keys():
            del z[2]["F Block"]
        cp.violin(z[2], xlabel=z[0], ylabel=z[1], isLog=z[3])

for key, value in all_featureCommandMap.items():
    x = []
    if "Element Kind" in key:
        d = getSubDict(all_ratios, value)
        if len(d) > 0:
            if 'frequency' in key or 'Frequency' in key:
                x.append([key, 'Frequency', d, True])
            if 'proportion' in key or 'Proportion' in key:
                x.append([key, 'Proportion', d, False])
    for z in x:
        cp.violin(z[2], xlabel=z[0], ylabel=z[1], isLog=z[3])


def getExample(d, of):
    if of in d.keys():
        return d[of]
    return "No Example"


syntacticExamples = {
    'Primitive-> Simple': '$\mathsf{\it{(int\:to\:UserId)}}$',
    'Update Container': '$\mathsf{\it{(List<\!T\!>\:to\:Set<\!T\!>)}}$',
    'Update Type Parameters': '$\mathsf{\it{(List<\!File\!>\:to\:List<\!Path\!>)}}$',
    'Update Simple': '$\mathsf{\it{(String\:to\:URI)}}$',
    'Update Primitive': '$\mathsf{\it{(int\:to\:long)}}$'
}

x = []
for key, value in all_featureCommandMap.items():
    if "Syntactic" in key:
        d = getSubDict(all_ratios, value)
        if len(d) > 0:
            if 'frequency' in key or 'Frequency' in key:
                x.append([key, 'Frequency', d, True])
            elif 'proportion' in key or 'Proportion' in key:
                x.append([key, 'Proportion', d, False])

proportionData = {}
for z in x:
    if 'Proportion' in z[1]:
        m = dict(sorted(z[2].items(), key=lambda item: statistics.median(item[1]), reverse=True))
        if len(z[2]) > 5:
            m = dict(sorted(z[2].items(), key=lambda item: statistics.mean(item[1]), reverse=True)[:5])
        z[2] = m
        proportionData = z[2]

frequencyMap = {}
for z in x:
    if 'Frequency' in z[1]:
        for k1, v1 in proportionData.items():
            frequencyMap[k1] = z[2][k1.replace('P ', 'F ')]
        z[2] = dict(sorted(frequencyMap.items(), key=lambda item: statistics.median(item[1]), reverse=True))

for z in x:
    for k in list(z[2].keys()):
        if 'Frequency' in z[1]:
            z[2][k.replace("with", "->").replace('Replace ', '')] = z[2].pop(k)
        else:
            newKey = k.replace("with", "->").replace('Replace ', '')
            z[2][newKey + "\n" + getExample(syntacticExamples, newKey.replace('P  ', '').replace('F  ', ''))] = z[
                2].pop(k)
    cp.violin(z[2], xlabel=z[0], ylabel=z[1], isLog=z[3])

namespaceExamples = {
    'Internal To Jdk': '$\mathsf{\it{org.apache.ignite.UuId}}$ -> \n$\mathsf{\it{java.io.File}}$',
    'Jdk To Jdk': '$\mathsf{\it{java.util.List}}$ -> \n$\mathsf{\it{java.util.Set}}$',
    'Internal To Internal': '$\mathsf{\it{org.apache.ignite.GridCache}}$ -> \n$\mathsf{\it{org.apache.ignite.IgniteCache}}$',
    'External To External': '$\mathsf{\it{org.apache.common.logging.Log}}$ -> \n$\mathsf{\it{org.slf4j.Logger}}$',
    'Jdk To Internal': '$\mathsf{\it{java.io.File}}$ -> \n$\mathsf{\it{org.geoserver.resource.Resource}}$'
}

x = []
for key, value in all_featureCommandMap.items():
    if "Namespace" in key:
        d = getSubDict(all_ratios, value)
        if len(d) > 0:
            if 'frequency' in key or 'Frequency' in key:
                x.append([key, 'Frequency', d, True])
            elif 'proportion' in key or 'Proportion' in key:
                x.append([key, 'Proportion', d, False])

proportionData = {}
for z in x:
    if 'Proportion' in z[1]:
        m = dict(sorted(z[2].items(), key=lambda item: statistics.median(item[1]), reverse=True))
        if len(z[2]) > 5:
            m = dict(sorted(z[2].items(), key=lambda item: statistics.mean(item[1]), reverse=True)[:5])
        z[2] = m
        proportionData = z[2]

frequencyMap = {}
for z in x:
    if 'Frequency' in z[1]:
        for k1, v1 in proportionData.items():
            frequencyMap[k1] = z[2][k1.replace('P ', 'F ')]
        z[2] = dict(sorted(frequencyMap.items(), key=lambda item: statistics.median(item[1]), reverse=True))

for z in x:
    for k in list(z[2].keys()):
        if 'Frequency' in z[1]:
            z[2][k.replace("with", "->").replace('Replace ', '')] = z[2].pop(k)
            # cp.violin(z[2], xlabel=z[0], ylabel=z[1], isLog=z[3])
        else:
            newKey = k.replace("with", "->").replace('Replace ', '')
            z[2][newKey + "\n" + getExample(namespaceExamples, newKey.replace('P  ', '').replace('F  ', ''))] = z[
                2].pop(k)

    cp.violin(z[2], xlabel=z[0], ylabel=z[1], isLog=z[3], height=3 if 'Frequency' in z[1] else 4)

for key, value in all_featureCommandMap.items():
    d = getSubDict(all_ratios, value)
    if len(d) > 0:
        print('------------------------------')
        print("For " + key)
        if 'Adaptation' in key:
            cp.violin(d, key, 'Complexity', isVertical=True, legendDontOVerlap=True)
