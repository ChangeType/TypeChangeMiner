import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import statistics
from os.path import join as join
from os.path import dirname as parent
from os.path import realpath as realPath
from matplotlib import rc
import matplotlib.patches as mpatches
from matplotlib.lines import Line2D
import scipy.stats as stats
import scikit_posthocs as posthocs
from adjustText import adjust_text


def _set_box_color(bodies, color):
    for body in bodies:
        body.set_facecolor(color)
        body.set_edgecolor(color)
        body.set_alpha(1)


def do_stats_stuff(m):
    ll = list(m.values())
    args = [zz for zz in ll]
    s = stats.kruskal(*args)
    print("Kruskal Result: " + str(s))

    lt = []
    for k,v in m.items():
        for v1 in v:
            lt.append((k,v1))

    df = pd.DataFrame(lt, columns =['label', 'ratio'])

    f = posthocs.posthoc_conover(df, sort=True, p_adjust='bonferroni', group_col='label', val_col='ratio')
    print("Dunn's Result")
    print(str(f))

    for k, v in m.items():
        if 'External To External' in k:
            q3 = np.percentile(v, 25, interpolation='midpoint')
            iqr = (1.5 * stats.iqr(v, interpolation='midpoint')) + q3
            outlier = []
            for v1 in v:
                if v1 >= iqr:
                    outlier.append(v1)
            if len(outlier) > 0:
                print(outlier)

    return s


def violin(m, xlabel, ylabel, isVertical=False, isLog=False, height=3, legend=True, legendDontOVerlap=False):
    # rc('text', usetex=True)

    xlabel = xlabel.replace("Project-level frequency ", "")
    xlabel = xlabel.replace("Project-level proportion", "")
    outputPlot = join(parent(parent(realPath('__file__'))), 'OutputPlots/' + xlabel + ylabel + ".pdf").replace(' ', '')
    sns.set(font_scale=1.3)
    sns.set_style("whitegrid")
    fig, axes = plt.subplots(figsize=(9, height))

    m = dict(sorted(m.items(), key=lambda item: statistics.median(item[1]), reverse=True))

    r = axes.violinplot(dataset=list(m.values()), showmeans=True, showmedians=True, vert=isVertical)
    _set_box_color(r['bodies'], "#a9a9a9")
    r['cmeans'].set_color('red')
    r['cmedians'].set_color('blue')
    r['cbars'].set_color('#353839')
    r['cmaxes'].set_color('#353839')
    r['cmins'].set_color('#3b444b')

    labels = list(m.keys())
    labels = list(map(lambda x: x.replace("P ", ""), labels))
    labels = list(map(lambda x: x.replace("F ", ""), labels))
    labels = list(map(lambda x: x.replace("Type Parameter", "Type Argument"), labels))

    red_patch = Line2D([0], [0], color='red', linewidth=2)
    blue_patch = Line2D([0], [0], color='blue', linewidth=2)

    # plt.legend([red_patch, blue_patch, extra1, extra2], ['Mean', 'Median', 'H(2)='+'{:.2e}'.format(h), 'p-value='+'{:0.2e}'.format(p)])
    if len(m) > 1:
        h, p = do_stats_stuff(m)
        axes.set_title('p-value='+ ('{:0.2e}'.format(p) if p != 0.0 else '0')+ ' ' +' H(2)='+'{:.2e}'.format(h), fontdict={'fontsize': 12})
    # plt.text(0.5,1, )
    # adjust_text(texts)

    if legend:
        if legendDontOVerlap:
            plt.legend([red_patch, blue_patch], ['Mean', 'Median'],bbox_to_anchor=(1,0.5), loc="center right",
                   bbox_transform=plt.gcf().transFigure)
        else:
            plt.legend([red_patch, blue_patch], ['Mean', 'Median'])

    if isLog:
        axes.set(xscale="log")
    if isVertical:
        axes.set_xticks(np.arange(1, len(labels) + 1))
        axes.set_xticklabels(labels, linespacing=0.9)
    else:
        axes.set_yticks(np.arange(1, len(labels) + 1))
        axes.set_yticklabels(labels, linespacing=0.9)
    plt.tight_layout()
    plt.savefig(outputPlot, format="pdf", dpi=300, bbox_inches='tight')
    plt.show()


