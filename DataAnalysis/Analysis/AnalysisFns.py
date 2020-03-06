from collections import Counter as C

from ElementKind_pb2 import ElementKind
from PrettyPrint import pretty, prettyTypeKind, prettyElementKind, prettyNameSpace1
from TheWorld_pb2 import TheWorld
from TypeChangeAnalysis_pb2 import TypeChangeAnalysis
from collections import namedtuple as nt

TypeChange = nt('TypeChange', ['before', 'after'])


def tca_level_information(tca: TypeChangeAnalysis) -> (C, C, C, dict):
    int_commands = C({})
    ratioDict = {}
    featuresToCommandMap = {}

    noOfTypeChangeInstances = len(tca.typeChangeInstances)

    featuresToCommandMap.setdefault('General', set()).add(('Total Type changes', 'noOfTypeChanges'))
    int_commands += C({'noOfTypeChanges': 1})
    # type_changes = C({TypeChange(before=pretty(tca.b4), after=pretty(tca.aftr)): 1})
    if tca.b4ComposesAfter:

        featuresToCommandMap.setdefault('Hierarchy vs Composition', set()).add(
            ('Total Type changes with composition relation', 'noOfCompositionTC'))
        int_commands += C({'noOfCompositionTC': 1})

        featuresToCommandMap.setdefault('Adaptation Complexity', set()).add(('For composition', 'Composition'))
        ratioDict.setdefault("Composition", []).append(get_adaptation_complexity(tca))
    else:
        if tca.hierarchyRelation != '':

            hierarchyRelation = 'noOf' + tca.hierarchyRelation + 'TC'
            featuresToCommandMap.setdefault('Hierarchy vs Composition', set()).add(
                ('total ' + hierarchyRelation, hierarchyRelation))
            int_commands += C({hierarchyRelation: 1})

            # RQ5
            if tca.hierarchyRelation == "T_SUPER_R" or tca.hierarchyRelation == "R_SUPER_T":
                featuresToCommandMap.setdefault('Adaptation Complexity', set()).add(
                    ('For Parent Child', 'Parent Child'))
                ratioDict.setdefault("Parent Child", []).append(get_adaptation_complexity(tca))
            if tca.hierarchyRelation == "SIBLING":
                featuresToCommandMap.setdefault('Adaptation Complexity', set()).add(('For Sibling', 'Sibling'))
                ratioDict.setdefault("Sibling", []).append(get_adaptation_complexity(tca))
        else:
            featuresToCommandMap.setdefault('Hierarchy vs Composition', set()).add(
                ('Total Type changes No relation', 'noOfNoHierarchyRelationTCI'))
            int_commands += C(C({'noOfNoHierarchyRelationTCI': 1}))

    if tca.primitiveInfo.widening:
        featuresToCommandMap.setdefault('Primitive Information', set()).add(('Total Widening', 'noOfWideningTCI'))
        int_commands += C({'noOfWideningTCI': noOfTypeChangeInstances})
    if tca.primitiveInfo.narrowing:
        featuresToCommandMap.setdefault('Primitive Information', set()).add(('Total Narrowing', 'noOfNarrowingfTCI'))
        int_commands += C({'noOfNarrowingfTCI': noOfTypeChangeInstances})
    if tca.primitiveInfo.boxing:
        featuresToCommandMap.setdefault('Primitive Information', set()).add(('Total Boxing', 'noOfBoxingTCI'))
        int_commands += C({'noOfBoxingTCI': noOfTypeChangeInstances})
    if tca.primitiveInfo.unboxing:
        featuresToCommandMap.setdefault('Primitive Information', set()).add(('Total Unboxing', 'noOfUnBoxingTCI'))
        int_commands += C({'noOfUnBoxingTCI': noOfTypeChangeInstances})

    return [int_commands, ratioDict, featuresToCommandMap]


def analyzeTheWorld(items, fName):
    featMap = {}
    cmds = C({})
    for v, cnt in items:
        x = "TheWorld " + v
        featMap.setdefault('TheWorld ' + fName, set()).add((v, x))
        cmds += C({x: cnt})
    return [cmds, featMap]


