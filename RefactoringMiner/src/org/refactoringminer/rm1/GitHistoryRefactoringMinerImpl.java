package org.refactoringminer.rm1;

import com.google.common.collect.Sets;
import gr.uom.java.xmi.TypeFactMiner.Models.GlobalContext;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLModelASTReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.vavr.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.refactoringminer.api.*;
import org.refactoringminer.util.GitServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.*;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

public class GitHistoryRefactoringMinerImpl implements GitHistoryRefactoringMiner {

//	public static List<String> JavaClasses = Try.ofFailable(() -> Files.lines(Paths.get("D:/MyProjects/JavaClasses.txt")))
//			.onFailure(Throwable::printStackTrace).map(x->x.collect(toList())).orElse(new ArrayList<>());
//
//	//
//	public static List<String> JavaLangClasses = Try.ofFailable(() -> Files.lines(Paths.get("D:/MyProjects/JavaLangClasses.txt")))
//			.onFailure(Throwable::printStackTrace).map(x->x.collect(toList())).orElse(new ArrayList<>());

	Logger logger = LoggerFactory.getLogger(GitHistoryRefactoringMinerImpl.class);
	private Set<RefactoringType> refactoringTypesToConsider = null;
	private GitHub gitHub;

	public GitHistoryRefactoringMinerImpl() {
		this.setRefactoringTypesToConsider(
			RefactoringType.RENAME_CLASS,
			RefactoringType.MOVE_CLASS,
			RefactoringType.MOVE_SOURCE_FOLDER,
			RefactoringType.RENAME_METHOD,
			RefactoringType.EXTRACT_OPERATION,
			RefactoringType.INLINE_OPERATION,
			RefactoringType.MOVE_OPERATION,
			RefactoringType.PULL_UP_OPERATION,
			RefactoringType.PUSH_DOWN_OPERATION,
			RefactoringType.MOVE_ATTRIBUTE,
			RefactoringType.MOVE_RENAME_ATTRIBUTE,
			RefactoringType.REPLACE_ATTRIBUTE,
			RefactoringType.PULL_UP_ATTRIBUTE,
			RefactoringType.PUSH_DOWN_ATTRIBUTE,
			RefactoringType.EXTRACT_INTERFACE,
			RefactoringType.EXTRACT_SUPERCLASS,
			RefactoringType.EXTRACT_SUBCLASS,
			RefactoringType.EXTRACT_CLASS,
			RefactoringType.EXTRACT_AND_MOVE_OPERATION,
			RefactoringType.MOVE_RENAME_CLASS,
			RefactoringType.RENAME_PACKAGE,
			RefactoringType.EXTRACT_VARIABLE,
			RefactoringType.INLINE_VARIABLE,
			RefactoringType.RENAME_VARIABLE,
			RefactoringType.RENAME_PARAMETER,
			RefactoringType.RENAME_ATTRIBUTE,
			RefactoringType.REPLACE_VARIABLE_WITH_ATTRIBUTE,
			RefactoringType.PARAMETERIZE_VARIABLE,
			RefactoringType.MERGE_VARIABLE,
			RefactoringType.MERGE_PARAMETER,
			RefactoringType.MERGE_ATTRIBUTE,
			RefactoringType.SPLIT_VARIABLE,
			RefactoringType.SPLIT_PARAMETER,
			RefactoringType.SPLIT_ATTRIBUTE,
			RefactoringType.CHANGE_RETURN_TYPE,
			RefactoringType.CHANGE_VARIABLE_TYPE,
			RefactoringType.CHANGE_PARAMETER_TYPE,
			RefactoringType.CHANGE_ATTRIBUTE_TYPE,
			RefactoringType.EXTRACT_ATTRIBUTE
		);
	}

	public void setRefactoringTypesToConsider(RefactoringType ... types) {
		this.refactoringTypesToConsider = new HashSet<RefactoringType>();
		for (RefactoringType type : types) {
			this.refactoringTypesToConsider.add(type);
		}
	}
	