def tca_tci_analysis(tcas: TypeChangeAnalysis, theWorld: TheWorld) -> [C, dict, dict]:
    int_commands = C({})
    ratioDict = {}
    featuresToCommandMap = {}
    typeChanges = C({})

    cmd, featMap = analyzeTheWorld(theWorld.visibilityMap.items(), 'Element Visibility')
    int_commands += cmd
    featuresToCommandMap = mergeDictSet(featuresToCommandMap, featMap)

    cmd, featMap = analyzeTheWorld(theWorld.elementKindMap.items(), 'Element Kind')
    int_commands += cmd
    featuresToCommandMap = mergeDictSet(featuresToCommandMap, featMap)

    cmd, featMap = analyzeTheWorld(theWorld.typeKindMap.items(), 'Syntactic Transformation Kind')
    int_commands += cmd
    featuresToCommandMap = mergeDictSet(featuresToCommandMap, featMap)

    cmd, featMap = analyzeTheWorld(theWorld.nameSpaceMap.items(), 'Namespace Change')
    int_commands += cmd
    featuresToCommandMap = mergeDictSet(featuresToCommandMap, featMap)

    for tca in tcas:

        noOfTCIs = len(tca.typeChangeInstances)
        zzz = TypeChange(before=pretty(tca.b4), after=pretty(tca.aftr))
        typeChanges += C({zzz: noOfTCIs})

        int_Cmds, ratios, feats = tca_level_information(tca)
        int_commands += int_Cmds
        ratioDict = mergeDict(ratioDict, ratios)
        featuresToCommandMap = mergeDictSet(featuresToCommandMap, feats)

        featuresToCommandMap.setdefault('General', set()).add(('Total TCIs', 'noOfTCI'))
        int_commands += C({'noOfTCI': noOfTCIs})

        namespaceChange = 'noOf' + prettyNameSpace1(tca.nameSpacesB4) + "To" + prettyNameSpace1(
            tca.nameSpaceAfter) + "TCI"
        featuresToCommandMap.setdefault('Namespace Change', set()).add(
            (prettyNameSpace1(tca.nameSpacesB4), namespaceChange))
        int_commands += C({namespaceChange: noOfTCIs})

        for tci in tca.typeChangeInstances:

            elem_Visibility = 'noOf' + tci.visibility + 'TCI'
            featuresToCommandMap.setdefault('Element Visibility', set()).add((tci.visibility, elem_Visibility))
            int_commands += C({elem_Visibility: 1})

            elem_kind = 'noOf' + prettyElementKind(tci.elementKindAffected) + 'TCI'
            featuresToCommandMap.setdefault('Element Kind', set()).add(
                (prettyElementKind(tci.elementKindAffected), elem_kind))
            int_commands += C({elem_kind: 1})

            syntactic_transformations = 'noOf' + tci.syntacticUpdate.transformation.replace(' ', '') + 'TCI'
            featuresToCommandMap.setdefault('Syntactic Transformation Kind', set()).add(
                (prettyTypeKind(tci.b4.root.kind), syntactic_transformations))
            int_commands += C({syntactic_transformations: 1})

            if prettyTypeKind(tci.b4.root.kind) == "Primitive" or prettyTypeKind(tci.aftr.root.kind) == "Primitive":
                featuresToCommandMap.setdefault('Primitive Information', set()).add(
                    ('Total TCI involving primitives', 'noInvolvesPrimitiveTCI'))
                int_commands += C({"noInvolvesPrimitiveTCI": 1})

            featuresToCommandMap.setdefault('General', set()).add(('Total TCIs with Renames', 'noOfRenameAndTCI'))
            int_commands += C({'noOfRenameAndTCI': 1 if tci.nameB4 != tci.nameAfter else 0})

            if prettyElementKind(tci.elementKindAffected) == "Field":
                featuresToCommandMap.setdefault('General', set()).add(('Total TCIs with Renames Fields', 'noOfRenameAndTCIField'))
                int_commands += C({'noOfRenameAndTCIField': 1 if tci.nameB4 != tci.nameAfter else 0})

            if prettyElementKind(tci.elementKindAffected) == "LocalVariable":
                featuresToCommandMap.setdefault('General', set()).add(('Total TCIs with Renames LocalVariables', 'noOfRenameAndTCILocalVariable'))
                int_commands += C({'noOfRenameAndTCILocalVariable': 1 if tci.nameB4 != tci.nameAfter else 0})

            if prettyElementKind(tci.elementKindAffected) == "Return":
                featuresToCommandMap.setdefault('General', set()).add(('Total TCIs with Renames Returns', 'noOfRenameAndTCIReturn'))
                int_commands += C({'noOfRenameAndTCIReturn': 1 if tci.nameB4 != tci.nameAfter else 0})

            if prettyElementKind(tci.elementKindAffected) == "Parameter":
                featuresToCommandMap.setdefault('General', set()).add(('Total TCIs with Renames Parameters', 'noOfRenameAndTCIParameter'))
                int_commands += C({'noOfRenameAndTCIParameter': 1 if tci.nameB4 != tci.nameAfter else 0})

            # RQ3 (Binary and Source Incompatibility)
            if tci.visibility == 'public':
                if tca.primitiveInfo.widening:
                    widening_tci = 'noOfPublicWideningTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Element Widening TCI", widening_tci))
                    int_commands += C({widening_tci: 1})
                if tci.elementKindAffected == ElementKind.Return and tca.hierarchyRelation == 'T_SUPER_R':
                    ret_type_sub_type = 'noOfPublicRetTypeToSubTypeTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Return Type to SubType TCI", ret_type_sub_type))
                    int_commands += C({ret_type_sub_type: 1})
                if tci.elementKindAffected == ElementKind.Parameter and tca.hierarchyRelation == 'R_SUPER_T':
                    param_type_super_type = 'noOfPublicParamTypeToSuperTypeTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Param Type to Super Type TCI", param_type_super_type))
                    int_commands += C({param_type_super_type: 1})
                if tci.elementKindAffected == ElementKind.Return and tca.primitiveInfo.narrowing:
                    narrow_tci = 'noOfPublicRetTypeNarrowTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Return Type narrowing TCI", narrow_tci))
                    int_commands += C({narrow_tci: 1})
                if tca.primitiveInfo.narrowing:
                    public_narrow_tci = 'noOfPublicNarrowTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Element narrowing TCI", public_narrow_tci))
                    int_commands += C({public_narrow_tci: 1})
                if tca.primitiveInfo.boxing or tca.primitiveInfo.unboxing:
                    unwrap_tci = 'noOfPublicPrimitiveWrapUnwrapTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public element box unbox", unwrap_tci))
                    int_commands += C({unwrap_tci: 1})
                if tca.primitiveInfo.widening and tci.elementKindAffected == ElementKind.Parameter:
                    primitive_widening_tci = 'noOfPublicParamPrimitiveWideningTCI'
                    featuresToCommandMap.setdefault('B&S Incompatibility', set()).add(
                        ("Public Param widening", primitive_widening_tci))
                    int_commands += C({primitive_widening_tci: 1})

    return [int_commands, ratioDict, featuresToCommandMap, typeChanges]


def get_adaptation_complexity(tca: TypeChangeAnalysis) -> float:
    c = 0
    tc = 0
    for t in tca.typeChangeInstances:
        tc += len(t.codeMapping)
        c += sum(not c.isSame for c in t.codeMapping)
    return 0.0 if tc == 0 else c / tc


def theWorldContains(world, c):
    for w in world:
        if w.sha == c:
            return w
    return None


def mergeDict(d1: dict, d2: dict) -> dict:
    for key, value in d2.items():
        if key in d1.keys():
            d1[key].extend(value)
        else:
            d1[key] = value
    return d1


def mergeDictSet(d1: dict, d2: dict) -> dict:
    for key, value in d2.items():
        if key in d1.keys():
            d1[key] = d1[key].union(value)
        else:
            d1[key] = value
    return d1