	private void detect(GitService gitService, Repository repository, final RefactoringHandler handler, Iterator<RevCommit> i) {
		int commitsCount = 0;
		int errorCommitsCount = 0;
		int refactoringsCount = 0;

		File metadataFolder = repository.getDirectory();
		File projectFolder = metadataFolder.getParentFile();
		String projectName = projectFolder.getName();
		
		long time = System.currentTimeMillis();
		while (i.hasNext()) {
			RevCommit currentCommit = i.next();
			try {
				List<Refactoring> refactoringsAtRevision = detectRefactorings(gitService, repository, handler, projectFolder, currentCommit);
				refactoringsCount += refactoringsAtRevision.size();
				
			} catch (Exception e) {
				logger.warn(String.format("Ignored revision %s due to error", currentCommit.getId().getName()), e);
				handler.handleException(currentCommit.getId().getName(),e);
				errorCommitsCount++;
			}

			commitsCount++;
			long time2 = System.currentTimeMillis();
			if ((time2 - time) > 20000) {
				time = time2;
				logger.info(String.format("Processing %s [Commits: %d, Errors: %d, Refactorings: %d]", projectName, commitsCount, errorCommitsCount, refactoringsCount));
			}
		}

		handler.onFinish(refactoringsCount, commitsCount, errorCommitsCount);
		logger.info(String.format("Analyzed %s [Commits: %d, Errors: %d, Refactorings: %d]", projectName, commitsCount, errorCommitsCount, refactoringsCount));
	}

	protected List<Refactoring> detectRefactorings(GitService gitService, Repository repository, final RefactoringHandler handler, File projectFolder, RevCommit currentCommit) throws Exception {
		List<Refactoring> refactoringsAtRevision;
		String commitId = currentCommit.getId().getName();
		List<String> filePathsBefore = new ArrayList<String>();
		List<String> filePathsCurrent = new ArrayList<String>();
		Map<String, String> renamedFilesHint = new HashMap<String, String>();
		gitService.fileTreeDiff(repository, currentCommit, filePathsBefore, filePathsCurrent, renamedFilesHint);
		Set<String> repositoryDirectoriesBefore = new LinkedHashSet<String>();
		Set<String> repositoryDirectoriesCurrent = new LinkedHashSet<String>();
		Map<String, String> fileContentsBefore = new LinkedHashMap<String, String>();
		Map<String, String> fileContentsCurrent = new LinkedHashMap<String, String>();
		try (RevWalk walk = new RevWalk(repository)) {
			// If no java files changed, there is no refactoring. Also, if there are
			// only ADD's or only REMOVE's there is no refactoring
			if (!filePathsBefore.isEmpty() && !filePathsCurrent.isEmpty() && currentCommit.getParentCount() > 0) {
				RevCommit parentCommit = currentCommit.getParent(0);

				populateFileContents(repository, parentCommit, filePathsBefore, fileContentsBefore, repositoryDirectoriesBefore);
				UMLModel parentUMLModel = createModel(fileContentsBefore, repositoryDirectoriesBefore, repository, parentCommit);

				populateFileContents(repository, currentCommit, filePathsCurrent, fileContentsCurrent, repositoryDirectoriesCurrent);
				UMLModel currentUMLModel = createModel(fileContentsCurrent, repositoryDirectoriesCurrent, repository, currentCommit);

				refactoringsAtRevision = parentUMLModel.diff(currentUMLModel, renamedFilesHint).getRefactorings();
				refactoringsAtRevision = filter(refactoringsAtRevision);
			} else {
				//logger.info(String.format("Ignored revision %s with no changes in java files", commitId));
				refactoringsAtRevision = Collections.emptyList();
			}

			refactoringsAtRevision = removeFPTypeChanges(refactoringsAtRevision);

			GraphTraversalSource gr = traversal().withRemote(DriverRemoteConnection.using("localhost",8182,"g"));
			GlobalContext classStructureAfter;

			if (unResolvedTypeChanges(refactoringsAtRevision)){

				GlobalContext classStructureB4 = new GlobalContext(repository, currentCommit.getParent(0), gr);
				classStructureAfter = new GlobalContext(repository, currentCommit, gr);
				refactoringsAtRevision = refactoringsAtRevision.stream()
						.map(x -> resolveTypeChange(classStructureB4, classStructureAfter, x)).collect(Collectors.toList());

				refactoringsAtRevision = refactoringEffect(refactoringsAtRevision);

				if(isDontKnowNameSpace(refactoringsAtRevision)){
					refactoringsAtRevision = refactoringsAtRevision.stream()
							.map(x -> populateNameSpacendTypeSem(classStructureB4, classStructureAfter, x))
							.collect(Collectors.toList());
				}
			}
			else{
				classStructureAfter = new GlobalContext(repository, currentCommit, gr);
			}
			refactoringsAtRevision = refactoringsAtRevision.stream()
					.map(x -> populateRealTypeChanges(classStructureAfter,x))
					.collect(toList());

			handler.handle(commitId, refactoringsAtRevision);
			gr.close();
			walk.dispose();
		}
		return refactoringsAtRevision;
	}


	public static List<Refactoring> removeFPTypeChanges(List<Refactoring> rs){
		return rs.stream()
				.filter(x-> !x.isTypeRelatedChange() || ((TypeRelatedRefactoring) x).getTypeB4() != null && ((TypeRelatedRefactoring) x).getTypeAfter() != null)
				.collect(toList());
	}


	public static Tuple2<Collection<String>, Collection<String>> getAddedRemovedClasses(GlobalContext gcB4, GlobalContext gcAfter){
		return Tuple.of(subtract(gcB4.getClassesInternal(),gcAfter.getClassesInternal())
				, subtract(gcB4.getClassesInternal(),gcAfter.getClassesInternal())) ;
	}

	public static List<Refactoring> refactoringEffect(List<Refactoring> r){
		Function2<List<Refactoring>,Refactoring, Boolean> isNotDueToOtherRefs = (rs,re) -> {
			List<Tuple2<List<String>, List<String>>> moveAndRenamedClasses = rs.stream()
					.filter(x->isClassMoveOrAndRename(x))
					.map(c -> Tuple.of(c.getInvolvedClassesBeforeRefactoring(), c.getInvolvedClassesAfterRefactoring()))
					.collect(toList());

			if(re.isTypeRelatedChange()){
				TypeRelatedRefactoring tr = (TypeRelatedRefactoring) re;
				boolean mathces = moveAndRenamedClasses.stream()
						.noneMatch(t -> t._1().stream().anyMatch(c -> tr.getTypeB4().getTypeStr().contains(c))
								&& t._2().stream().anyMatch(c -> tr.getTypeAfter().getTypeStr().contains(c)));
				if(mathces){
					System.out.println("Removed " + tr.getTypeB4().getTypeStr() + " ---> " + tr.getTypeAfter().getTypeStr() + " Due to Rename/&Move Class refactoring");
				}
			}
			return true;
		};

		Function1<Refactoring, Boolean> filterNonCT = isNotDueToOtherRefs.apply(r);
		return new ArrayList<>(r.stream().filter(filterNonCT::apply).collect(toList()));
	}

	public static boolean isClassMoveOrAndRename(Refactoring x) {
		return x.getRefactoringType().equals(RefactoringType.MOVE_CLASS) || x.getRefactoringType().equals(RefactoringType.RENAME_CLASS)
				|| x.getRefactoringType().equals(RefactoringType.MOVE_RENAME_CLASS);
	}


	public Refactoring updateTypeRelatedChange(Consumer<TypeRelatedRefactoring> updater, Refactoring r){
		if(r.isTypeRelatedChange()){
			updater.accept((TypeRelatedRefactoring)r);
		}
		return r;
	}

	private Refactoring populateRealTypeChanges( GlobalContext gc, Refactoring x) {
		return updateTypeRelatedChange(tr -> tr.extractRealTypeChange(gc),x);
	}

	private Refactoring populateNameSpacendTypeSem(GlobalContext gcB4, GlobalContext gcAftr, Refactoring x) {
		return updateTypeRelatedChange(tr -> {
			if (!tr.getTypeB4().isKnowsAllNameSpace())    tr.updateTypeNameSpaceBefore(gcB4);
			if(!tr.getTypeAfter().isKnowsAllNameSpace())  tr.updateTypeNameSpaceAfter(gcAftr);
		}, x);
	}

	private Refactoring resolveTypeChange(GlobalContext classStructureB4, GlobalContext classStructureAfter, Refactoring x) {
		return updateTypeRelatedChange(tr -> {
			if (!tr.getTypeB4().isResolved())   tr.updateTypeB4(classStructureB4);
			if(!tr.getTypeAfter().isResolved()) tr.updateTypeAfter(classStructureAfter);
		},x);
	}


	private static boolean unResolvedTypeChanges(List<Refactoring> refactorings){
		List<TypeRelatedRefactoring> typeRelatedRefactorings = refactorings.stream().filter(Refactoring::isTypeRelatedChange)
				.map(x -> (TypeRelatedRefactoring) x)
				.collect(toList());
		return typeRelatedRefactorings.stream().anyMatch(x->!x.isResolved());
	}

	private static boolean isDontKnowNameSpace(List<Refactoring> refactorings){
		List<TypeRelatedRefactoring> typeRelatedRefactorings = refactorings.stream().filter(Refactoring::isTypeRelatedChange)
				.map(x -> (TypeRelatedRefactoring) x)
				.collect(toList());
		return typeRelatedRefactorings.stream().anyMatch(x->!x.getTypeB4().isKnowsAllNameSpace() || !x.getTypeAfter().isKnowsAllNameSpace());
	}

	private void populateFileContents(Repository repository, RevCommit commit,
			List<String> filePaths, Map<String, String> fileContents, Set<String> repositoryDirectories) throws Exception {
		logger.info("Processing {} {} ...", repository.getDirectory().getParent().toString(), commit.getName());
		RevTree parentTree = commit.getTree();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(filePaths.contains(pathString)) {
					ObjectId objectId = treeWalk.getObjectId(0);
					ObjectLoader loader = repository.open(objectId);
					StringWriter writer = new StringWriter();
					IOUtils.copy(loader.openStream(), writer);
					fileContents.put(pathString, writer.toString());
				}
				if(pathString.endsWith(".java")) {
					String directory = pathString.substring(0, pathString.lastIndexOf("/"));
					repositoryDirectories.add(directory);
					//include sub-directories
					String subDirectory = new String(directory);
					while(subDirectory.contains("/")) {
						subDirectory = subDirectory.substring(0, subDirectory.lastIndexOf("/"));
						repositoryDirectories.add(subDirectory);
					}

				}
			}
		}
	}

	protected List<Refactoring> detectRefactorings(final RefactoringHandler handler, File projectFolder, String cloneURL, String currentCommitId) {
		List<Refactoring> refactoringsAtRevision = Collections.emptyList();
		try {
			List<String> filesBefore = new ArrayList<String>();
			List<String> filesCurrent = new ArrayList<String>();
			Map<String, String> renamedFilesHint = new HashMap<String, String>();
			String parentCommitId = populateWithGitHubAPI(cloneURL, currentCommitId, filesBefore, filesCurrent, renamedFilesHint);
			File currentFolder = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + currentCommitId);
			File parentFolder = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + parentCommitId);
			if (!currentFolder.exists()) {	
				downloadAndExtractZipFile(projectFolder, cloneURL, currentCommitId);
			}
			if (!parentFolder.exists()) {	
				downloadAndExtractZipFile(projectFolder, cloneURL, parentCommitId);
			}
			if (currentFolder.exists() && parentFolder.exists()) {
				UMLModel currentUMLModel = createModel(currentFolder, filesCurrent, repositoryDirectories(currentFolder));
				UMLModel parentUMLModel = createModel(parentFolder, filesBefore,repositoryDirectories(currentFolder));
				// Diff between currentModel e parentModel
				refactoringsAtRevision = parentUMLModel.diff(currentUMLModel, renamedFilesHint).getRefactorings();
				refactoringsAtRevision = filter(refactoringsAtRevision);
			}
			else {
				logger.warn(String.format("Folder %s not found", currentFolder.getPath()));
			}
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", currentCommitId), e);
			handler.handleException(currentCommitId, e);
		}
		handler.handle(currentCommitId, refactoringsAtRevision);

		return refactoringsAtRevision;
	}

	private  Set<String> repositoryDirectories(File folder) {
		final String systemFileSeparator = Matcher.quoteReplacement(File.separator);
		Set<String> repositoryDirectories = new LinkedHashSet<String>();

		Collection<File> files = FileUtils.listFiles(folder, null, true);
		for(File file : files) {
			String path = file.getPath();
			String relativePath = path.substring(folder.getPath().length()+1, path.length()).replaceAll(systemFileSeparator, "/");
			if(relativePath.endsWith(".java")) {
				String directory = relativePath.substring(0, relativePath.lastIndexOf("/"));
				repositoryDirectories.add(directory);
				//include sub-directories
				String subDirectory = new String(directory);
				while(subDirectory.contains("/")) {
					subDirectory = subDirectory.substring(0, subDirectory.lastIndexOf("/"));
					repositoryDirectories.add(subDirectory);
				}
			}
		}
		return repositoryDirectories;
	}

	private void downloadAndExtractZipFile(File projectFolder, String cloneURL, String commitId)
			throws IOException {
		String downloadLink = cloneURL.substring(0, cloneURL.indexOf(".git")) + "/archive/" + commitId + ".zip";
		File destinationFile = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + commitId + ".zip");
		logger.info(String.format("Downloading archive %s", downloadLink));
		FileUtils.copyURLToFile(new URL(downloadLink), destinationFile);
		logger.info(String.format("Unzipping archive %s", downloadLink));
		java.util.zip.ZipFile zipFile = new ZipFile(destinationFile);
		try {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(projectFolder.getParentFile(),  entry.getName());
				if (entry.isDirectory()) {
					entryDestination.mkdirs();
				} else {
					entryDestination.getParentFile().mkdirs();
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(entryDestination);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					out.close();
				}
			}
		} finally {
			zipFile.close();
		}
	}

	private String populateWithGitHubAPI(String cloneURL, String currentCommitId,
			List<String> filesBefore, List<String> filesCurrent, Map<String, String> renamedFilesHint) throws IOException {
		logger.info("Processing {} {} ...", cloneURL, currentCommitId);
		GitHub gitHub = connectToGitHub();
		//https://github.com/ is 19 chars
		String repoName = cloneURL.substring(19, cloneURL.indexOf(".git"));
		GHRepository repository = gitHub.getRepository(repoName);
		GHCommit commit = repository.getCommit(currentCommitId);
		String parentCommitId = commit.getParents().get(0).getSHA1();
		List<GHCommit.File> commitFiles = commit.getFiles();
		for (GHCommit.File commitFile : commitFiles) {
			if (commitFile.getFileName().endsWith(".java")) {
				if (commitFile.getStatus().equals("modified")) {
					filesBefore.add(commitFile.getFileName());
					filesCurrent.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("added")) {
					filesCurrent.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("removed")) {
					filesBefore.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("renamed")) {
					filesBefore.add(commitFile.getPreviousFilename());
					filesCurrent.add(commitFile.getFileName());
					renamedFilesHint.put(commitFile.getPreviousFilename(), commitFile.getFileName());
				}
			}
		}
		return parentCommitId;
	}

	private GitHub connectToGitHub() {
		if(gitHub == null) {
			try {
				Properties prop = new Properties();
				InputStream input = new FileInputStream("github-credentials.properties");
				prop.load(input);
				String username = prop.getProperty("username");
				String password = prop.getProperty("password");
				if (username != null && password != null) {
					gitHub = GitHub.connectUsingPassword(username, password);
					if(gitHub.isCredentialValid()) {
						logger.info("Connected to GitHub with account: " + username);
					}
				}
				else {
					gitHub = GitHub.connect();
				}
			} catch(FileNotFoundException e) {
				logger.warn("File github-credentials.properties was not found in RefactoringMiner's execution directory", e);
			} catch(IOException ioe) {
				ioe.printStackTrace();
			}
		}
		return gitHub;
	}

	protected List<Refactoring> filter(List<Refactoring> refactoringsAtRevision) {
		if (this.refactoringTypesToConsider == null) {
			return refactoringsAtRevision;
		}
		List<Refactoring> filteredList = new ArrayList<Refactoring>();
		for (Refactoring ref : refactoringsAtRevision) {
			if (this.refactoringTypesToConsider.contains(ref.getRefactoringType())) {
				filteredList.add(ref);
			}
		}
		return filteredList;
	}
	
	@Override
	public void detectAll(Repository repository, String branch, final RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		RevWalk walk = gitService.createAllRevsWalk(repository, branch);
		try {
			detect(gitService, repository, handler, walk.iterator());
		} finally {
			walk.dispose();
		}
	}

	@Override
	public void fetchAndDetectNew(Repository repository, final RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		RevWalk walk = gitService.fetchAndCreateNewRevsWalk(repository);
		try {
			detect(gitService, repository, handler, walk.iterator());
		} finally {
			walk.dispose();
		}
	}

	// for .git
	protected UMLModel createModel(Map<String, String> fileContents, Set<String> repositoryDirectories, Repository repo, RevCommit commit) throws Exception {
		return new UMLModelASTReader(fileContents, repositoryDirectories, repo, commit).getUmlModel();
	}

	// for GitGub API
	protected UMLModel createModel(Map<String, String> fileContents, Set<String> repositoryDirectories) throws Exception {
		return new UMLModelASTReader(fileContents, repositoryDirectories).getUmlModel();
	}

	// for download and analyze
	protected UMLModel createModel(File projectFolder, List<String> filePaths, Set<String> repositoryDirectories) throws Exception {
		return new UMLModelASTReader(projectFolder, filePaths, repositoryDirectories).getUmlModel();
	}

	@Override
	public void detectAtCommit(Repository repository, String commitId, RefactoringHandler handler) {
		String cloneURL = repository.getConfig().getString("remote", "origin", "url");
		File metadataFolder = repository.getDirectory();
		File projectFolder = metadataFolder.getParentFile();
		GitService gitService = new GitServiceImpl();
		RevWalk walk = new RevWalk(repository);
		try {
			RevCommit commit = walk.parseCommit(repository.resolve(commitId));
			if (commit.getParentCount() > 0) {
				walk.parseCommit(commit.getParent(0));
				this.detectRefactorings(gitService, repository, handler, projectFolder, commit);
			}
			else {
				logger.warn(String.format("Ignored revision %s because it has no parent", commitId));
			}
		} catch (MissingObjectException moe) {
//			this.detectRefactorings(handler, projectFolder, cloneURL, commitId);
		} catch (RefactoringMinerTimedOutException e) {
			logger.warn(String.format("Ignored revision %s due to timeout", commitId), e);
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", commitId), e);
			handler.handleException(commitId, e);
		} finally {
			walk.close();
			walk.dispose();
		}
	}

	public void detectAtCommit(Repository repository, String commitId, RefactoringHandler handler, int timeout) {
		//ExecutorService service = Executors.newSingleThreadExecutor();
		detectAtCommit(repository, commitId, handler);
		//Future<?> f = null;
//		try {
//			Runnable r = () -> detectAtCommit(repository, commitId, handler);
//			f = service.submit(r);
//			f.get(timeout, TimeUnit.SECONDS);
//		} catch (TimeoutException e) {
//			f.cancel(true);
//		} catch (ExecutionException e) {
//			e.printStackTrace();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		} finally {
//			service.shutdown();
//		}
	}

	@Override
	public String getConfigId() {
	    return "RM1";
	}

	@Override
	public void detectBetweenTags(Repository repository, String startTag, String endTag, RefactoringHandler handler)
			throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		
		Iterable<RevCommit> walk = gitService.createRevsWalkBetweenTags(repository, startTag, endTag);
		detect(gitService, repository, handler, walk.iterator());
	}

	@Override
	public void detectBetweenCommits(Repository repository, String startCommitId, String endCommitId,
			RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		
		Iterable<RevCommit> walk = gitService.createRevsWalkBetweenCommits(repository, startCommitId, endCommitId);
		detect(gitService, repository, handler, walk.iterator());
	}

	@Override
	public Churn churnAtCommit(Repository repository, String commitId, RefactoringHandler handler) {
		GitService gitService = new GitServiceImpl();
		RevWalk walk = new RevWalk(repository);
		try {
			RevCommit commit = walk.parseCommit(repository.resolve(commitId));
			if (commit.getParentCount() > 0) {
				walk.parseCommit(commit.getParent(0));
				return gitService.churn(repository, commit);
			}
			else {
				logger.warn(String.format("Ignored revision %s because it has no parent", commitId));
			}
		} catch (MissingObjectException moe) {
			logger.warn(String.format("Ignored revision %s due to missing commit", commitId), moe);
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", commitId), e);
			handler.handleException(commitId, e);
		} finally {
			walk.close();
			walk.dispose();
		}
		return null;
	}

	@Override
	public void detectAtCommit(String gitURL, String commitId, RefactoringHandler handler, int timeout) {
		ExecutorService service = Executors.newSingleThreadExecutor();
		Future<?> f = null;
		try {
			Runnable r = () -> detectRefactorings(handler, gitURL, commitId);
			f = service.submit(r);
			f.get(timeout, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			f.cancel(true);
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			service.shutdown();
		}
	}

	protected List<Refactoring> detectRefactorings(final RefactoringHandler handler, String gitURL, String currentCommitId) {
		List<Refactoring> refactoringsAtRevision = Collections.emptyList();
		try {
			Set<String> repositoryDirectoriesBefore = ConcurrentHashMap.newKeySet();
			Set<String> repositoryDirectoriesCurrent = ConcurrentHashMap.newKeySet();
			Map<String, String> fileContentsBefore = new ConcurrentHashMap<String, String>();
			Map<String, String> fileContentsCurrent = new ConcurrentHashMap<String, String>();
			Map<String, String> renamedFilesHint = new ConcurrentHashMap<String, String>();
			populateWithGitHubAPI(gitURL, currentCommitId, fileContentsBefore, fileContentsCurrent, renamedFilesHint, repositoryDirectoriesBefore, repositoryDirectoriesCurrent);
			UMLModel currentUMLModel = createModel(fileContentsCurrent, repositoryDirectoriesCurrent);
			UMLModel parentUMLModel = createModel(fileContentsBefore, repositoryDirectoriesBefore);
			//  Diff between currentModel e parentModel
			refactoringsAtRevision = parentUMLModel.diff(currentUMLModel, renamedFilesHint).getRefactorings();
			refactoringsAtRevision = filter(refactoringsAtRevision);
		}
		catch(RefactoringMinerTimedOutException e) {
			logger.warn(String.format("Ignored revision %s due to timeout", currentCommitId), e);
			handler.handleException(currentCommitId, e);
		}
		catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", currentCommitId), e);
			handler.handleException(currentCommitId, e);
		}
		handler.handle(currentCommitId, refactoringsAtRevision);

		return refactoringsAtRevision;
	}

	private void populateWithGitHubAPI(String cloneURL, String currentCommitId,
			Map<String, String> filesBefore, Map<String, String> filesCurrent, Map<String, String> renamedFilesHint,
			Set<String> repositoryDirectoriesBefore, Set<String> repositoryDirectoriesCurrent) throws IOException, InterruptedException {
		logger.info("Processing {} {} ...", cloneURL, currentCommitId);
		GitHub gitHub = connectToGitHub();
		//https://github.com/ is 19 chars
		String repoName = cloneURL.substring(19, cloneURL.indexOf(".git"));
		GHRepository repository = gitHub.getRepository(repoName);
		GHCommit currentCommit = repository.getCommit(currentCommitId);
		final String parentCommitId = currentCommit.getParents().get(0).getSHA1();
		Set<String> deletedAndRenamedFileParentDirectories = ConcurrentHashMap.newKeySet();
		List<GHCommit.File> commitFiles = currentCommit.getFiles();
		ExecutorService pool = Executors.newFixedThreadPool(commitFiles.size());
		for (GHCommit.File commitFile : commitFiles) {
			String fileName = commitFile.getFileName();
			if (commitFile.getFileName().endsWith(".java")) {
				if (commitFile.getStatus().equals("modified")) {
					Runnable r = () -> {
						try {
							URL currentRawURL = commitFile.getRawUrl();
							InputStream currentRawFileInputStream = currentRawURL.openStream();
							String currentRawFile = IOUtils.toString(currentRawFileInputStream);
							String rawURLInParentCommit = currentRawURL.toString().replace(currentCommitId, parentCommitId);
							InputStream parentRawFileInputStream = new URL(rawURLInParentCommit).openStream();
							String parentRawFile = IOUtils.toString(parentRawFileInputStream);
							filesBefore.put(fileName, parentRawFile);
							filesCurrent.put(fileName, currentRawFile);
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					};
					pool.submit(r);
				}
				else if (commitFile.getStatus().equals("added")) {
					Runnable r = () -> {
						try {
							URL currentRawURL = commitFile.getRawUrl();
							InputStream currentRawFileInputStream = currentRawURL.openStream();
							String currentRawFile = IOUtils.toString(currentRawFileInputStream);
							filesCurrent.put(fileName, currentRawFile);
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					};
					pool.submit(r);
				}
				else if (commitFile.getStatus().equals("removed")) {
					Runnable r = () -> {
						try {
							URL rawURL = commitFile.getRawUrl();
							InputStream rawFileInputStream = rawURL.openStream();
							String rawFile = IOUtils.toString(rawFileInputStream);
							filesBefore.put(fileName, rawFile);
							if(fileName.contains("/")) {
								deletedAndRenamedFileParentDirectories.add(fileName.substring(0, fileName.lastIndexOf("/")));
							}
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					};
					pool.submit(r);
				}
				else if (commitFile.getStatus().equals("renamed")) {
					Runnable r = () -> {
						try {
							String previousFilename = commitFile.getPreviousFilename();
							URL currentRawURL = commitFile.getRawUrl();
							InputStream currentRawFileInputStream = currentRawURL.openStream();
							String currentRawFile = IOUtils.toString(currentRawFileInputStream);
							String rawURLInParentCommit = currentRawURL.toString().replace(currentCommitId, parentCommitId).replace(fileName, previousFilename);
							InputStream parentRawFileInputStream = new URL(rawURLInParentCommit).openStream();
							String parentRawFile = IOUtils.toString(parentRawFileInputStream);
							filesBefore.put(previousFilename, parentRawFile);
							filesCurrent.put(fileName, currentRawFile);
							renamedFilesHint.put(previousFilename, fileName);
							if(previousFilename.contains("/")) {
								deletedAndRenamedFileParentDirectories.add(previousFilename.substring(0, previousFilename.lastIndexOf("/")));
							}
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					};
					pool.submit(r);
				}
			}
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		repositoryDirectories(currentCommit.getTree(), "", repositoryDirectoriesCurrent, deletedAndRenamedFileParentDirectories);
		//allRepositoryDirectories(currentCommit.getTree(), "", repositoryDirectoriesCurrent);
		//GHCommit parentCommit = repository.getCommit(parentCommitId);
		//allRepositoryDirectories(parentCommit.getTree(), "", repositoryDirectoriesBefore);
	}

	private void repositoryDirectories(GHTree tree, String pathFromRoot, Set<String> repositoryDirectories, Set<String> targetPaths) throws IOException {
		for(GHTreeEntry entry : tree.getTree()) {
			String path = null;
			if(pathFromRoot.equals("")) {
				path = entry.getPath();
			}
			else {
				path = pathFromRoot + "/" + entry.getPath();
			}
			if(atLeastOneStartsWith(targetPaths, path)) {
				if(targetPaths.contains(path)) {
					repositoryDirectories.add(path);
				}
				else {
					repositoryDirectories.add(path);
					GHTree asTree = entry.asTree();
					if(asTree != null) {
						repositoryDirectories(asTree, path, repositoryDirectories, targetPaths);
					}
				}
			}
		}
	}

	private boolean atLeastOneStartsWith(Set<String> targetPaths, String path) {
		for(String targetPath : targetPaths) {
			if(path.endsWith("/") && targetPath.startsWith(path)) {
				return true;
			}
			else if(!path.endsWith("/") && targetPath.startsWith(path + "/")) {
				return true;
			}
		}
		return false;
	}
	/*
	private void allRepositoryDirectories(GHTree tree, String pathFromRoot, Set<String> repositoryDirectories) throws IOException {
		for(GHTreeEntry entry : tree.getTree()) {
			String path = null;
			if(pathFromRoot.equals("")) {
				path = entry.getPath();
			}
			else {
				path = pathFromRoot + "/" + entry.getPath();
			}
			GHTree asTree = entry.asTree();
			if(asTree != null) {
				allRepositoryDirectories(asTree, path, repositoryDirectories);
			}
			else if(path.endsWith(".java")) {
				repositoryDirectories.add(path.substring(0, path.lastIndexOf("/")));
			}
		}
	}
	*/

	@Override
	public void detectAtPullRequest(String cloneURL, int pullRequestId, RefactoringHandler handler, int timeout) throws IOException {
		GitHub gitHub = connectToGitHub();
		//https://github.com/ is 19 chars
		String repoName = cloneURL.substring(19, cloneURL.indexOf(".git"));
		GHRepository repository = gitHub.getRepository(repoName);
		GHPullRequest pullRequest = repository.getPullRequest(pullRequestId);
		PagedIterable<GHPullRequestCommitDetail> commits = pullRequest.listCommits();
		for(GHPullRequestCommitDetail commit : commits) {
			detectAtCommit(cloneURL, commit.getSha(), handler, timeout);
		}
	}
}
